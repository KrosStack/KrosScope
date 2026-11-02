package io.github.krosstack.kscope.service

/** A result of a query together with the level of the index that produced it. */
public class Leveled<out T>(public val level: ScanLevel?, public val value: T)

/** The level the index of this workspace was built at, or null while it has no index. */
public fun KrosScope.indexLevel(): ScanLevel? = memory.levelMemory.level()

/** Pairs [value] with the level of this workspace's index. */
public fun <T> KrosScope.leveled(value: T): Leveled<T> = Leveled(indexLevel(), value)
