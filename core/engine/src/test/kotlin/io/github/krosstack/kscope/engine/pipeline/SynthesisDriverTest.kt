package io.github.krosstack.kscope.engine.pipeline

import io.github.krosstack.kscope.engine.RecordingProducer
import io.github.krosstack.kscope.engine.RecordingSynthesizer
import io.github.krosstack.kscope.engine.assertMatchesRebuild
import io.github.krosstack.kscope.engine.coreRegistry
import io.github.krosstack.kscope.engine.scanningSample
import io.github.krosstack.kscope.model.OwnershipKind
import kotlin.test.Test
import kotlin.test.assertEquals

class SynthesisDriverTest {
    private val greeter = "core/src/commonMain/kotlin/com/example/core/Greeter.kt"
    private val coreBuildScript = "core/build.gradle.kts"

    @Test
    fun `synthetic sources reach the producers and survive through the cache while the synthesizer is current`() {
        val synthesizer = RecordingSynthesizer("synthesizer")
        val producer = RecordingProducer("by-source-set", granularity = OwnershipKind.SOURCE_SET)
        scanningSample(coreRegistry(synthesizer, producer)) { workspace, engine ->
            engine.update()
            assertEquals(1, synthesizer.synthesizeCalls)
            assertEquals(2, producer.syntheticSources.size)

            workspace.write(greeter, "class Greeter\n")
            engine.update()
            assertEquals(1, synthesizer.synthesizeCalls)
            assertEquals(2, producer.syntheticSources.size)

            workspace.write(coreBuildScript, "plugins { kotlin(\"jvm\") }\n")
            engine.update()
            assertEquals(2, synthesizer.synthesizeCalls)
            engine.assertMatchesRebuild()
        }
    }

    @Test
    fun `synthetic sources invalidate the source set units that depend on them`() {
        val synthesizer = RecordingSynthesizer("synthesizer")
        val producer = RecordingProducer("by-source-set", granularity = OwnershipKind.SOURCE_SET)
        scanningSample(coreRegistry(synthesizer, producer)) { workspace, engine ->
            engine.update()
            producer.calls.clear()
            workspace.write(coreBuildScript, "plugins { id(\"x\") }\n")
            engine.update()
            assertEquals(6, producer.producedKeys.size)
            engine.assertMatchesRebuild()
        }
    }
}
