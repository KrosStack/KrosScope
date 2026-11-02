package io.github.krosstack.kscope.engine

import io.github.krosstack.kscope.engine.structure.TestGit
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFilePermission
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class WorkspaceStampTest {
    @Test
    fun `stamping notices metadata additions deletions and renames without creating an index`() {
        TestWorkspace("plain").use { workspace ->
            workspace.engine().use { engine ->
                val initial = engine.workspaceStamp()
                assertEquals(initial, engine.workspaceStamp())
                workspace.write("src/New.kt", "class New")
                val added = engine.workspaceStamp()
                assertNotEquals(initial, added)
                workspace.move("src/New.kt", "src/Renamed.kt")
                val renamed = engine.workspaceStamp()
                assertNotEquals(added, renamed)
                workspace.write("src/Renamed.kt", "class LongerName")
                val edited = engine.workspaceStamp()
                assertNotEquals(renamed, edited)
                val file = workspace.root.resolve("src/Renamed.kt")
                Files.setLastModifiedTime(file, FileTime.fromMillis(Files.getLastModifiedTime(file).toMillis() + 5_000))
                assertNotEquals(edited, engine.workspaceStamp())
                workspace.delete("src/Renamed.kt")
                assertEquals(initial, engine.workspaceStamp())
                assertFalse(Files.exists(workspace.indexDirectory))
            }
        }
    }

    @Test
    fun `the walker and git ignore rules and the custom index are reused`() {
        TestWorkspace().use { workspace ->
            val index = workspace.root.resolve("custom-index")
            val config = WorkspaceConfig(workspace.root, index)
            ScanEngine(coreRegistry(), config).use { engine ->
                val initial = engine.workspaceStamp()
                workspace.write("build/Ignore.kt", "class Ignore")
                workspace.write(".kscope/ignored", "ignored")
                workspace.write("custom-index/ignored", "ignored")
                assertEquals(initial, engine.workspaceStamp())
                assumeTrue(TestGit.available())
                TestGit.output(workspace.root, "init", "-q")
                workspace.write(".gitignore", "build/\n.kscope/\nignored/\n")
                val gitStamp = engine.workspaceStamp()
                workspace.write("ignored/Hidden.kt", "class Hidden")
                workspace.write("custom-index/another", "ignored")
                assertEquals(gitStamp, engine.workspaceStamp())
            }
        }
    }

    @Test
    fun `a file without read permission is stamped where permission enforcement is available`() {
        TestWorkspace().use { workspace ->
            assumeTrue(Files.getFileStore(workspace.root).supportsFileAttributeView("posix"))
            workspace.write("Locked.kt", "class Locked")
            val file = workspace.root.resolve("Locked.kt")
            val permissions = Files.getPosixFilePermissions(file)
            try {
                Files.setPosixFilePermissions(file, setOf(PosixFilePermission.OWNER_WRITE))
                assumeTrue(!Files.isReadable(file))
                workspace.engine().use { engine ->
                    val before = engine.workspaceStamp()
                    Files.writeString(file, "class Locked { val changed = true }")
                    assertNotEquals(before, engine.workspaceStamp())
                }
            } finally {
                Files.setPosixFilePermissions(file, permissions)
            }
        }
    }
}
