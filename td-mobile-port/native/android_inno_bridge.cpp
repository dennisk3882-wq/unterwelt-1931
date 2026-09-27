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

jobject EnsureService(JNIEnv* env)
{
    if (g_service) return g_service;

    jclass cls = env->FindClass("org/tiberiandawn/android/InnoExtractCompatService");
    if (!cls) return nullptr;
    jmethodID ctor = env->GetMethodID(cls, "<init>", "()V");
    if (!ctor) return nullptr;

    jobject local = env->NewObject(cls, ctor);
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

    void* library = dlopen("libinnoextract.so", RTLD_NOW | RTLD_LOCAL);
    if (!library) {
        const char* detail = dlerror();
        return Error(env, std::string("Could not load German package extractor: ")
            + (detail ? detail : "unknown dlopen error"));
    }

    using PrepareFn = void (*)(JNIEnv*, jobject);
    using ExtractFn = int (*)(JNIEnv*, jobject, jint, jstring);

    dlerror();
    PrepareFn prepare = reinterpret_cast<PrepareFn>(dlsym(
        library,
        "Java_uk_co_armedpineapple_innoextract_service_ExtractService_nativePrepare"));
    const char* prepare_error = dlerror();
    if (!prepare || prepare_error) {
        std::string message = "German package extractor prepare entry point is unavailable";
        if (prepare_error) message += std::string(": ") + prepare_error;
        dlclose(library);
        return Error(env, message);
    }

    dlerror();
    ExtractFn extract = reinterpret_cast<ExtractFn>(dlsym(
        library,
        "Java_uk_co_armedpineapple_innoextract_service_ExtractService_nativeExtract"));
    const char* extract_error = dlerror();
    if (!extract || extract_error) {
        std::string message = "German package extractor entry point is unavailable";
        if (extract_error) message += std::string(": ") + extract_error;
        dlclose(library);
        return Error(env, message);
    }

    jobject service = EnsureService(env);
    if (!service) {
        std::string message;
        CheckAndClearJavaException(env, message);
        dlclose(library);
        return Error(env, message.empty()
            ? "German package extractor compatibility service is unavailable"
            : message);
    }

    if (!ConfigureOutputRoot(env, service, output_directory)) {
        std::string message;
        CheckAndClearJavaException(env, message);
        dlclose(library);
        return Error(env, message.empty()
            ? "German package extractor output directory could not be configured"
            : message);
    }

    if (!g_prepared) {
        // The Android innoextract library prepares Java callback state here.
        // It also redirects stdout/stderr. Preserve and restore our process
        // descriptors immediately afterwards so the game keeps its own logs.
        const int saved_stdout = dup(STDOUT_FILENO);
        const int saved_stderr = dup(STDERR_FILENO);

        prepare(env, service);
        std::string message;
        if (CheckAndClearJavaException(env, message)) {
            if (saved_stdout >= 0) {
                dup2(saved_stdout, STDOUT_FILENO);
                close(saved_stdout);
            }
            if (saved_stderr >= 0) {
                dup2(saved_stderr, STDERR_FILENO);
                close(saved_stderr);
            }
            dlclose(library);
            return Error(env, message);
        }

        if (saved_stdout >= 0) {
            dup2(saved_stdout, STDOUT_FILENO);
            close(saved_stdout);
        }
        if (saved_stderr >= 0) {
            dup2(saved_stderr, STDERR_FILENO);
            close(saved_stderr);
        }
        g_prepared = true;
    }

    const int fd = open(installer.c_str(), O_RDONLY | O_CLOEXEC);
    if (fd < 0) {
        dlclose(library);
        return Error(env, "German package installer could not be opened");
    }

    // Important: the Android fork expects a Linux file descriptor here,
    // not a filesystem path. Passing the path was the cause of error code 2.
    const int result = extract(env, service, static_cast<jint>(fd), output_directory);
    close(fd);

    std::string java_error;
    if (CheckAndClearJavaException(env, java_error)) {
        dlclose(library);
        return Error(env, java_error);
    }

    dlclose(library);
    if (result != 0) {
        return Error(env, "German package extractor failed with code "
            + std::to_string(result));
    }
    return nullptr;
}
