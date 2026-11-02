package io.github.krosstack.kscope.engine.incremental

import io.github.krosstack.kscope.engine.RecordingProducer
import io.github.krosstack.kscope.engine.ScanEngine
import io.github.krosstack.kscope.engine.build.BuildModelCodec
import io.github.krosstack.kscope.engine.build.ModelIndex
import io.github.krosstack.kscope.engine.hashing.FingerprintBuilder
import io.github.krosstack.kscope.engine.hashing.digestOf
import io.github.krosstack.kscope.engine.kotlinSource
import io.github.krosstack.kscope.engine.store
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.SourceSetId
import kotlin.test.Test
import kotlin.test.assertEquals

class LegacySourceSetFingerprintTest {
    @Test
    fun `a producer without a digester keeps every legacy fingerprint and invalidates dependents`() =
        apiWorkspace().use { workspace ->
            val producer = RecordingProducer("test.legacy", granularity = OwnershipKind.SOURCE_SET)
            workspace.engine(apiRegistry(producer)).use { engine ->
                engine.update()
                assertLegacyFingerprints(engine, producer)
                producer.calls.clear()
                workspace.write(LIB_SOURCE, "lib API\nbody two\n")

                val report = engine.update()

                assertEquals(3, report.unitsProduced)
                assertEquals(1, report.unitsSkipped)
                assertEquals(2, producer.producedKeys.size)
                assertLegacyFingerprints(engine, producer)
            }
        }

    private fun assertLegacyFingerprints(engine: ScanEngine, producer: RecordingProducer) {
        val model = BuildModelCodec.fromFacts(engine.store.batches().flatMap { it.facts })
        val index = ModelIndex(model)
        val definitions = ScopeDigests(emptyList())
        val artifacts = engine.graph().artifacts().filter { it.type == kotlinSource }.sortedBy { it.ref.path.value }
        val bySourceSet = artifacts.groupBy { it.sourceSet }
        engine.store.units().filter { it.producer == producer.descriptor.producer }.forEach { unit ->
            val sourceSet = model.sourceSets.single { it.id.value == unit.owner.key }
            val scope = index.dependencyScope(sourceSet.module)
            val expected = FingerprintBuilder()
                .add(producer.descriptor.producer.id)
                .add(producer.descriptor.producer.version)
                .add(sourceSet.id.value)
                .addAll(scope.map { it.value })
                .addAll(index.moduleDependenciesOf(scope).flatMap { it.fingerprintParts })
                .addAll(
                    scope.flatMap { module ->
                        index.sourceSetsOf(module).flatMap { member ->
                            listOf(
                                member.id.value,
                                definitions.definition(member),
                                legacyArtifacts(member.id, bySourceSet),
                                definitions.synthetic(member.id),
                                definitions.classpath(member.id),
                            )
                        }
                    },
                )
                .build()
            assertEquals(expected, unit.fingerprint)
        }
    }

    private fun legacyArtifacts(id: SourceSetId, members: Map<SourceSetId?, List<Artifact>>): String =
        members[id]?.let { artifacts ->
            digestOf(artifacts.flatMap { listOf(it.ref.path.value, it.contentHash.value) })
        }.orEmpty()
}
