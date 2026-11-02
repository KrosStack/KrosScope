#include <jni.h>

#include <cstddef>
#include <cstdint>

namespace {

std::uint32_t crc32(const std::uint8_t* data, std::size_t size) {
    std::uint32_t crc = 0xFFFFFFFFu;
    for (std::size_t i = 0; i < size; ++i) {
        crc ^= data[i];
        for (int bit = 0; bit < 8; ++bit) {
            crc = (crc >> 1) ^ (0xEDB88320u & (0u - (crc & 1u)));
        }
    }
    return ~crc;
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_sample_tasktide_client_NativeChecksum_crc32(JNIEnv* env, jobject, jbyteArray bytes) {
    const jsize size = env->GetArrayLength(bytes);
    jbyte* data = env->GetByteArrayElements(bytes, nullptr);
    const std::uint32_t result = crc32(reinterpret_cast<const std::uint8_t*>(data), static_cast<std::size_t>(size));
    env->ReleaseByteArrayElements(bytes, data, JNI_ABORT);
    return static_cast<jlong>(result);
}
