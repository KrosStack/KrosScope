package io.github.krosstack.kscope.analyzer.jni.mangling

private val primitives = mapOf(
    "Int" to "I",
    "int" to "I",
    "Long" to "J",
    "long" to "J",
    "Short" to "S",
    "short" to "S",
    "Byte" to "B",
    "byte" to "B",
    "Char" to "C",
    "char" to "C",
    "Boolean" to "Z",
    "boolean" to "Z",
    "Float" to "F",
    "float" to "F",
    "Double" to "D",
    "double" to "D",
)

private val boxes = mapOf(
    "I" to "java/lang/Integer",
    "J" to "java/lang/Long",
    "S" to "java/lang/Short",
    "B" to "java/lang/Byte",
    "C" to "java/lang/Character",
    "Z" to "java/lang/Boolean",
    "F" to "java/lang/Float",
    "D" to "java/lang/Double",
)

private val primitiveArrays = primitives.filterKeys { it.first().isUpperCase() }
    .mapKeys { "${it.key}Array" }.mapValues { "[${it.value}" }

private val knownClasses = mapOf(
    "String" to "java/lang/String",
    "CharSequence" to "java/lang/CharSequence",
    "Any" to "java/lang/Object",
    "Object" to "java/lang/Object",
    "Number" to "java/lang/Number",
    "Throwable" to "java/lang/Throwable",
    "Class" to "java/lang/Class",
    "List" to "java/util/List",
    "MutableList" to "java/util/List",
    "Collection" to "java/util/Collection",
    "MutableCollection" to "java/util/Collection",
    "Iterable" to "java/lang/Iterable",
    "MutableIterable" to "java/lang/Iterable",
    "Set" to "java/util/Set",
    "MutableSet" to "java/util/Set",
    "Map" to "java/util/Map",
    "MutableMap" to "java/util/Map",
)

private val jvmPackages = listOf("java.", "javax.", "android.", "androidx.", "kotlin.jvm.")
private val projectionWords = Regex("^(out|in)\\s+")
private val annotations = Regex("@[\\w.]+(\\([^)]*\\))?\\s*")

/**
 * The JVM descriptor of a type as a declaration spells it, for the long form of a JNI name. Primitive types, arrays of
 * them and the standard classes are known. A class of the workspace is found by its simple name through [classes],
 * which answers with its binary name such as `pkg/Outer$Inner`; a name that nothing answers has no descriptor, and the
 * caller falls back to the short name.
 */
internal class JvmDescriptors(private val classes: (String) -> String?) {
    fun of(typeText: String): String? {
        val text = typeText.replace(annotations, "").trim()
        val core = text.removeSuffix("?").trim()
        val element = elementOfArray(core)
        return if (element != null) of(element)?.let { "[$it" } else simple(core, text.endsWith("?"))
    }

    private fun elementOfArray(type: String): String? = when {
        type.endsWith("[]") -> type.removeSuffix("[]")

        type.endsWith("...") -> type.removeSuffix("...")

        type.startsWith("Array<") && type.endsWith(">") ->
            boxed(type.removePrefix("Array<").removeSuffix(">").trim().replace(projectionWords, ""))

        else -> null
    }

    // The elements of an `Array<Int>` are objects: only `IntArray` and the like hold primitives.
    private fun boxed(element: String): String {
        val isPrimitive = element in primitives && element.first().isUpperCase()
        return if (isPrimitive) "$element?" else element
    }

    private fun simple(type: String, isNullable: Boolean): String? = when (type) {
        in primitiveArrays -> primitiveArrays.getValue(type)
        in primitives -> primitive(type, isNullable)
        else -> classDescriptor(type)
    }

    private fun primitive(name: String, isNullable: Boolean): String {
        val code = primitives.getValue(name)
        val isKotlinBoxed = isNullable && name.first().isUpperCase()
        return if (isKotlinBoxed) "L${boxes.getValue(code)};" else code
    }

    private fun classDescriptor(type: String): String? {
        val name = type.substringBefore('<').trim()
        val binary = when {
            name in knownClasses -> knownClasses[name]
            '.' in name && jvmPackages.any { name.startsWith(it) } -> name.replace('.', '/')
            name.startsWith("kotlin.") -> knownClasses[name.removePrefix("kotlin.")]
            else -> classes(name.substringAfterLast('.'))
        }
        return binary?.let { "L$it;" }
    }
}
