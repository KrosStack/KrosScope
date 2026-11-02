package io.github.krosstack.kscope.analyzer.kotlin.worker.relationships

import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

/** A member that a compiler plugin adds to the classes carrying [annotation], whose qualified names are [qualified]. */
private class PluginMember(val annotation: String, val qualified: Set<String>)

private val serializer = PluginMember("Serializable", setOf("kotlinx.serialization.Serializable"))
private val parcelCreator =
    PluginMember("Parcelize", setOf("kotlinx.parcelize.Parcelize", "kotlinx.android.parcel.Parcelize"))

/**
 * The members that the serialization and parcelize compiler plugins add to a class: `serializer()` and `CREATOR`. The
 * standalone front end runs without the plugins, so a reference to such a member does not resolve; when its class is
 * a workspace class that carries the annotation of the plugin, the reference stands for the class.
 */
internal object SyntheticMembers {
    private val membersByName = mapOf("serializer" to serializer, "CREATOR" to parcelCreator)

    fun isPluginMemberName(name: String): Boolean = name in membersByName

    /** The class that owns the member [name], or null when it is not a class the plugin adds [name] to. */
    fun ownerOf(name: String, candidate: KtClassOrObject?): KtClassOrObject? {
        val member = membersByName[name] ?: return null
        val owner = candidate?.let(::classOfCompanion)
        return owner?.takeIf { it.annotationEntries.any { entry -> isAnnotation(entry, member) } }
    }

    private fun classOfCompanion(declaration: KtClassOrObject): KtClassOrObject? {
        val isCompanion = declaration is KtObjectDeclaration && declaration.isCompanion()
        return if (isCompanion) declaration.containingClassOrObject else declaration
    }

    private fun isAnnotation(entry: KtAnnotationEntry, member: PluginMember): Boolean {
        val written = entry.typeReference?.text?.substringBefore('<')?.trim() ?: return false
        return when {
            '.' in written -> written in member.qualified
            written != member.annotation -> false
            else -> importedAs(entry.containingKtFile, written)?.let { it in member.qualified } ?: true
        }
    }

    private fun importedAs(file: KtFile, simpleName: String): String? = file.importDirectives
        .filterNot { it.isAllUnder }
        .firstOrNull { (it.aliasName ?: it.importedFqName?.shortName()?.asString()) == simpleName }
        ?.importedFqName?.asString()
}
