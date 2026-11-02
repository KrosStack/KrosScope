package io.github.krosstack.kscope.context.selection.generated

import kotlin.random.Random

internal fun budgetsUpTo(total: Int, seed: Long): List<Int> {
    val random = Random(seed xor total.toLong())
    val between = List(RANDOM_BUDGETS) { 1 + random.nextInt(maxOf(total, 1)) }
    return (listOf(1, total - 1, total, total + 1) + between).filter { it >= 1 }.distinct().sorted()
}

private const val RANDOM_BUDGETS = 3
