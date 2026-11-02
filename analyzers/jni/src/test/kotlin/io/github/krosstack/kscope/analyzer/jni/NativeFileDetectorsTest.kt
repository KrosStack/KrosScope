package io.github.krosstack.kscope.analyzer.jni

import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.spi.ArtifactCandidate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NativeFileDetectorsTest {
    private fun detect(path: String): String? {
        val candidate = ArtifactCandidate(RepoPath(path), 0) { ByteArray(0) }
        val types = JniCapabilities.detectors().mapNotNull { it.detect(candidate) }
        return types.singleOrNull()?.id
    }

    @Test
    fun `rust sources are native rust source`() {
        listOf("lib.rs", "src/lib.rs", "src/jni/Mod.RS").forEach { assertEquals("native.rust-source", detect(it), it) }
    }

    @Test
    fun `the build files of native code have a type each, wherever they lie`() {
        assertEquals("native.cmake-lists", detect("CMakeLists.txt"))
        assertEquals("native.cmake-lists", detect("native/app/CMakeLists.txt"))
        assertEquals("native.cargo-manifest", detect("Cargo.toml"))
        assertEquals("native.cargo-manifest", detect("crates/hash/Cargo.toml"))
        assertEquals("native.ndk-makefile", detect("jni/Android.mk"))
        assertEquals("native.cmake-lists", detect("cmakelists.TXT"))
    }

    @Test
    fun `files that only look like native build files are not claimed`() {
        listOf("CMakeLists.txt.orig", "toolchain.cmake", "Cargo.lock", "Cargo.toml.bak", "Application.mk", "lib.rs.bk")
            .forEach { assertNull(detect(it), it) }
    }

    @Test
    fun `c and c plus plus sources keep their type`() {
        assertEquals("native.source", detect("src/checksum.cpp"))
    }

    @Test
    fun `the detectors have distinct ids`() {
        val ids = JniCapabilities.detectors().map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }
}
