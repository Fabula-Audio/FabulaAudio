#include "tempo.h"
#include <jni.h>
#include <exception>
#include <stdexcept>

extern "C" JNIEXPORT jlong JNICALL
Java_com_fabula_audio_SbsmsTempo_process(JNIEnv *env, jobject, jstring input, jstring output, jint tempo, jobject listener) {
    const char *in = env->GetStringUTFChars(input, nullptr);
    if (!in) return 0;
    const char *out = env->GetStringUTFChars(output, nullptr);
    if (!out) { env->ReleaseStringUTFChars(input, in); return 0; }
    jlong frames = 0;
    try {
        TempoProgress progress;
        if (listener) {
            jclass type = env->GetObjectClass(listener);
            jmethodID method = env->GetMethodID(type, "onProgress", "(JJ)V");
            env->DeleteLocalRef(type);
            if (!method) throw std::runtime_error("Missing tempo progress callback");
            progress = [env, listener, method](int64_t completed, int64_t total) {
                env->CallVoidMethod(listener, method, static_cast<jlong>(completed), static_cast<jlong>(total));
                if (env->ExceptionCheck()) throw std::runtime_error("Tempo progress callback failed");
            };
        }
        frames = changeTempo(in, out, tempo, progress);
    }
    catch (const std::exception &e) {
        if (!env->ExceptionCheck()) env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), e.what());
    }
    catch (...) { env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "SBSMS processing failed"); }
    env->ReleaseStringUTFChars(output, out);
    env->ReleaseStringUTFChars(input, in);
    return frames;
}
