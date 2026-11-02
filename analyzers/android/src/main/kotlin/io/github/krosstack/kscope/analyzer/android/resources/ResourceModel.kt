package io.github.krosstack.kscope.analyzer.android.resources

import io.github.krosstack.kscope.analyzer.android.linking.ClassMention
import io.github.krosstack.kscope.model.TextRange

/** A resource by its type and name as written, such as `string` and `app_name` or `style` and `Theme.MyApp`. */
internal data class ResourceKey(val type: String, val name: String) {
    /** The form that references use, such as `string/app_name`. */
    val reference: String get() = "$type/$name"

    /** The name of the field in the generated `R` class: dots become underscores. */
    val fieldName: String get() = name.replace('.', '_')
}

/**
 * [members] are the attributes of a `declare-styleable`, as written; empty for every other resource. [isByReference] is
 * true for an id that an attribute other than `android:id` declares with `@+id`.
 */
internal class ResourceDefinition(
    val key: ResourceKey,
    val declaration: TextRange,
    val nameRange: TextRange,
    val members: List<String> = emptyList(),
    val isByReference: Boolean = false,
)

/** A reference from the resource [owner] to [target]; [subKind] says what kind of reference it is, if special. */
internal class ResourceUse(val owner: ResourceKey, val target: ResourceKey, val subKind: String?, val range: TextRange)

/**
 * What one resource file declares and refers to. [externalReferences] counts references to framework and other
 * packages' resources, which the workspace cannot answer; [problem] says why reading stopped early; [classMentions]
 * are the classes a layout or navigation graph names.
 */
internal class ParsedResourceFile(
    val definitions: List<ResourceDefinition>,
    val uses: List<ResourceUse>,
    val externalReferences: Int,
    val problem: String?,
    val classMentions: List<ClassMention> = emptyList(),
) {
    companion object {
        val empty = ParsedResourceFile(emptyList(), emptyList(), 0, null)
    }
}

internal class ResourceCollector {
    private val definitions = mutableListOf<ResourceDefinition>()
    private val uses = mutableListOf<ResourceUse>()
    private var externals = 0

    fun define(definition: ResourceDefinition) {
        definitions += definition
    }

    fun use(use: ResourceUse) {
        uses += use
    }

    fun external() {
        externals++
    }

    fun finish(problem: String?, classMentions: List<ClassMention> = emptyList()) =
        ParsedResourceFile(declared(), uses.toList(), externals, problem, classMentions)

    // An id that only attributes other than `android:id` declare is defined at the first of them.
    private fun declared(): List<ResourceDefinition> {
        val known = definitions.filterNot { it.isByReference }.mapTo(mutableSetOf()) { it.key }
        return definitions.filter { !it.isByReference || known.add(it.key) }
    }
}
