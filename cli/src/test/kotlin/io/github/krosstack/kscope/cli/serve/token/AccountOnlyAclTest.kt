package io.github.krosstack.kscope.cli.serve.token

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountOnlyAclTest {
    private val directory = Files.createTempDirectory("kscope-account-only")

    @AfterTest
    fun cleanUp() {
        directory.toFile().deleteRecursively()
    }

    private fun newFile(): Path = Files.createTempFile(directory, "acl-", ".tmp")

    private fun viewOf(file: Path): AclFileAttributeView {
        val view = Files.getFileAttributeView(file, AclFileAttributeView::class.java)
        assumeTrue(view != null, "$file has no access control list")
        return checkNotNull(view)
    }

    @Test
    fun `a list that names other accounts is not account only`() {
        val view = viewOf(newFile())
        assumeTrue(view.acl.size > 1, "the temporary directory grants access to one account only")

        assertFalse(AccountOnlyAcl.grantsOnlyTo(view.acl, view.owner))
    }

    @Test
    fun `an empty list and a list that denies are not account only`() {
        val view = viewOf(newFile())
        val denying = view.acl.map { AclEntry.newBuilder(it).setType(AclEntryType.DENY).build() }

        assertFalse(AccountOnlyAcl.grantsOnlyTo(emptyList(), view.owner))
        assertFalse(AccountOnlyAcl.grantsOnlyTo(denying, view.owner))
    }

    @Test
    fun `restricting the file leaves one entry that allows the account of the process`() {
        val file = newFile()
        val view = viewOf(file)

        AccountOnlyAcl.restrict(file)

        val entry = view.acl.single()
        assertEquals(AclEntryType.ALLOW, entry.type())
        assertTrue(AccountOnlyAcl.grantsOnlyTo(view.acl, entry.principal()))
        assertTrue(entry.principal().name.endsWith(System.getProperty("user.name"), ignoreCase = true))
    }
}
