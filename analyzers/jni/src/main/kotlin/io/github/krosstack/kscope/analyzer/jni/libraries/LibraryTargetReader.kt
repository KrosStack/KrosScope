package io.github.krosstack.kscope.analyzer.jni.libraries

/** Reads the shared libraries that the text of one kind of native build file declares. */
internal interface LibraryTargetReader {
    fun read(text: String): List<LibraryTarget>
}
