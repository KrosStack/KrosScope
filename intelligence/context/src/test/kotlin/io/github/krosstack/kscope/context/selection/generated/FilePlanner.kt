package io.github.krosstack.kscope.context.selection.generated

import io.github.krosstack.kscope.model.ModuleId
import kotlin.random.Random

internal class FilePlanner(
    private val random: Random,
    private val seed: Long,
    private val profile: CaseProfile = CaseProfile.ORDINARY,
) {
    fun plan(): List<FilePlan> {
        val repositories = REPOSITORIES.take(repositoryCount())
        val plans = repositories.flatMap(::repositoryPlans)
        return withTwins(withKeyCollision(withSharedPath(plans)))
    }

    private fun repositoryCount(): Int {
        val wanted = 1 + random.nextInt(REPOSITORIES.size)
        return if (seed % FALLBACK_EVERY == 0L) maxOf(wanted, 2) else wanted
    }

    private fun repositoryPlans(repository: String): List<FilePlan> {
        val modules = MODULES.shuffled(random).take(1 + random.nextInt(MODULES.size))
        val paths = generateSequence { "${random.pick(DIRECTORIES)}/${random.pick(NAMES)}" }
            .distinct().take(1 + random.nextInt(MAX_SOURCE_FILES)).toList()
        val sources = paths.map { FilePlan(repository, it, moduleOf(repository, modules), FileKind.SOURCE) }
        val testPath = "tests/${paths.first().substringAfter('/')}Test"
        val tests = listOf(FilePlan(repository, testPath, moduleOf(repository, modules), FileKind.TEST))
            .filter { random.chance(TEST_PERCENT) }
        return sources + tests
    }

    private fun moduleOf(repository: String, modules: List<String>): ModuleId? =
        if (random.chance(NO_MODULE_PERCENT)) null else ModuleId("$repository//:${random.pick(modules)}")

    private fun withSharedPath(plans: List<FilePlan>): List<FilePlan> {
        if (seed % FALLBACK_EVERY != 0L) return plans
        val sources = plans.filter { it.kind == FileKind.SOURCE }
        val first = sources.first()
        val second = sources.first { it.repository != first.repository }
        val shared = ModuleId("shared//:common")
        return plans.map {
            when (it) {
                first -> it.copy(module = shared)
                second -> it.copy(path = first.path, module = shared)
                else -> it
            }
        }
    }

    private fun withTwins(plans: List<FilePlan>): List<FilePlan> {
        if (profile != CaseProfile.TWINS) return plans
        val anchor = plans.first { it.kind == FileKind.SOURCE }
        return plans + (1..TWIN_FILES).map { anchor.copy(path = "twin/File$it", twin = it) }
    }

    private fun withKeyCollision(plans: List<FilePlan>): List<FilePlan> {
        if (profile != CaseProfile.TIES && seed % COLLISION_EVERY != COLLISION_REMAINDER) return plans
        return plans + listOf(
            FilePlan("app", "pair/x:y", ModuleId("app//:alpha"), FileKind.SOURCE),
            FilePlan("app:pair/x", "y", ModuleId("app:pair/x//:alpha"), FileKind.SOURCE),
        )
    }

    private companion object {
        val REPOSITORIES = listOf("app", "lib", "tools")
        val MODULES = listOf("zeta", "alpha", "mid", "beta")
        val DIRECTORIES = listOf("core", "ui", "data", "util")
        val NAMES = listOf("Parser", "Reader", "Writer", "Model", "Cache", "Index")
        const val MAX_SOURCE_FILES = 4
        const val TEST_PERCENT = 40
        const val NO_MODULE_PERCENT = 10
        const val FALLBACK_EVERY = 5L
        const val COLLISION_EVERY = 11L
        const val COLLISION_REMAINDER = 4L
        const val TWIN_FILES = 3
    }
}
