package io.github.krosstack.kscope.engine.pipeline

import io.github.krosstack.kscope.engine.AnsweringLinker
import io.github.krosstack.kscope.engine.assertMatchesRebuild
import io.github.krosstack.kscope.engine.coreRegistry
import io.github.krosstack.kscope.engine.scanningSample
import io.github.krosstack.kscope.engine.store
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.Fingerprint
import kotlin.test.Test
import kotlin.test.assertEquals

class FailedUnitLinkingTest {
    private val mainFile = "app/src/main/kotlin/com/example/app/Main.kt"

    @Test
    fun `linkers run again when a unit that failed is produced successfully`() {
        val producer = FlakyReferencingProducer()
        val linker = AnsweringLinker("linker")
        scanningSample(coreRegistry(producer, linker)) { workspace, engine ->
            workspace.write(mainFile, "found-main\n")
            engine.update()
            assertEquals(1, linker.linkCalls)

            producer.failing = false
            engine.update()

            assertEquals(2, linker.linkCalls)
            engine.assertMatchesRebuild()
        }
    }

    @Test
    fun `linkers stay current while a unit keeps failing and after it recovered`() {
        val producer = FlakyReferencingProducer()
        val linker = AnsweringLinker("linker")
        scanningSample(coreRegistry(producer, linker)) { workspace, engine ->
            workspace.write(mainFile, "found-main\n")
            engine.update()
            engine.update()
            assertEquals(1, linker.linkCalls)

            producer.failing = false
            engine.update()
            engine.update()
            assertEquals(2, linker.linkCalls)
        }
    }

    @Test
    fun `a linker stored under a base of an older build is run once and then stays current`() {
        val linker = AnsweringLinker("linker")
        scanningSample(coreRegistry(FlakyReferencingProducer(), linker)) { workspace, engine ->
            workspace.write(mainFile, "found-main\n")
            engine.update()
            val stored = engine.store.batches().single { it.producer.id == "linker" }
            engine.store.replace(FactBatch(stored.producer, stored.owner, Fingerprint("older-base"), stored.facts))
            assertEquals(1, linker.linkCalls)

            engine.update()
            assertEquals(2, linker.linkCalls)

            engine.update()
            assertEquals(2, linker.linkCalls)
        }
    }
}
