package io.github.krosstack.kscope.store.generator

import io.github.krosstack.kscope.model.BuildRoot
import io.github.krosstack.kscope.model.BuildRootId
import io.github.krosstack.kscope.model.BuildSystem
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.ModuleTrait
import io.github.krosstack.kscope.model.Platform
import io.github.krosstack.kscope.model.Repository
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.SourceRoot
import io.github.krosstack.kscope.model.SourceRootKind
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.model.SourceSetRole
import io.github.krosstack.kscope.model.Target
import io.github.krosstack.kscope.model.TargetId
import io.github.krosstack.kscope.model.Variant
import io.github.krosstack.kscope.model.VariantId

internal class StructureFactGenerator(private val values: RandomValues) {
    fun repository() = Repository(
        id = RepositoryId(values.name()),
        name = values.name(),
        headRevision = values.maybe { values.text() },
        originUrl = values.maybe { values.text() },
        directory = values.repoPath(),
    )

    fun buildRoot() = BuildRoot(
        id = BuildRootId(values.name()),
        repository = RepositoryId(values.name()),
        directory = values.repoPath(),
        buildSystem = values.pick(BuildSystem.entries),
    )

    fun module() = Module(
        id = ModuleId(values.name()),
        buildRoot = BuildRootId(values.name()),
        path = values.name(),
        directory = values.repoPath(),
        plugins = values.listOfUpTo(2) { values.name() },
        traits = ModuleTrait.entries.filter { values.random.nextBoolean() }.toSet(),
        androidNamespace = values.maybe { values.name() },
        evidenceClass = values.pick(EvidenceClass.entries),
        certainty = values.pick(Certainty.entries),
        attributes = values.listOfUpTo(2) { values.name() to values.text() }.toMap(),
    )

    fun target() = Target(
        id = TargetId(values.name()),
        module = ModuleId(values.name()),
        name = values.name(),
        platform = values.pick(Platform.entries),
    )

    fun variant() = Variant(
        id = VariantId(values.name()),
        module = ModuleId(values.name()),
        name = values.name(),
        buildType = values.maybe { values.name() },
        flavors = values.listOfUpTo(2) { values.name() },
    )

    private fun sourceRoot() = SourceRoot(
        directory = values.repoPath(),
        kind = SourceRootKind(values.name()),
        generated = values.random.nextBoolean(),
    )

    fun sourceSet() = SourceSet(
        id = SourceSetId(values.name()),
        module = ModuleId(values.name()),
        name = values.name(),
        role = values.pick(SourceSetRole.entries),
        dependsOn = values.listOfUpTo(2) { SourceSetId(values.name()) },
        roots = values.listOfUpTo(2) { sourceRoot() },
        targets = values.listOfUpTo(2) { TargetId(values.name()) },
        variants = values.listOfUpTo(2) { VariantId(values.name()) },
        evidenceClass = values.pick(EvidenceClass.entries),
        certainty = values.pick(Certainty.entries),
    )
}
