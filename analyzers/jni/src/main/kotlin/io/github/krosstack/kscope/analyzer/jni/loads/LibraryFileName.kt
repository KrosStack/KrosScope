package io.github.krosstack.kscope.analyzer.jni.loads

private val libraryFile = Regex("^(?:lib)?(.+?)(?:\\.so(?:\\.[0-9]+)*|\\.dylib|\\.dll|\\.jnilib)?$")

/** The name of the library that a file holds, as `System.loadLibrary` would be given it: `libfoo.so` is `foo`. */
internal fun libraryOfFile(path: String): String {
    val fileName = path.substringAfterLast('/').substringAfterLast('\\')
    return libraryFile.matchEntire(fileName)?.groupValues?.get(1) ?: fileName
}
