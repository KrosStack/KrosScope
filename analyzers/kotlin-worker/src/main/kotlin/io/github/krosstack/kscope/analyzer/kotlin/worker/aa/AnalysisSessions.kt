package io.github.krosstack.kscope.analyzer.kotlin.worker.aa

import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import io.github.krosstack.kscope.analyzer.kotlin.worker.plan.FileOwner
import io.github.krosstack.kscope.analyzer.kotlin.worker.plan.FileOwners
import io.github.krosstack.kscope.analyzer.kotlin.worker.plan.ModulePlan
import io.github.krosstack.kscope.analyzer.kotlin.worker.plan.PlannedModule
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.spi.worker.WorkerModule
import io.github.krosstack.kscope.spi.worker.WorkerSourceSet
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.analysis.api.standalone.buildStandaloneAnalysisAPISession
import org.jetbrains.kotlin.analysis.project.structure.builder.KtModuleProviderBuilder
import org.jetbrains.kotlin.analysis.project.structure.builder.KtSourceModuleBuilder
import org.jetbrains.kotlin.analysis.project.structure.builder.buildKtSourceModule
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * Builds one standalone session per request: a source module per [WorkerModule], with `dependsOn` edges kept,
 * regular dependencies expanded with each dependency's `dependsOn` closure, friends visible, and the Kotlin and Java
 * files of all its source sets together.
 */
internal object AnalysisSessions {
    fun open(modules: List<WorkerModule>): AnalysisWorkspace {
        val plan = ModulePlan.of(modules)
        val synthetic = SyntheticRoots.write(modules)
        val disposable = Disposer.newDisposable("kscope-analysis")
        return runCatching {
            val built = linkedMapOf<String, KaSourceModule>()
            val session = buildStandaloneAnalysisAPISession(disposable, false) {
                buildKtModuleProvider { addSourceModules(plan, synthetic, built) }
            }
            val owners = FileOwners(claims(modules, synthetic))
            val sourceSets = modules.flatMap { module ->
                val found = session.modulesWithFiles[built[module.name]].orEmpty()
                sourceSetFiles(module, found, owners)
            }
            AnalysisWorkspace(session, sourceSets, owners, plan.problems) {
                Disposer.dispose(disposable)
                synthetic.close()
            }
        }.onFailure {
            Disposer.dispose(disposable)
            synthetic.close()
        }.getOrThrow()
    }

    private fun KtModuleProviderBuilder.addSourceModules(
        plan: ModulePlan,
        synthetic: SyntheticRoots,
        built: MutableMap<String, KaSourceModule>,
    ) {
        platform = JvmPlatforms.defaultJvmPlatform
        val libraries = LibraryModules(this)
        plan.ordered.forEach { planned ->
            built[planned.module.name] = buildKtSourceModule {
                configure(planned, built, libraries, sourceRoots(planned.module, synthetic))
            }.also { addModule(it) }
        }
    }

    private fun KtSourceModuleBuilder.configure(
        planned: PlannedModule,
        built: Map<String, KaSourceModule>,
        libraries: LibraryModules,
        roots: List<Path>,
    ) {
        platform = planned.module.analysisPlatform().toTargetPlatform()
        moduleName = planned.module.name
        languageVersionSettings = multiplatformLanguageSettings()
        libraries.forModule(planned.module).forEach(::addRegularDependency)
        planned.dependsOn.forEach { addDependsOnDependency(built.getValue(it)) }
        planned.regularDependencies.forEach { addRegularDependency(built.getValue(it)) }
        planned.friends.forEach { addFriendDependency(built.getValue(it)) }
        roots.forEach(::addSourceRoot)
    }

    private fun sourceRoots(module: WorkerModule, synthetic: SyntheticRoots): List<Path> =
        module.sourceSets.flatMap { sourceSet ->
            sourceSet.files.mapNotNull { existingFile(it.absolutePath) } + listOfNotNull(synthetic.rootOf(sourceSet))
        }.distinct()

    private fun claims(modules: List<WorkerModule>, synthetic: SyntheticRoots): List<Pair<Path, FileOwner>> {
        val requested = modules.flatMap { module ->
            val claimed = mutableSetOf<Path>()
            module.sourceSets.flatMap { sourceSet ->
                sourceSet.files.mapNotNull { file ->
                    existingFile(file.absolutePath)?.takeIf { claimed.add(FileOwners.normalized(it)) }
                        ?.let { it to ownerOf(module, sourceSet, file.ref) }
                }
            }
        }
        return requested + synthetic.files.map { it.path to ownerOf(it.module, it.owner, null) }
    }

    private fun ownerOf(module: WorkerModule, sourceSet: WorkerSourceSet, artifact: ArtifactRef?) =
        FileOwner(module.name, sourceSet.module, sourceSet.sourceSet, artifact)

    private fun sourceSetFiles(module: WorkerModule, found: List<PsiFile>, owners: FileOwners): List<SourceSetFiles> {
        val byPath = found.associateBy { FileOwners.normalized(Path.of(it.virtualFile.path)) }
        return module.sourceSets.map { sourceSet ->
            val (present, missing) = sourceSet.files.partition { file ->
                existingFile(file.absolutePath)?.let { byPath.containsKey(FileOwners.normalized(it)) } == true
            }
            val owned = present.mapNotNull { file ->
                val psi = byPath.getValue(FileOwners.normalized(Path.of(file.absolutePath)))
                owners.of(file.absolutePath, module.name)
                    ?.takeIf { it.sourceSet == sourceSet.sourceSet }
                    ?.let { OwnedFile(psi, it, file) }
            }
            SourceSetFiles(module.name, sourceSet, owned, missing, module.classpath.isNotEmpty())
        }
    }

    private fun existingFile(path: String): Path? = try {
        Path.of(path).takeIf { Files.isRegularFile(it) }
    } catch (_: InvalidPathException) {
        null
    }
}
