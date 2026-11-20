package io.github.krosstack.kscope.model

/**
 * Names of the Android source sets this variant builds from, highest precedence first: the variant, its build type,
 * the combination of its flavors, each flavor in dimension order, then `main`.
 */
public fun Variant.sourceSetPrecedence(): List<String> {
    val combined = flavors.mapIndexed { index, flavor ->
        if (index == 0) flavor else flavor.replaceFirstChar(Char::uppercaseChar)
    }.joinToString("").ifEmpty { null }
    return (listOfNotNull(name, buildType, combined) + flavors + "main").distinct()
}
