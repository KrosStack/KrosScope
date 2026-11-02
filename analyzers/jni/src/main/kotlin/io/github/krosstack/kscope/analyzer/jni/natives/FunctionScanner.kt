package io.github.krosstack.kscope.analyzer.jni.natives

/** Finds the JNI functions that one native source file defines or declares. */
internal interface FunctionScanner {
    fun scan(): List<NativeFunction>
}
