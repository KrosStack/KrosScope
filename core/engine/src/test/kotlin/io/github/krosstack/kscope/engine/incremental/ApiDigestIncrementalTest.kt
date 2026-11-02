package io.github.krosstack.kscope.engine.incremental

import io.github.krosstack.kscope.engine.assertMatchesRebuild
import io.github.krosstack.kscope.engine.build.BuildModelCodec
import io.github.krosstack.kscope.engine.store
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApiDigestIncrementalTest {
    @Test
    fun `a body edit produces the library and skips the application`() = apiWorkspace().use { workspace ->
        val producer = ApiRecordingProducer()
        workspace.engine(apiRegistry(producer)).use { engine ->
            engine.update()
            producer.recording.calls.clear()
            workspace.write(LIB_SOURCE, "lib API\nbody two\n")

            val report = engine.update()

            assertEquals(2, report.unitsProduced)
            assertEquals(2, report.unitsSkipped)
            assertTrue(producer.recording.producedKeys.single().endsWith(":lib@main"))
            engine.assertMatchesRebuild()
        }
    }

    @Test
    fun `an API edit produces both source sets`() = apiWorkspace().use { workspace ->
        val producer = ApiRecordingProducer()
        workspace.engine(apiRegistry(producer)).use { engine ->
            engine.update()
            producer.recording.calls.clear()
            workspace.write(LIB_SOURCE, "new lib API\nbody two\n")

            val report = engine.update()

            assertEquals(3, report.unitsProduced)
            assertEquals(1, report.unitsSkipped)
            assertEquals(2, producer.recording.producedKeys.size)
            assertTrue(producer.recording.producedKeys.any { it.endsWith(":app@main") })
            assertTrue(producer.recording.producedKeys.any { it.endsWith(":lib@main") })
            engine.assertMatchesRebuild()
        }
    }

    @Test
    fun `unchanged sources are cached and only an edited artifact is digested`() = apiWorkspace().use { workspace ->
        val producer = ApiRecordingProducer()
        workspace.engine(apiRegistry(producer)).use { engine ->
            engine.update()
            assertEquals(listOf(listOf(APP_SOURCE, LIB_SOURCE)), producer.apiDigester.calls)
            producer.apiDigester.calls.clear()
            producer.recording.calls.clear()

            val unchanged = engine.update()

            assertEquals(0, unchanged.unitsProduced)
            assertEquals(4, unchanged.unitsSkipped)
            assertTrue(producer.recording.calls.isEmpty())
            assertTrue(producer.apiDigester.calls.isEmpty())
            workspace.write(LIB_SOURCE, "lib API\nbody two\n")
            engine.update()
            assertEquals(listOf(listOf(LIB_SOURCE)), producer.apiDigester.calls)
        }
    }

    @Test
    fun `an absent API digest uses content and its absence is cached`() = apiWorkspace().use { workspace ->
        workspace.write(LIB_SOURCE, "no digest\nbody one\n")
        val producer = ApiRecordingProducer()
        workspace.engine(apiRegistry(producer)).use { engine ->
            engine.update()
            producer.apiDigester.calls.clear()
            producer.recording.calls.clear()
            workspace.write(LIB_SOURCE, "no digest\nbody two\n")

            assertEquals(3, engine.update().unitsProduced)
            assertEquals(2, producer.recording.producedKeys.size)
            assertEquals(listOf(listOf(LIB_SOURCE)), producer.apiDigester.calls)
            producer.apiDigester.calls.clear()
            assertEquals(0, engine.update().unitsProduced)
            assertTrue(producer.apiDigester.calls.isEmpty())
        }
    }

    @Test
    fun `a source that was not examined counts with its content and is asked about again`() =
        apiWorkspace().use { workspace ->
            workspace.write(LIB_SOURCE, "unexamined\nbody one\n")
            val producer = ApiRecordingProducer()
            workspace.engine(apiRegistry(producer)).use { engine ->
                engine.update()
                producer.apiDigester.calls.clear()
                producer.recording.calls.clear()

                assertEquals(0, engine.update().unitsProduced)
                assertEquals(listOf(listOf(LIB_SOURCE)), producer.apiDigester.calls)
                workspace.write(LIB_SOURCE, "unexamined\nbody two\n")
                engine.update()
                assertEquals(2, producer.recording.producedKeys.size)
            }
        }

    @Test
    fun `digests that arrive after a digester could not run produce the dependents once`() =
        apiWorkspace().use { workspace ->
            val producer = ApiRecordingProducer()
            producer.apiDigester.examines = false
            workspace.engine(apiRegistry(producer)).use { engine ->
                engine.update()
                producer.recording.calls.clear()
                producer.apiDigester.calls.clear()
                producer.apiDigester.examines = true

                engine.update()

                assertEquals(listOf(listOf(APP_SOURCE, LIB_SOURCE)), producer.apiDigester.calls)
                assertTrue(producer.recording.producedKeys.single().endsWith(":app@main"))
                producer.recording.calls.clear()
                producer.apiDigester.calls.clear()
                assertEquals(0, engine.update().unitsProduced)
                assertTrue(producer.apiDigester.calls.isEmpty())
                engine.assertMatchesRebuild()
            }
        }

    @Test
    fun `a version change digests every source and produces every unit`() = apiWorkspace().use { workspace ->
        val producer = ApiRecordingProducer()
        workspace.engine(apiRegistry(producer)).use { engine ->
            engine.update()
            producer.apiDigester.calls.clear()
            producer.recording.calls.clear()
            producer.apiDigester.version = "2"

            val report = engine.update()

            assertEquals(2, report.unitsProduced)
            assertEquals(2, report.unitsSkipped)
            assertEquals(2, producer.recording.producedKeys.size)
            assertEquals(listOf(listOf(APP_SOURCE, LIB_SOURCE)), producer.apiDigester.calls)
        }
    }

    @Test
    fun `another source set in the same module changes only with the API`() = apiWorkspace().use { workspace ->
        workspace.write("lib/src/test/kotlin/LibTest.kt", "test API\nbody one\n")
        val producer = ApiRecordingProducer()
        workspace.engine(apiRegistry(producer)).use { engine ->
            engine.update()
            val model = BuildModelCodec.fromFacts(engine.store.batches().flatMap { it.facts })
            assertTrue(model.sourceSets.single { it.name == "test" }.dependsOn.isNotEmpty())
            producer.recording.calls.clear()
            workspace.write(LIB_SOURCE, "lib API\nbody two\n")
            assertEquals(2, engine.update().unitsProduced)
            assertTrue(producer.recording.producedKeys.single().endsWith(":lib@main"))
            producer.recording.calls.clear()
            workspace.write(LIB_SOURCE, "new API\nbody two\n")
            assertEquals(4, engine.update().unitsProduced)
            assertTrue(producer.recording.producedKeys.any { it.endsWith(":lib@test") })
            assertEquals(3, producer.recording.producedKeys.size)
            engine.assertMatchesRebuild()
        }
    }
}
