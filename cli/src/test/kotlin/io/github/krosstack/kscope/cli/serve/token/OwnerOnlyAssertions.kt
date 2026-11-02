package io.github.krosstack.kscope.cli.serve.token

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal fun assertReadableByOwnerOnly(file: Path) {
    val store = Files.getFileStore(file)
    val posix = store.supportsFileAttributeView("posix")
    val acl = store.supportsFileAttributeView(AclFileAttributeView::class.java)
    assumeTrue(posix || acl, "$file is on a file system with neither POSIX permissions nor an ACL")
    if (posix) {
        assertEquals(setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), permissionsOf(file))
    } else {
        assertOnlyTheCurrentAccountIsAllowed(file)
    }
}

private fun permissionsOf(file: Path) = Files.getPosixFilePermissions(file)

private fun assertOnlyTheCurrentAccountIsAllowed(file: Path) {
    val account = System.getProperty("user.name")
    val entries = checkNotNull(Files.getFileAttributeView(file, AclFileAttributeView::class.java)).acl
    assertTrue(entries.isNotEmpty(), "$file has an empty ACL, which grants nobody anything")
    for (entry in entries) {
        assertEquals(AclEntryType.ALLOW, entry.type(), "$file: $entry")
        val name = entry.principal().name.substringAfterLast(BACKSLASH)
        assertTrue(name.equals(account, ignoreCase = true), "$file grants access to ${entry.principal().name}")
    }
}

private const val BACKSLASH = '\\'
