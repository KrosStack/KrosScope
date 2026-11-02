package io.github.krosstack.kscope.engine.build

import io.github.krosstack.kscope.engine.hashing.FingerprintBuilder
import io.github.krosstack.kscope.model.BuildRoot
import io.github.krosstack.kscope.model.BuildRootId
import io.github.krosstack.kscope.model.BuildSystem
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Fingerprint
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.Repository
import io.github.krosstack.kscope.model.SourceRoot
import io.github.krosstack.kscope.model.SourceRootKind
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.model.SourceSetRole
import io.github.krosstack.kscope.spi.BuildModel

/**
 * The build model of a repository that no build model provider covers: one build root at the repository root, one
 * module that stands for the whole repository and one main source set rooted at the repository root, so that every
 * file of the repository belongs to the module. The module is marked with [ATTRIBUTE]. The model depends on the
 * identity of the repository alone.
 */
internal class RepositoryModule(private val repository: Repository) {
    fun model(): BuildModel {
        val root = BuildRoot(BuildRootId("${repository.id.value}//"), repository.id, RepoPath(""), BuildSystem.NONE)
        val module = moduleOf(root)
        return BuildModel(
            buildRoots = listOf(root),
            modules = listOf(module),
            targets = emptyList(),
            variants = emptyList(),
            sourceSets = listOf(sourceSetOf(module)),
            dependencies = emptyList(),
        )
    }

    fun fingerprint(): Fingerprint =
        FingerprintBuilder().add(producer.id).add(producer.version).add(repository.id.value).build()

    fun notice() = Diagnostic(
        severity = DiagnosticSeverity.INFO,
        code = ReasonCode("build-model.repository-module"),
        message = "No build model provider yielded a model for '${repository.name}'; its files form one module.",
        location = null,
    )

    private fun moduleOf(root: BuildRoot) = Module(
        id = ModuleId("${root.id.value}$PATH"),
        buildRoot = root.id,
        path = PATH,
        directory = RepoPath(""),
        plugins = emptyList(),
        traits = emptySet(),
        androidNamespace = null,
        evidenceClass = EvidenceClass.CONVENTION,
        certainty = Certainty.HEURISTIC,
        attributes = mapOf(ATTRIBUTE to "true"),
    )

    private fun sourceSetOf(module: Module) = SourceSet(
        id = SourceSetId("${module.id.value}@$SOURCE_SET"),
        module = module.id,
        name = SOURCE_SET,
        role = SourceSetRole.MAIN,
        dependsOn = emptyList(),
        roots = listOf(SourceRoot(RepoPath(""), SourceRootKind(ROOT_KIND), generated = false)),
        targets = emptyList(),
        variants = emptyList(),
        evidenceClass = EvidenceClass.CONVENTION,
        certainty = Certainty.HEURISTIC,
    )

    companion object {
        /** The attribute of a module that stands for a whole repository because no build describes it. */
        const val ATTRIBUTE = "kscope.repository-module"

        val producer = ProducerRef("kscope.build.repository-module", "1")

        private const val PATH = ":"
        private const val SOURCE_SET = "main"
        private const val ROOT_KIND = "repository"
    }
}
