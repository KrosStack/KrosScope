package io.github.krosstack.kscope.engine.build

import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.spi.BuildModel

/**
 * Converts a build model to the facts that persist it and back. Top-level lists are ordered by identity. The
 * dependencies of a model are its `DEPENDS_ON` relationships; any other fact a provider adds travels beside the model.
 */
internal object BuildModelCodec {
    val empty = BuildModel(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

    fun canonical(model: BuildModel): BuildModel = BuildModel(
        buildRoots = model.buildRoots.sortedBy { it.id.value },
        modules = model.modules.sortedBy { it.id.value },
        targets = model.targets.sortedBy { it.id.value },
        variants = model.variants.sortedBy { it.id.value },
        sourceSets = model.sourceSets.sortedBy { it.id.value },
        dependencies = model.dependencies.sortedBy { KscopeJson.encodeToString(Relationship.serializer(), it) },
    )

    fun toFacts(model: BuildModel, diagnostics: List<Diagnostic>, additional: List<Fact> = emptyList()): List<Fact> =
        model.buildRoots.map { Fact.OfBuildRoot(it) } +
            model.modules.map { Fact.OfModule(it) } +
            model.targets.map { Fact.OfTarget(it) } +
            model.variants.map { Fact.OfVariant(it) } +
            model.sourceSets.map { Fact.OfSourceSet(it) } +
            model.dependencies.map { Fact.OfRelationship(it) } +
            diagnostics.map { Fact.OfDiagnostic(it) } +
            additional

    fun fromFacts(facts: List<Fact>): BuildModel = canonical(
        BuildModel(
            buildRoots = facts.filterIsInstance<Fact.OfBuildRoot>().map { it.value },
            modules = facts.filterIsInstance<Fact.OfModule>().map { it.value },
            targets = facts.filterIsInstance<Fact.OfTarget>().map { it.value },
            variants = facts.filterIsInstance<Fact.OfVariant>().map { it.value },
            sourceSets = facts.filterIsInstance<Fact.OfSourceSet>().map { it.value },
            dependencies = facts.filterIsInstance<Fact.OfRelationship>().map { it.value }
                .filter { it.kind == RelationKind.DEPENDS_ON },
        ),
    )
}
