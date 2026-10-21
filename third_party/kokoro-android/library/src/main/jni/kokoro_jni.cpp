// JNI bridge between the Kotlin API (dev.ffmpegkit.kokoro.KokoroJNI) and espeak-ng.
// The native side does grapheme-to-phoneme only: text → IPA phonemes. Tokenization
// and the Kokoro ONNX inference run in Kotlin (onnxruntime-android). Produces
// libkokoro_jni.so; Kotlin loads espeak-ng then kokoro_jni.
//
// Native entry points (must match KokoroJNI.kt):
//   nativeInitEspeak(dataPath)   -> jint   (sample rate, or -1 on failure)
//   nativeSetVoice(name)         -> jboolean
//   nativePhonemize(text)        -> jstring (IPA phonemes)
//   nativeRelease()              -> void

#include <jni.h>
#include <android/log.h>
#include <string>

#include "speak_lib.h"

#define LOG_TAG "kokoro-jni"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

bool g_ready = false;

std::string jstr(JNIEnv *env, jstring s) {
    if (!s) return {};
    const char *c = env->GetStringUTFChars(s, nullptr);
    std::string out = c ? c : "";
    if (c) env->ReleaseStringUTFChars(s, c);
    return out;
}

} // namespace

extern "C" {

// dataPath: the directory that CONTAINS `espeak-ng-data/` (extracted from assets).
JNIEXPORT jint JNICALL
Java_dev_ffmpegkit_kokoro_KokoroJNI_nativeInitEspeak(JNIEnv *env, jobject, jstring dataPath) {
    // AUDIO_OUTPUT_SYNCHRONOUS: no real audio device; we only phonemize.
    // DONT_EXIT: never let espeak call exit() from inside a library on error.
    const int rate = espeak_Initialize(
            AUDIO_OUTPUT_SYNCHRONOUS, 0, jstr(env, dataPath).c_str(),
            espeakINITIALIZE_DONT_EXIT);
    if (rate <= 0) {
        LOGE("espeak_Initialize failed (data path wrong? need <path>/espeak-ng-data/)");
        return -1;
    }
    g_ready = true;
    return rate;
}

// name: an espeak language/voice, e.g. "en-us", "en-gb", "fr", "es", "ja".
JNIEXPORT jboolean JNICALL
Java_dev_ffmpegkit_kokoro_KokoroJNI_nativeSetVoice(JNIEnv *env, jobject, jstring name) {
    if (!g_ready) return JNI_FALSE;
    return espeak_SetVoiceByName(jstr(env, name).c_str()) == EE_OK ? JNI_TRUE : JNI_FALSE;
}

// Translate UTF-8 text into a continuous IPA phoneme string (phoneme_mode bit 1 = IPA,
// no separator). Loops because espeak returns one clause at a time.
JNIEXPORT jstring JNICALL
Java_dev_ffmpegkit_kokoro_KokoroJNI_nativePhonemize(JNIEnv *env, jobject, jstring text) {
    if (!g_ready) return env->NewStringUTF("");
    const std::string in = jstr(env, text);
    const void *ptr = in.c_str();
    std::string out;
    while (ptr != nullptr) {
        const char *ph = espeak_TextToPhonemes(&ptr, espeakCHARS_UTF8, /*IPA*/ 0x02);
        if (ph) out += ph;
    }
    return env->NewStringUTF(out.c_str());
}

JNIEXPORT void JNICALL
Java_dev_ffmpegkit_kokoro_KokoroJNI_nativeRelease(JNIEnv *, jobject) {
    if (g_ready) { espeak_Terminate(); g_ready = false; }
}

} // extern "C"
