package io.github.krosstack.kscope.cli.serve.token

import java.io.IOException
import java.nio.file.FileStore
import java.nio.file.Files
import java.nio.file.attribute.FileAttributeView
import java.nio.file.attribute.FileStoreAttributeView
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OwnerOnlyFileTest {
    private val directory = Files.createTempDirectory("kscope-owner-only")

    @AfterTest
    fun cleanUp() {
        directory.toFile().deleteRecursively()
    }

    private fun storeWithoutAnyRestriction(): FileStore = object : FileStore() {
        override fun name() = "plain"

        override fun type() = "plain"

        override fun isReadOnly() = false

        override fun getTotalSpace() = 0L

        override fun getUsableSpace() = 0L

        override fun getUnallocatedSpace() = 0L

        override fun supportsFileAttributeView(type: Class<out FileAttributeView>) = false

        override fun supportsFileAttributeView(name: String) = false

        override fun <V : FileStoreAttributeView> getFileStoreAttributeView(type: Class<V>): V? = null

        override fun getAttribute(attribute: String): Any = throw UnsupportedOperationException(attribute)
    }

    @Test
    fun `a temporary file can be read by its owner only`() {
        val file = OwnerOnlyFile.createTemporary(directory, "serve-", ".tmp")

        assertReadableByOwnerOnly(file)
    }

    @Test
    fun `no file is created where neither permissions nor a list can restrict it`() {
        val failure = assertFailsWith<IOException> {
            OwnerOnlyFile.createTemporary(directory, "serve-", ".tmp", storeWithoutAnyRestriction())
        }

        assertTrue(failure.message.orEmpty().contains("neither POSIX permissions nor access control lists"))
        assertEquals(0L, Files.list(directory).use { it.count() })
    }
}
