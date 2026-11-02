#include <jni.h>

extern "C" JNIEXPORT jint JNICALL Java_sample_jni_bridge_JavaNative_add(JNIEnv *, jobject, jint left, jint right) {
    return left + right;
}
