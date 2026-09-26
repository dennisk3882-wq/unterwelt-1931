#include <jni.h>
#include <dlfcn.h>

#include <mutex>
#include <string>
#include <vector>

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
    using InnoMain = int (*)(int, char**);
    InnoMain inno_main = reinterpret_cast<InnoMain>(dlsym(library, "main"));
    const char* symbol_error = dlerror();
    if (!inno_main || symbol_error) {
        std::string message = "German package extractor entry point is unavailable";
        if (symbol_error) message += std::string(": ") + symbol_error;
        dlclose(library);
        return Error(env, message);
    }

    std::vector<std::string> args;
    args.emplace_back("innoextract");
    args.emplace_back("--extract");
    args.emplace_back("--silent");
    args.emplace_back("--output-dir");
    args.emplace_back(output);
    args.emplace_back(installer);

    std::vector<char*> argv;
    argv.reserve(args.size() + 1);
    for (std::string& arg : args) argv.push_back(&arg[0]);
    argv.push_back(nullptr);

    int result = -1;
    try {
        result = inno_main(static_cast<int>(args.size()), argv.data());
    } catch (...) {
        dlclose(library);
        return Error(env, "German package extractor terminated unexpectedly");
    }

    dlclose(library);
    if (result != 0) {
        return Error(env, "German package extractor failed with code "
            + std::to_string(result));
    }
    return nullptr;
}
