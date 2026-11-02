package io.github.krosstack.kscope.context.selection.generated

import kotlin.random.Random

internal fun <T> Random.pick(items: List<T>): T = items[nextInt(items.size)]

internal fun Random.chance(percent: Int): Boolean = nextInt(PERCENT) < percent

private const val PERCENT = 100
