package io.github.krosstack.kscope.analyzer.gradle.statics.layout

import io.github.krosstack.kscope.analyzer.gradle.script.bodies
import io.github.krosstack.kscope.analyzer.gradle.script.firstExpression
import io.github.krosstack.kscope.analyzer.gradle.script.firstLiteral
import io.github.krosstack.kscope.analyzer.gradle.statics.ScriptSource
import io.github.krosstack.kscope.spi.worker.ScriptCall

/** A block that configures one source set; [creates] says that it declares the source set. */
internal class SourceSetBlock(val name: String, val creates: Boolean, val calls: List<ScriptCall>)

private val creators = setOf("create", "register", "maybeCreate", "creating", "registering")
private val getters = setOf("getByName", "named", "getting", "findByName")
private val containerOperations =
    setOf("all", "configureEach", "whenObjectAdded", "withType", "matching", "forEach", "each")
private const val CONFIGURE = "configure"
private val listOfSourceSets = Regex("""(?:listOf|setOf|arrayOf)\((.*)\)""", RegexOption.DOT_MATCHES_ALL)
private val dependsOnTarget = Regex("""(?:getByName|named|findByName)\(\s*["'](\w+)["']\s*\)|(\w+)(?:\.get\(\))?$""")

/**
 * Finds the source set blocks of a script, in `sourceSets { }` and in `kotlin { sourceSets { } }`, in every form
 * the DSLs use: `commonMain.dependencies { }`, `named("x") { }`, `val x by getting { }`, `create("x") { }`,
 * `configure(listOf(a, b)) { }`, which is one block for each source set of the list, and the Groovy `x { }`.
 */
internal object SourceSetBlocks {
    fun of(source: ScriptSource): List<SourceSetBlock> {
        val inKotlin = source.calls.bodies("kotlin").filter { it.name == "sourceSets" && it.receiver == null }
        return (source.calls.bodies("sourceSets") + inKotlin.flatMap { it.body }).flatMap(::blocksOf)
    }

    /** The source sets a block makes this one depend on, by name. */
    fun dependsOn(block: SourceSetBlock): List<String> =
        block.calls.filter { it.name == "dependsOn" }.mapNotNull { it.firstExpression() }.mapNotNull(::sourceSetNamed)

    private fun blocksOf(statement: ScriptCall): List<SourceSetBlock> =
        if (statement.receiver == null && statement.name == CONFIGURE) {
            listedSourceSets(statement.firstExpression()).map { SourceSetBlock(it, false, statement.body) }
        } else {
            listOfNotNull(blockOf(statement))
        }

    private fun listedSourceSets(list: String?): List<String> =
        list?.let { listOfSourceSets.matchEntire(it.trim())?.groupValues?.get(1) }?.split(',').orEmpty()
            .mapNotNull(::sourceSetNamed)

    private fun sourceSetNamed(expression: String): String? =
        dependsOnTarget.find(expression.trim().trim('"', '\''))?.groupValues?.let { it[1].ifEmpty { it[2] } }
            ?.takeIf { it.isNotEmpty() }

    private fun blockOf(statement: ScriptCall): SourceSetBlock? {
        val name = statement.firstLiteral()
        val isNamedBlock = statement.name !in containerOperations && statement.body.isNotEmpty()
        return when {
            statement.receiver != null -> dependenciesBlock(statement)
            statement.name in creators -> name?.let { SourceSetBlock(it, true, statement.body) }
            statement.name in getters -> name?.let { SourceSetBlock(it, false, statement.body) }
            isNamedBlock -> SourceSetBlock(statement.name, false, statement.body)
            else -> null
        }
    }

    private fun dependenciesBlock(statement: ScriptCall): SourceSetBlock? = statement.receiver
        ?.takeIf { statement.name == "dependencies" }
        ?.let { SourceSetBlock(it.substringAfterLast('.'), false, listOf(statement)) }
}
