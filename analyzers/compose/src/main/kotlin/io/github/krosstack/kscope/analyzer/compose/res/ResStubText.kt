package io.github.krosstack.kscope.analyzer.compose.res

import io.github.krosstack.kscope.analyzer.compose.ResourceType

private const val INDENT = "    "
private const val LIBRARY = "org.jetbrains.compose.resources"

internal const val RES = "Res"
internal const val READ_BYTES = "readBytes"
internal const val GET_URI = "getUri"

/** The holders of `Res` in the order the generator writes them. */
internal val holders: List<String> = ResourceType.entries.map { it.holder }.distinct()

internal fun ResPlan.visibility(): String = if (isPublic) "public" else "internal"

/**
 * The Kotlin source of the signature-only stub of the resource class: `Res` with the object for each kind of resource,
 * the two functions that read files, and an extension property for each resource. The bodies are placeholders; the
 * types of the properties are the ones of the resources library, which the module's classpath may or may not have.
 */
internal object ResStubText {
    fun render(plan: ResPlan): String {
        val visibility = plan.visibility()
        val res = listOf(
            "$visibility object $RES {",
            holders.joinToString("\n") { "$INDENT$visibility object $it" },
            "$INDENT$visibility suspend fun $READ_BYTES(path: String): ByteArray = TODO()",
            "$INDENT$visibility fun $GET_URI(path: String): String = TODO()",
            "}",
        ).joinToString("\n")
        val accessors = plan.accessors.joinToString("\n") { accessorText(visibility, it) }
        val body = if (accessors.isEmpty()) res else "$res\n\n$accessors"
        return "package ${plan.pkg.name}\n\n$body\n"
    }

    private fun accessorText(visibility: String, accessor: ResAccessor): String {
        val type = accessor.key.type
        val name = KotlinNames.written(accessor.name)
        return "$visibility val $RES.${type.holder}.$name: $LIBRARY.${type.accessorType} get() = TODO()"
    }
}
