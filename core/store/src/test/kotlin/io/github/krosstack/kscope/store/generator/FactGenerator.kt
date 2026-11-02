package io.github.krosstack.kscope.store.generator

import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.Fingerprint
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.model.ProducerRef
import kotlin.random.Random

/** Builds arbitrary fact batches that are reproducible from the seed. */
internal class FactGenerator(seed: Int) {
    private val values = RandomValues(Random(seed))
    private val structure = StructureFactGenerator(values)
    private val content = ContentFactGenerator(values, RandomReferences(values))

    fun batches(count: Int): List<FactBatch> = List(count) { index ->
        batch(OwnershipUnit(values.pick(OwnershipKind.entries), "unit-$index ${values.text()}"))
    }

    fun batch(owner: OwnershipUnit): FactBatch {
        val producer = ProducerRef("producer-${values.random.nextInt(PRODUCERS)}", "1.${values.random.nextInt(2)}")
        val facts = values.listOfUpTo(MAX_FACTS) { fact() }
        return FactBatch(producer, owner, Fingerprint(values.hex()), facts)
    }

    private fun fact(): Fact {
        val type = values.random.nextInt(FACT_TYPES)
        return if (type < STRUCTURE_TYPES) structureFact(type) else contentFact(type)
    }

    private fun structureFact(type: Int): Fact = when (type) {
        0 -> Fact.OfRepository(structure.repository())
        1 -> Fact.OfBuildRoot(structure.buildRoot())
        2 -> Fact.OfModule(structure.module())
        3 -> Fact.OfTarget(structure.target())
        4 -> Fact.OfVariant(structure.variant())
        else -> Fact.OfSourceSet(structure.sourceSet())
    }

    private fun contentFact(type: Int): Fact = when (type) {
        STRUCTURE_TYPES -> Fact.OfArtifact(content.artifact())
        STRUCTURE_TYPES + 1, STRUCTURE_TYPES + 2 -> Fact.OfSymbol(content.symbol())
        STRUCTURE_TYPES + 3, STRUCTURE_TYPES + 4, STRUCTURE_TYPES + 5 -> Fact.OfRelationship(content.relationship())
        STRUCTURE_TYPES + 6 -> Fact.OfSymbolicReference(content.symbolicReference())
        STRUCTURE_TYPES + 7 -> Fact.OfMetric(content.metric())
        else -> Fact.OfDiagnostic(content.diagnostic())
    }

    private companion object {
        const val PRODUCERS = 3
        const val MAX_FACTS = 40
        const val STRUCTURE_TYPES = 6
        const val FACT_TYPES = 15
    }
}
