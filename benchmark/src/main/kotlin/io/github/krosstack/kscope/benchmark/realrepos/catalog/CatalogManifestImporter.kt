package io.github.krosstack.kscope.benchmark.realrepos.catalog

import io.github.krosstack.kscope.benchmark.realrepos.RealCommitTask
import io.github.krosstack.kscope.benchmark.realrepos.RealRepositoryManifest
import io.github.krosstack.kscope.benchmark.realrepos.RealRepositorySpec
import io.github.krosstack.kscope.benchmark.realrepos.RealSplit
import io.github.krosstack.kscope.benchmark.realrepos.RealWorkspaceMember
import io.github.krosstack.kscope.benchmark.realrepos.corpus.CorpusGit
import io.github.krosstack.kscope.benchmark.realrepos.members
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

internal class CatalogManifestImporter(private val checkouts: Path) {
    fun import(catalogFile: Path, freezeFile: Path, split: RealSplit): RealRepositoryManifest {
        val bytes = Files.readAllBytes(catalogFile)
        val reader = Json { ignoreUnknownKeys = true }
        val catalog = reader.decodeFromString<ExternalCatalog>(String(bytes, Charsets.UTF_8))
        val freeze = reader.decodeFromString<CatalogFreezeFile>(Files.readString(freezeFile))
        CatalogFreeze.verify(bytes, freeze, catalog)
        require(catalog.tasks.isNotEmpty()) { "Catalog has no tasks." }
        val tasks = catalog.tasks.map { task(catalog, it, split) }
        val used = tasks.flatMap { it.members().map { member -> member.repository } }.toSortedSet()
        val specs = used.map { id ->
            val base = freeze.repositories.single { it.id == id }.baseCommit
            RealRepositorySpec(id, checkout(id).toString(), base)
        }
        return RealRepositoryManifest(
            specs,
            tasks,
            specs.associate { it.id to emptyMap() },
            "external catalog frozen as ${freeze.id}",
        )
    }

    private fun task(catalog: ExternalCatalog, task: CatalogTask, split: RealSplit): RealCommitTask {
        val withGold = task.repositories.filter { it.gold.isNotEmpty() }
        require(withGold.isNotEmpty()) { "Task ${task.id} has no gold." }
        val nested = withGold.size > 1
        val members = if (nested) task.repositories else withGold
        val primary = members.firstOrNull { nested && it.name == catalog.repository } ?: withGold.first()
        members.forEach(::verify)
        val gold = if (nested) withGold.flatMap { state -> state.gold.map { "${state.name}/$it" } } else primary.gold
        val workspace = members.map { RealWorkspaceMember(it.name, it.scenarioCommit, it.solutionCommit) }
            .takeIf { nested }.orEmpty()
        return RealCommitTask(
            "${primary.name}:${task.id}", primary.name, primary.scenarioCommit, primary.solutionCommit,
            committedAt(primary), task.prompt, gold, split, false, task.level, task.named, workspace,
        )
    }

    private fun committedAt(state: CatalogRepositoryState): Long =
        CorpusGit(checkout(state.name)).read("log", "--no-show-signature", "-1", "--format=%ct", state.solutionCommit)
            .trim().toLong()

    private fun verify(state: CatalogRepositoryState) {
        val git = CorpusGit(checkout(state.name))
        val commits = listOf(state.baseCommit, state.scenarioCommit, state.solutionCommit)
        require(commits.all(git::contains)) { "Commits of ${state.name} are missing from its checkout." }
        require(git.descends(state.scenarioCommit, state.baseCommit)) {
            "Scenario of ${state.name} does not follow its base."
        }
        require(git.descends(state.solutionCommit, state.scenarioCommit)) {
            "Solution of ${state.name} does not follow its scenario."
        }
    }

    private fun checkout(id: String): Path {
        val path = checkouts.resolve(id)
        require(Files.isDirectory(path)) { "No checkout directory for repository $id." }
        return path.toAbsolutePath().normalize()
    }
}
