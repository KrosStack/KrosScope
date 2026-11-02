package io.github.krosstack.kscope.analyzer.jni.loads

import kotlin.test.Test
import kotlin.test.assertEquals

class LibraryFileNameTest {
    @Test
    fun `the prefix lib and the extensions of shared libraries are taken off`() {
        mapOf(
            "libfoo.so" to "foo",
            "/usr/lib/libfoo.so.1.2" to "foo",
            "libfoo.dylib" to "foo",
            "libfoo.jnilib" to "foo",
            "foo.dll" to "foo",
            "C:\\libs\\foo.dll" to "foo",
        ).forEach { (path, library) -> assertEquals(library, libraryOfFile(path), path) }
    }

    @Test
    fun `a name without prefix or extension stays and lib alone is a name`() {
        assertEquals("plain", libraryOfFile("plain"))
        assertEquals("lib", libraryOfFile("lib"))
        assertEquals("foo.bar", libraryOfFile("libfoo.bar.so"))
    }
}
