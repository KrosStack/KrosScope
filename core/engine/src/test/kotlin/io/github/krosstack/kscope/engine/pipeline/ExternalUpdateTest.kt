package io.github.krosstack.kscope.engine.pipeline

import io.github.krosstack.kscope.engine.TestWorkspace
import kotlin.test.Test
import kotlin.test.assertEquals

class ExternalUpdateTest {
    @Test
    fun `an update plans from externally committed units instead of an earlier read`() =
        TestWorkspace().use { workspace ->
            val path = "src/Main.kt"
            val original = "class Original\n"
            workspace.write(path, original)
            workspace.engine().use { reader ->
                reader.update()
                val originalHash = reader.graph().artifacts().single().contentHash
                workspace.write(path, "class Changed\n")
                workspace.engine().use { it.update() }
                workspace.write(path, original)

                val report = reader.update()

                assertEquals(1, report.unitsProduced)
                assertEquals(originalHash, reader.graph().artifacts().single().contentHash)
            }
        }
}
