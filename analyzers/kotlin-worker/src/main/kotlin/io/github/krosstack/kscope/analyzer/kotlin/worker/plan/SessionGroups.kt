package io.github.krosstack.kscope.analyzer.kotlin.worker.plan

import io.github.krosstack.kscope.spi.worker.WorkerModule
import io.github.krosstack.kscope.spi.worker.WorkerSourceSet
import java.io.IOException
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * Spreads the modules of a request over analysis sessions. The Analysis API looks a Java type up in the first source
 * file of the session that declares its name and gives up when that file lies outside the modules the asking module
 * sees. A Java name that two modules declare in different files would then hide one declaration from the other module,
 * and the facts of a module would depend on which other modules a request holds. Each module whose facts are wanted
 * is therefore placed in a session where no module outside what it sees declares a Java name that it sees. What a
 * module sees is what the compiler lets it use: its own refinements, its dependencies and friends, each with its
 * refinements. A module only reached through a dependency of a dependency is context, and stays out of the session
 * of a module whose Java names it would hide. A request without a Java name declared twice stays one session.
 */
internal class SessionGroups(private val modules: List<WorkerModule>) {
    private val byName = modules.associateBy { it.name }
    private val declarations: Map<String, Map<String, Set<String>>> =
        modules.associate { it.name to declarationsOf(it) }
    private val closures = mutableMapOf<String, Set<String>>()
    private val visibles = mutableMapOf<String, Set<String>>()
    private val contexts = mutableMapOf<String, Set<String>>()

    fun sessions(): List<List<WorkerModule>> {
        if (!hasRepeatedName()) return listOf(modules)
        val groups = mutableListOf<List<WorkerModule>>()
        modules.filter { module -> module.sourceSets.any { it.emitFactsFor } }.forEach { module ->
            val index = groups.indexOfFirst { fits(it + module) }
            if (index < 0) groups += listOf(module) else groups[index] = groups[index] + module
        }
        return groups.map(::requestFor)
    }

    private fun hasRepeatedName(): Boolean = declarations.values.flatMap { it.entries }
        .groupBy({ it.key }, { it.value })
        .values.any { files -> files.flatten().toSet().size > 1 }

    private fun fits(group: List<WorkerModule>): Boolean {
        val union = group.flatMap { contextOf(it.name) }.toSet()
        return group.all { member ->
            val visible = visibleFrom(member.name)
            hiddenFrom(visible, union - visible).isEmpty()
        }
    }

    private fun hiddenFrom(visible: Set<String>, others: Set<String>): List<String> {
        val seen = visible.map(declarations::getValue)
        val names = seen.flatMap { it.keys }.toSet()
        val files = seen.flatMap { it.values.flatten() }.toSet()
        return others.filter { other ->
            declarations.getValue(other).any { (name, paths) -> name in names && paths.any { it !in files } }
        }
    }

    private fun requestFor(group: List<WorkerModule>): List<WorkerModule> {
        val asked = group.map { it.name }.toSet()
        val included = group.flatMap { contextOf(it.name) }.toSet()
        return modules.filter { it.name in included }.map { module ->
            if (module.name in asked) module else module.copy(sourceSets = module.sourceSets.map(::withoutFacts))
        }
    }

    private fun withoutFacts(sourceSet: WorkerSourceSet) = sourceSet.copy(emitFactsFor = false)

    private fun contextOf(name: String): Set<String> = contexts.getOrPut(name) {
        val visible = visibleFrom(name)
        closureOf(name) - hiddenFrom(visible, closureOf(name) - visible).toSet()
    }

    private fun visibleFrom(name: String): Set<String> = visibles.getOrPut(name) {
        val module = byName.getValue(name)
        val direct = (module.dependsOn + module.dependencies + module.friends).filter { it in byName }
        (listOf(name) + direct).flatMapTo(linkedSetOf(), ::withDependsOnClosure)
    }

    private fun withDependsOnClosure(name: String): Set<String> {
        val reached = linkedSetOf(name)
        val pending = ArrayDeque(listOf(name))
        while (pending.isNotEmpty()) {
            byName.getValue(pending.removeFirst()).dependsOn
                .filter { it in byName && reached.add(it) }
                .forEach(pending::addLast)
        }
        return reached
    }

    private fun closureOf(name: String): Set<String> = closures.getOrPut(name) {
        val reached = linkedSetOf(name)
        val pending = ArrayDeque(listOf(name))
        while (pending.isNotEmpty()) {
            val module = byName.getValue(pending.removeFirst())
            (module.dependsOn + module.dependencies + module.friends)
                .filter { it in byName && reached.add(it) }
                .forEach(pending::addLast)
        }
        reached
    }

    private companion object {
        const val JAVA_EXTENSION = ".java"
        const val JAVA_LANGUAGE = "java"

        /** The Java names a module declares, each with the files that declare it. */
        fun declarationsOf(module: WorkerModule): Map<String, Set<String>> {
            val declared = module.sourceSets.flatMap { sourceSet ->
                val files = sourceSet.files.filter { it.absolutePath.endsWith(JAVA_EXTENSION) }
                    .mapNotNull { file -> textOf(file.absolutePath)?.let { file.absolutePath to it } }
                val stubs = sourceSet.syntheticSources.filter { it.language == JAVA_LANGUAGE }
                    .map { "${sourceSet.sourceSet.value}/${it.virtualPath}" to it.text }
                (files + stubs).flatMap { (key, text) -> JavaTypeNames(text).read().map { it to key } }
            }
            return declared.groupBy({ it.first }, { it.second }).mapValues { it.value.toSet() }
        }

        private fun textOf(path: String): String? = try {
            Path.of(path).readText()
        } catch (_: IOException) {
            null
        } catch (_: InvalidPathException) {
            null
        }
    }
}
