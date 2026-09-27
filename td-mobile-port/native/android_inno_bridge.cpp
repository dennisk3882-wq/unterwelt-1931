#include <jni.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <unistd.h>

#include <mutex>
#include <string>

namespace {
std::mutex g_inno_mutex;
jobject g_service = nullptr;
bool g_prepared = false;
void* g_library = nullptr;

using PrepareFn = void (*)(JNIEnv*, jobject);
using ExtractFn = int (*)(JNIEnv*, jobject, jint, jstring);
PrepareFn g_prepare = nullptr;
ExtractFn g_extract = nullptr;

std::string FromJava(JNIEnv* env, jstring value)
{
    if (!value) return std::string();
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (!chars) return std::string();
    std::string out(chars);
    env->ReleaseStringUTFChars(value, chars);
    return out;
}

jstring Error(JNIEnv* env, const std::string& message)
{
    return env->NewStringUTF(message.c_str());
}

bool CheckAndClearJavaException(JNIEnv* env, std::string& message)
{
    if (!env->ExceptionCheck()) return false;
    env->ExceptionDescribe();
    env->ExceptionClear();
    message = "German package extractor Java compatibility layer failed";
    return true;
}

bool EnsureNativeLibrary(JNIEnv* env, std::string& error)
{
    if (g_library && g_prepare && g_extract) return true;

    g_library = dlopen("libinnoextract.so", RTLD_NOW | RTLD_LOCAL);
    if (!g_library) {
        const char* detail = dlerror();
        error = std::string("Could not load German package extractor: ")
            + (detail ? detail : "unknown dlopen error");
        return false;
    }

    dlerror();
    g_prepare = reinterpret_cast<PrepareFn>(dlsym(
        g_library,
        "Java_uk_co_armedpineapple_innoextract_service_ExtractService_nativePrepare"));
    const char* prepare_error = dlerror();
    if (!g_prepare || prepare_error) {
        error = "German package extractor prepare entry point is unavailable";
        if (prepare_error) error += std::string(": ") + prepare_error;
        return false;
    }

    dlerror();
    g_extract = reinterpret_cast<ExtractFn>(dlsym(
        g_library,
        "Java_uk_co_armedpineapple_innoextract_service_ExtractService_nativeExtract"));
    const char* extract_error = dlerror();
    if (!g_extract || extract_error) {
        error = "German package extractor entry point is unavailable";
        if (extract_error) error += std::string(": ") + extract_error;
        return false;
    }
    return true;
}

jobject EnsureService(JNIEnv* env)
{
    if (g_service) return g_service;

    jclass cls = env->FindClass("org/tiberiandawn/android/InnoExtractCompatService");
    if (!cls) return nullptr;
    jmethodID ctor = env->GetMethodID(cls, "<init>", "()V");
    if (!ctor) {
        env->DeleteLocalRef(cls);
        return nullptr;
    }

    jobject local = env->NewObject(cls, ctor);
    env->DeleteLocalRef(cls);
    if (!local) return nullptr;

    g_service = env->NewGlobalRef(local);
    env->DeleteLocalRef(local);
    return g_service;
}

bool ConfigureOutputRoot(JNIEnv* env, jobject service, jstring output_directory)
{
    jclass cls = env->GetObjectClass(service);
    if (!cls) return false;
    jmethodID method = env->GetMethodID(cls, "setOutputRoot", "(Ljava/lang/String;)V");
    env->DeleteLocalRef(cls);
    if (!method) return false;

    env->CallVoidMethod(service, method, output_directory);
    return !env->ExceptionCheck();
}

bool PrepareOnce(JNIEnv* env, jobject service, std::string& error)
{
    if (g_prepared) return true;

    // nativePrepare stores the Java callback object and installs its stdout/
    // stderr capture. Preserve the game's descriptors around that setup.
    const int saved_stdout = dup(STDOUT_FILENO);
    const int saved_stderr = dup(STDERR_FILENO);

    g_prepare(env, service);

    if (saved_stdout >= 0) {
        dup2(saved_stdout, STDOUT_FILENO);
        close(saved_stdout);
    }
    if (saved_stderr >= 0) {
        dup2(saved_stderr, STDERR_FILENO);
        close(saved_stderr);
    }

    if (CheckAndClearJavaException(env, error)) return false;
    g_prepared = true;
    return true;
}
} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_org_tiberiandawn_android_GermanPackageInstaller_nativeExtractInno(
    JNIEnv* env, jclass, jstring installer_path, jstring output_directory)
{
    std::lock_guard<std::mutex> guard(g_inno_mutex);

    const std::string installer = FromJava(env, installer_path);
    const std::string output = FromJava(env, output_directory);
    if (installer.empty() || output.empty()) {
        return Error(env, "German extractor received an empty path");
    }

    std::string error;
    if (!EnsureNativeLibrary(env, error)) return Error(env, error);

    jobject service = EnsureService(env);
    if (!service) {
        CheckAndClearJavaException(env, error);
        return Error(env, error.empty()
            ? "German package extractor compatibility service is unavailable"
            : error);
    }

    if (!ConfigureOutputRoot(env, service, output_directory)) {
        CheckAndClearJavaException(env, error);
        return Error(env, error.empty()
            ? "German package extractor output directory could not be configured"
            : error);
    }

    if (!PrepareOnce(env, service, error)) return Error(env, error);

    const int fd = open(installer.c_str(), O_RDONLY | O_CLOEXEC);
    if (fd < 0) {
        return Error(env, "German package installer could not be opened");
    }

    // The Android innoextract fork interprets the positional setup argument as
    // an already-open Linux file descriptor. Its JNI wrapper supplies exactly
    // that descriptor to its internal main().
    const int result = g_extract(
        env, service, static_cast<jint>(fd), output_directory);
    close(fd);

    if (CheckAndClearJavaException(env, error)) return Error(env, error);

    if (result != 0) {
        return Error(env, "German package extractor failed with code "
            + std::to_string(result));
    }
    return nullptr;
}
