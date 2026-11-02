package io.github.krosstack.kscope.engine

import kotlin.test.Test
import kotlin.test.assertNotEquals

class WorkspaceStampDisappearanceTest {
    @Test
    fun `a listed file that disappeared still contributes to the stamp`() {
        TestWorkspace().use { workspace ->
            val listing = listOf("Temporary.kt")
            val missing = workspaceStamp(workspace.root, listing)
            workspace.write("Temporary.kt", "class Temporary")
            val present = workspaceStamp(workspace.root, listing)
            assertNotEquals(missing, present)
            assertNotEquals(missing, workspaceStamp(workspace.root, emptyList()))
        }
    }
}
