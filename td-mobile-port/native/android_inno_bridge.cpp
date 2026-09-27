#include <jni.h>
#include <dlfcn.h>

#include <mutex>
#include <string>

namespace {
std::mutex g_inno_mutex;
void* g_inno_library = nullptr;

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

    // innoextract-android v3.2.0 exposes the same JNI entry points used by
    // its original ExtractService. Unlike v4, this release accepts normal
    // filesystem paths, which is exactly what we have in app-private storage.
    using NativeInit = void (*)(JNIEnv*, jobject);
    using NativeDoExtract = int (*)(JNIEnv*, jobject, jstring, jstring);

    dlerror();
    auto native_init = reinterpret_cast<NativeInit>(dlsym(
        g_inno_library,
        "Java_uk_co_armedpineapple_innoextract_service_ExtractService_nativeInit"));
    const char* init_error = dlerror();
    if (!native_init || init_error) {
        return Error(env, std::string("German extractor init entry point unavailable: ")
            + (init_error ? init_error : "unknown symbol error"));
    }

    dlerror();
    auto native_extract = reinterpret_cast<NativeDoExtract>(dlsym(
        g_inno_library,
        "Java_uk_co_armedpineapple_innoextract_service_ExtractService_nativeDoExtract"));
    const char* extract_error = dlerror();
    if (!native_extract || extract_error) {
        return Error(env, std::string("German extractor entry point unavailable: ")
            + (extract_error ? extract_error : "unknown symbol error"));
    }

    // Match the v3.2 ExtractService lifecycle: initialise immediately before
    // each extraction, then hand the private app paths to nativeDoExtract.
    native_init(env, compat_service);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return Error(env, "German extractor could not initialise its callbacks");
    }

    int result = -1;
    try {
        result = native_extract(
            env, compat_service, installer_path, output_directory);
    } catch (...) {
        return Error(env, "German package extractor terminated unexpectedly");
    }

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
