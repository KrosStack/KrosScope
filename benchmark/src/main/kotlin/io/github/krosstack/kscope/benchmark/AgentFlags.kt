package io.github.krosstack.kscope.benchmark

import java.nio.file.Path

internal class AgentFlags private constructor(private val values: Map<String, String>, private val set: Set<String>) {
    fun value(name: String): String? = values[name]

    fun has(name: String): Boolean = name in set

    fun path(name: String, fallback: String): Path = Path.of(values[name] ?: fallback).toAbsolutePath().normalize()

    fun int(name: String, fallback: Int): Int? {
        val text = values[name] ?: return fallback
        return text.toIntOrNull()?.takeIf { it > 0 }
    }

    fun ids(name: String): Set<String>? =
        values[name]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()

    companion object {
        fun parse(args: List<String>, valued: Set<String>, switches: Set<String>): AgentFlags? {
            val values = mutableMapOf<String, String>()
            val set = mutableSetOf<String>()
            var index = 0
            while (index < args.size) {
                val name = args[index]
                when {
                    name in switches -> set += name
                    name in valued && index + 1 < args.size && name !in values -> values[name] = args[++index]
                    else -> return null
                }
                index++
            }
            return AgentFlags(values, set)
        }
    }
}
