package io.github.krosstack.kscope.engine

import io.github.krosstack.kscope.engine.structure.TestGit
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import kotlin.test.Test
import kotlin.test.assertFalse

class WorkspaceStampSafetyTest {
    @Test
    fun `stamping and scanning do not execute a configured git fsmonitor hook`() {
        assumeTrue(TestGit.available())
        TestWorkspace("plain").use { workspace ->
            TestGit.output(workspace.root, "init", "-q")
            TestGit.output(workspace.root, "add", ".")
            TestGit.output(workspace.root, "commit", "-qm", "sample")
            val hook = workspace.root.resolve(".git/watch-fsmonitor")
            Files.writeString(hook, "#!/bin/sh\necho executed > watch-executed\n")
            if (Files.getFileStore(hook).supportsFileAttributeView("posix")) {
                Files.setPosixFilePermissions(
                    hook,
                    setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE),
                )
            }
            TestGit.output(workspace.root, "config", "core.fsmonitor", ".git/watch-fsmonitor")
            workspace.engine().use { engine ->
                engine.workspaceStamp()
                assertFalse(Files.exists(workspace.root.resolve("watch-executed")))
                engine.update()
                assertFalse(Files.exists(workspace.root.resolve("watch-executed")))
            }
        }
    }
}
