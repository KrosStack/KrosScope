package io.github.krosstack.kscope.engine

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.AclEntryPermission
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.FileTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class WorkspaceStampPermissionsTest {
    @Test
    fun `denying content access through an ACL still permits stamping file metadata`() {
        TestWorkspace().use { workspace ->
            workspace.write("Locked.kt", "class Locked")
            val file = workspace.root.resolve("Locked.kt")
            val available = Files.getFileAttributeView(file, AclFileAttributeView::class.java)
            assumeTrue(available != null)
            val view = checkNotNull(available)
            val original = view.acl
            val denied = AclEntry.newBuilder().setType(AclEntryType.DENY).setPrincipal(Files.getOwner(file))
                .setPermissions(AclEntryPermission.READ_DATA).build()
            workspace.engine().use { engine ->
                val before = engine.workspaceStamp()
                try {
                    view.acl = listOf(denied) + original
                    assertFailsWith<AccessDeniedException> { Files.newInputStream(file).use { it.read() } }
                    assertEquals(before, engine.workspaceStamp())
                    Files.setLastModifiedTime(file, FileTime.fromMillis(0))
                    assertNotEquals(before, engine.workspaceStamp())
                } finally {
                    view.acl = original
                }
            }
        }
    }
}
