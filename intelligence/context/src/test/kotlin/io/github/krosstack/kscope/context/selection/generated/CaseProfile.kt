package io.github.krosstack.kscope.context.selection.generated

internal enum class CaseProfile { ORDINARY, DENSE, TWINS, TIES }

internal fun caseProfileOf(seed: Long): CaseProfile {
    if (seed <= ORDINARY_SEEDS) return CaseProfile.ORDINARY
    val shaped = CaseProfile.entries.drop(1)
    return shaped[((seed - ORDINARY_SEEDS - 1) % shaped.size).toInt()]
}

private const val ORDINARY_SEEDS = 360L
