package io.github.krosstack.kscope.service

import io.github.krosstack.kscope.service.analysis.SampleWorkspace
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class WorkspaceStateTest {
    @Test
    fun `the service exposes stamps without opening or writing the index`() {
        SampleWorkspace("mixed").use { workspace ->
            KrosScope.open(workspace.root).use { scope ->
                val before = scope.workspaceStamp()
                assertEquals(before, scope.workspaceStamp())
                assertFalse(scope.indexRequiresTrust())
                workspace.write("Added.kt", "class Added")
                assertNotEquals(before, scope.workspaceStamp())
                assertFalse(Files.exists(workspace.root.resolve(".kscope")))
            }
        }
    }
}
