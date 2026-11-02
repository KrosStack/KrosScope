package io.github.krosstack.kscope.analyzer.android.linking

import io.github.krosstack.kscope.analyzer.android.rclass.JavaNames

/** What a class name written in an Android XML file stands for. */
internal sealed interface WrittenClass {
    /** A class the workspace may declare, by its qualified name with nested classes separated by dots. */
    data class Named(val qualifiedName: String) : WrittenClass

    /** A class of the platform or of a library that the workspace does not declare. */
    data object Framework : WrittenClass

    /** A name relative to the namespace of a module that has none. */
    data object NeedsNamespace : WrittenClass

    /** Text that is no class name, such as a manifest placeholder. */
    data object NotAClassName : WrittenClass
}

/** The symbolic references of namespace `jvm.class` and the reading of the class names that make them. */
internal object JvmClasses {
    const val NAMESPACE = "jvm.class"

    private val frameworkPrefixes =
        listOf("android.", "androidx.", "com.google.android.", "java.", "javax.", "kotlin.", "kotlinx.")

    /**
     * Resolves a name as Android does: `.Name` and `Name` are relative to the [namespace] of the module, anything with
     * a dot inside is qualified already. Only qualified names can belong to the platform or to a library.
     */
    fun read(written: String, namespace: String?): WrittenClass {
        val name = written.trim()
        val isRelative = name.startsWith('.') || '.' !in name
        val qualified = if (isRelative) qualify(name, namespace ?: return WrittenClass.NeedsNamespace) else name
        val dotted = qualified.replace('$', '.')
        return when {
            !JavaNames.isPackage(dotted) -> WrittenClass.NotAClassName
            !isRelative && frameworkPrefixes.any(dotted::startsWith) -> WrittenClass.Framework
            else -> WrittenClass.Named(dotted)
        }
    }

    private fun qualify(name: String, namespace: String): String =
        if (name.startsWith('.')) namespace + name else "$namespace.$name"
}
