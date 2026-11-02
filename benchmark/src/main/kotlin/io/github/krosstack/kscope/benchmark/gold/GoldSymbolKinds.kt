package io.github.krosstack.kscope.benchmark.gold

internal object GoldSymbolKinds {
    fun matches(actualKind: String, goldKind: String?): Boolean {
        if (goldKind == null) return true
        val canonical = if (goldKind.startsWith(CATALOG_PREFIX)) catalogKindOf(goldKind) else declaredKindOf(goldKind)
        return actualKind == goldKind || actualKind == canonical
    }

    fun declaredKindOf(kind: String): String? = when {
        declaredPrefixes.any { kind.startsWith(it) } -> kind
        kind in layoutKinds -> "$RESOURCE_PREFIX${kind.removePrefix(ANDROID_PREFIX)}"
        kind.startsWith(MANIFEST_PREFIX) -> "$ANDROID_PREFIX$kind"
        else -> schemaKinds[kind]
    }

    fun catalogKindOf(kind: String): String = "$CATALOG_SYMBOL_PREFIX${kind.removePrefix(CATALOG_PREFIX)}"

    private const val ANDROID_PREFIX = "android."
    private const val RESOURCE_PREFIX = "android.resource."
    private const val MANIFEST_PREFIX = "manifest."
    private const val CATALOG_PREFIX = "catalog."
    private const val CATALOG_SYMBOL_PREFIX = "gradle.catalog."
    private val declaredPrefixes = listOf(RESOURCE_PREFIX, "aidl.", "proto.", "compose.resource.")
    private val layoutKinds = setOf("android.layout", "android.navigation")
    private val schemaKinds = mapOf("sq.table" to "sqldelight.table", "sq.query" to "sqldelight.query")
}
