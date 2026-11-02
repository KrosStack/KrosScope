package io.github.krosstack.kscope.analyzer.android.manifest

import io.github.krosstack.kscope.analyzer.android.linking.ClassMention
import io.github.krosstack.kscope.analyzer.android.resources.ResourceKey
import io.github.krosstack.kscope.model.TextRange

/** A resource that an attribute of a component names, with the place of the attribute value. */
internal class ResourceMention(val target: ResourceKey, val range: TextRange)

/** The resources a component uses; [externals] counts those of the platform and of other packages. */
internal class ResourceMentions(val resources: List<ResourceMention>, val externals: Int)

/**
 * An element of the manifest that declares something: the application, one of its components or a permission.
 * [written] is `android:name` as written, null for an application without one; [classMention] is the class that
 * implements the component.
 */
internal data class ManifestComponent(
    val tag: String,
    val written: String?,
    val nameRange: TextRange?,
    val declaration: TextRange,
    val exported: String?,
    val actions: List<String>,
    val classMention: ClassMention?,
    val uses: ResourceMentions,
) {
    val isPermission: Boolean get() = tag == "permission"
}

/** [packageName] is the legacy `package` attribute, which older builds use in place of the namespace. */
internal class ParsedManifest(
    val packageName: String?,
    val components: List<ManifestComponent>,
    val problem: String?,
) {
    companion object {
        fun failed(problem: String?) = ParsedManifest(null, emptyList(), problem)
    }
}
