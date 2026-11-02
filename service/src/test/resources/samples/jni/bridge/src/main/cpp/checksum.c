#include <jni.h>
#include <stdint.h>

static uint32_t crc32_update(uint32_t crc, uint8_t byte) {
    crc ^= byte;
    return crc;
}

JNIEXPORT jint JNICALL
Java_sample_jni_bridge_Checksum_crc32(JNIEnv *env, jobject thiz, jbyteArray data) {
    jsize length = (*env)->GetArrayLength(env, data);
    return (jint) crc32_update(0, 0) + length;
}

JNIEXPORT jstring JNICALL
Java_sample_jni_bridge_Checksum_nativeVersion(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "crc32-1");
}
