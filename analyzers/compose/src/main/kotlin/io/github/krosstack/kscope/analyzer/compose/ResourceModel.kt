package io.github.krosstack.kscope.analyzer.compose

import io.github.krosstack.kscope.model.TextRange

/** A resource by its type and name as written, such as `string` and `app_name`. */
internal data class ResourceKey(val type: ResourceType, val name: String)

/** A resource that a file defines: [declaration] is the whole element or file, [nameRange] the name. */
internal class ResourceDefinition(val key: ResourceKey, val declaration: TextRange, val nameRange: TextRange)

/** What one resource file defines. [problem] says why reading stopped early, if it did. */
internal class ParsedResourceFile(val definitions: List<ResourceDefinition>, val problem: String? = null)
