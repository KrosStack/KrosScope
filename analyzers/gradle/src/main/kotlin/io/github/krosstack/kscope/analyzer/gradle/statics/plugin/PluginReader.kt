package io.github.krosstack.kscope.analyzer.gradle.statics.plugin

import io.github.krosstack.kscope.analyzer.gradle.catalog.CatalogLookup
import io.github.krosstack.kscope.analyzer.gradle.catalog.CatalogReference
import io.github.krosstack.kscope.analyzer.gradle.catalog.CatalogResolver
import io.github.krosstack.kscope.analyzer.gradle.script.blocks
import io.github.krosstack.kscope.analyzer.gradle.script.bodies
import io.github.krosstack.kscope.analyzer.gradle.script.firstExpression
import io.github.krosstack.kscope.analyzer.gradle.script.firstLiteral
import io.github.krosstack.kscope.analyzer.gradle.script.namedArgument
import io.github.krosstack.kscope.analyzer.gradle.statics.ScriptDiagnostics
import io.github.krosstack.kscope.analyzer.gradle.statics.ScriptSource
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.spi.worker.ScriptCall

/** A plugin a script declares; [applied] is false for `apply false`. [reference] is the catalog entry of an alias. */
internal class PluginUse(
    val id: String,
    val applied: Boolean,
    val source: ScriptSource,
    val call: ScriptCall,
    val reference: CatalogReference?,
)

internal class PluginReading(val uses: List<PluginUse>, val problems: List<Diagnostic>)

/**
 * Reads the plugins a script declares: `plugins { id("x"); alias(libs.plugins.x); kotlin("jvm"); java }` and the
 * legacy `apply plugin: "x"`. Legacy plugin ids are given their current id.
 */
internal class PluginReader(private val catalogs: CatalogResolver) {
    fun read(source: ScriptSource): PluginReading {
        val declared = source.calls.bodies("plugins").map { it to aliasOf(it) }
        val uses = declared.mapNotNull { (call, lookup) -> useOf(call, lookup, source) } + legacyUses(source)
        val problems = declared.mapNotNull { (call, lookup) ->
            (lookup as? CatalogLookup.Missing)?.let {
                ScriptDiagnostics.aliasUnresolved(it.catalog, it.accessor, source.location(call))
            }
        }
        return PluginReading(uses, problems)
    }

    private fun aliasOf(call: ScriptCall): CatalogLookup =
        if (call.name == "alias") catalogs.lookup(call.firstExpression().orEmpty()) else CatalogLookup.NotACatalog

    private fun useOf(call: ScriptCall, lookup: CatalogLookup, source: ScriptSource): PluginUse? {
        val reference = (lookup as? CatalogLookup.Found)?.reference
        val id = idOf(call, reference) ?: return null
        return PluginUse(PluginTable.canonical(id), call.assignments["apply"] != "false", source, call, reference)
    }

    private fun idOf(call: ScriptCall, reference: CatalogReference?): String? = when (call.name) {
        "id" -> call.firstLiteral()
        "alias" -> reference?.entry?.value
        "kotlin" -> call.firstLiteral()?.let { "org.jetbrains.kotlin.$it" }
        else -> call.name.takeIf { call.arguments.isEmpty() && call.body.isEmpty() && call.receiver == null }
    }

    private fun legacyUses(source: ScriptSource): List<PluginUse> = source.calls.blocks("apply").mapNotNull { call ->
        call.namedArgument("plugin")?.literal?.let { PluginUse(PluginTable.canonical(it), true, source, call, null) }
    }
}
