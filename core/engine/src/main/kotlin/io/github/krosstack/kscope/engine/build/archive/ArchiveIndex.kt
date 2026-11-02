package io.github.krosstack.kscope.engine.build.archive

import io.github.krosstack.kscope.model.Module

/** The modules that build JAR files, found by file name from the module attribute `jvm.archives`. */
internal class ArchiveIndex(modules: List<Module>) {
    private val byFileName: Map<String, List<Module>> = modules
        .flatMap { module -> fileNamesOf(module).map { it to module } }
        .groupBy({ it.first }, { it.second })

    fun producersOf(fileName: String): List<Module> = byFileName[fileName].orEmpty()

    private fun fileNamesOf(module: Module): List<String> =
        module.attributes[ATTRIBUTE].orEmpty().split(',').map(String::trim).filter(String::isNotEmpty).distinct()

    private companion object {
        const val ATTRIBUTE = "jvm.archives"
    }
}
