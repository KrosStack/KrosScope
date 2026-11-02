#include <jni.h>
#include <stdint.h>

static uint32_t crc32_update(uint32_t crc, uint8_t byte) {
    crc ^= byte;
    for (int bit = 0; bit < 8; bit++) {
        crc = (crc >> 1) ^ (0xEDB88320u & (0u - (crc & 1u)));
    }
    return crc;
}

JNIEXPORT jint JNICALL
Java_sample_tasktide_nativebridge_Checksum_crc32(JNIEnv *env, jobject thiz, jbyteArray data) {
    jsize length = (*env)->GetArrayLength(env, data);
    jbyte *bytes = (*env)->GetByteArrayElements(env, data, NULL);
    uint32_t crc = 0xFFFFFFFFu;
    for (jsize i = 0; i < length; i++) {
        crc = crc32_update(crc, (uint8_t) bytes[i]);
    }
    (*env)->ReleaseByteArrayElements(env, data, bytes, JNI_ABORT);
    return (jint) (crc ^ 0xFFFFFFFFu);
}

JNIEXPORT jstring JNICALL
Java_sample_tasktide_nativebridge_Checksum_nativeVersion(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "crc32-1");
}
