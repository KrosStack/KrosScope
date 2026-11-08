package io.github.krosstack.kscope.semantic.worker

import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DistributionTest {
    @Test
    fun `archive has component directories and licence files at its root`() {
        ZipFile(System.getProperty("kscope.semantic.archive")).use { zip ->
            val roots = zip.entries().asSequence().map { it.name.substringBefore('/') }.toSet()
            assertEquals(setOf("lib", "native", "licenses", "LICENSE", "NOTICE", "THIRD-PARTY-LICENSES.md"), roots)
        }
    }

    @Test
    fun `archive contains isolated runtime and four native platforms without weights`() {
        ZipFile(System.getProperty("kscope.semantic.archive")).use { zip ->
            val paths = zip.entries().asSequence().filterNot { it.isDirectory }.map {
                it.name
            }.toList()
            val native = paths.filter { it.startsWith("native/") }
            assertEquals(
                setOf("windows-x64", "linux-x64", "linux-aarch64", "macos-arm64"),
                native.map { it.split('/')[1] }.toSet(),
            )
            assertEquals(9, native.size)
            assertTrue("native/windows-x64/onnxruntime_providers_shared.dll" in native)
            listOf(
                "semantic-worker-",
                "spi-",
                "model-",
                "kotlinx-serialization-json-jvm-",
                "onnxruntime-",
            ).forEach { jar ->
                assertTrue(paths.any { it.startsWith("lib/$jar") && it.endsWith(".jar") }, jar)
            }
            assertTrue("licenses/onnxruntime-MIT.txt" in paths)
            assertFalse(paths.any { it.startsWith("model/") || it.endsWith(".onnx") })
        }
    }
}
