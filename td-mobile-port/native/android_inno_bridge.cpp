#include <jni.h>
#include <dlfcn.h>

#include <mutex>
#include <string>

namespace {
std::mutex g_inno_mutex;

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
}

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

    dlerror();
    using NativeDoExtract = jint (*)(JNIEnv*, jobject, jstring, jstring);
    NativeDoExtract native_extract = reinterpret_cast<NativeDoExtract>(
        dlsym(library,
            "Java_uk_co_armedpineapple_innoextract_service_ExtractService_nativeDoExtract"));
    const char* symbol_error = dlerror();
    if (!native_extract || symbol_error) {
        std::string message =
            "Compatible German package extractor entry point is unavailable";
        if (symbol_error) message += std::string(": ") + symbol_error;
        dlclose(library);
        return Error(env, message);
    }

    // innoextract-android v3.2 exposes a path-based JNI extraction function.
    // Call that directly instead of its CLI main().  v4 switched to scoped-
    // storage file descriptors and returns code 2 when used with our private
    // app paths, which is the failure seen on real Android devices.
    const jint result = native_extract(
        env, nullptr, installer_path, output_directory);

    dlclose(library);
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
