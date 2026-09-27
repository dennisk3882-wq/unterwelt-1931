#include <jni.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <unistd.h>

#include <mutex>
#include <string>

namespace {
std::mutex g_inno_mutex;
void* g_inno_library = nullptr;
bool g_inno_prepared = false;

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

bool EnsureLibrary(std::string& error)
{
    if (g_inno_library) return true;

    g_inno_library = dlopen("libinnoextract.so", RTLD_NOW | RTLD_LOCAL);
    if (!g_inno_library) {
        const char* detail = dlerror();
        error = std::string("Could not load German package extractor: ")
            + (detail ? detail : "unknown dlopen error");
        return false;
    }
    return true;
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_tiberiandawn_android_GermanPackageInstaller_nativeExtractInno(
    JNIEnv* env, jclass, jstring installer_path, jstring output_directory,
    jobject compat_service)
{
    std::lock_guard<std::mutex> guard(g_inno_mutex);

    const std::string installer = FromJava(env, installer_path);
    const std::string output = FromJava(env, output_directory);
    if (installer.empty() || output.empty() || !compat_service) {
        return Error(env, "German extractor received invalid parameters");
    }

    std::string load_error;
    if (!EnsureLibrary(load_error)) {
        return Error(env, load_error);
    }

    // alanwoolley/innoextract-android expects the input installer as an
    // already-open Linux file descriptor. The numeric fd is passed into
    // innoextract's Android stream adapter by its JNI nativeExtract method.
    using NativePrepare = void (*)(JNIEnv*, jobject);
    using NativeExtract = int (*)(JNIEnv*, jobject, jint, jstring);

    dlerror();
    auto native_prepare = reinterpret_cast<NativePrepare>(dlsym(
        g_inno_library,
        "Java_uk_co_armedpineapple_innoextract_service_ExtractService_nativePrepare"));
    const char* prepare_error = dlerror();
    if (!native_prepare || prepare_error) {
        return Error(env, std::string("German extractor prepare entry point unavailable: ")
            + (prepare_error ? prepare_error : "unknown symbol error"));
    }

    dlerror();
    auto native_extract = reinterpret_cast<NativeExtract>(dlsym(
        g_inno_library,
        "Java_uk_co_armedpineapple_innoextract_service_ExtractService_nativeExtract"));
    const char* extract_error = dlerror();
    if (!native_extract || extract_error) {
        return Error(env, std::string("German extractor entry point unavailable: ")
            + (extract_error ? extract_error : "unknown symbol error"));
    }

    if (!g_inno_prepared) {
        native_prepare(env, compat_service);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return Error(env, "German extractor could not initialise its callbacks");
        }
        g_inno_prepared = true;
    }

    int fd = open(installer.c_str(), O_RDONLY | O_CLOEXEC);
    if (fd < 0) {
        return Error(env, "German installer could not be opened");
    }

    int result = -1;
    try {
        result = native_extract(env, compat_service, static_cast<jint>(fd),
                                output_directory);
    } catch (...) {
        close(fd);
        return Error(env, "German package extractor terminated unexpectedly");
    }

    close(fd);

    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return Error(env, "German package extractor raised an Android exception");
    }
    if (result != 0) {
        return Error(env, "German package extractor failed with code "
            + std::to_string(result));
    }

    return nullptr;
}
