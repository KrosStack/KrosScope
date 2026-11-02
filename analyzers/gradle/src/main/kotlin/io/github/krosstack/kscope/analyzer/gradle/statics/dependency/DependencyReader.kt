package io.github.krosstack.kscope.analyzer.gradle.statics.dependency

import io.github.krosstack.kscope.analyzer.gradle.script.bodies
import io.github.krosstack.kscope.analyzer.gradle.script.positional
import io.github.krosstack.kscope.analyzer.gradle.statics.ScriptSource
import io.github.krosstack.kscope.analyzer.gradle.statics.layout.SourceSetBlocks
import io.github.krosstack.kscope.spi.worker.ScriptCall

/**
 * A dependency a script declares. [configuration] is the full name of the configuration, such as `commonMainApi`;
 * [sourceSet] is the source set whose block declares it, if any.
 */
internal class DependencyDeclaration(
    val configuration: String,
    val sourceSet: String?,
    val call: ScriptCall,
    val expression: String?,
    val source: ScriptSource,
)

private val notConfigurations = setOf("constraints", "components", "modules", "attributesSchema", "artifactTypes")

/**
 * Reads `dependencies { }` blocks of a project and of its source sets. The block that the Kotlin extension offers
 * (`kotlin { dependencies { } }`) declares the same project configurations as the top-level one.
 */
internal object DependencyReader {
    fun read(source: ScriptSource): List<DependencyDeclaration> {
        val inKotlin = source.calls.bodies("kotlin").filter { it.name == "dependencies" && it.receiver == null }
        val projectBlocks = source.calls.bodies("dependencies") + inKotlin.flatMap { it.body }
        val ofProject = projectBlocks.mapNotNull { declarationOf(it, null, source) }
        val ofSourceSets = SourceSetBlocks.of(source).flatMap { block ->
            block.calls.filter { it.name == "dependencies" }.flatMap { it.body }
                .mapNotNull { declarationOf(it, block.name, source) }
        }
        return ofProject + ofSourceSets
    }

    private fun declarationOf(call: ScriptCall, sourceSet: String?, source: ScriptSource): DependencyDeclaration? {
        val arguments = call.positional()
        val isAdd = call.name == "add"
        val name = if (isAdd) arguments.firstOrNull()?.literal else call.name
        val expression = arguments.getOrNull(if (isAdd) 1 else 0)?.expression
        val isDeclaration = call.receiver == null && call.name !in notConfigurations &&
            (expression != null || call.arguments.isNotEmpty())
        return name?.takeIf { isDeclaration }?.let {
            val configuration = if (sourceSet == null) it else sourceSet + it.replaceFirstChar { c -> c.uppercase() }
            DependencyDeclaration(configuration, sourceSet, call, expression, source)
        }
    }
}
