package io.github.krosstack.kscope.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.isPrivate

public class SingleBehavingType(config: Config) :
    Rule(config, "A class, object or interface with behaviour has its own file.") {

    override fun visitKtFile(file: KtFile) {
        val behavingTypes = file.behavingTypes()
        if (behavingTypes.areLinkedByPrivateDeclarationsOf(file)) return
        val names = behavingTypes.joinToString { it.name.orEmpty() }
        report(Finding(Entity.atPackageOrFirstDecl(file), "$names each have behaviour. Give each its own file."))
    }
}

private fun KtFile.behavingTypes(): List<KtClassOrObject> {
    val sealedTypeNames = collectDescendantsOfType<KtClassOrObject>().filter { it.isSealed() }
        .mapNotNull { it.name }.toSet()
    return declarations.filterIsInstance<KtClassOrObject>().filter { it.hasBehaviour(sealedTypeNames) }
}

private fun KtClassOrObject.hasBehaviour(sealedTypeNames: Set<String>) =
    !isPrivate() && !isSealed() && !extendsAnyOf(sealedTypeNames) && declaredFunctionCount() >= 2

private fun KtClassOrObject.isSealed() = hasModifier(KtTokens.SEALED_KEYWORD)

private fun KtClassOrObject.extendsAnyOf(typeNames: Set<String>) =
    superTypeListEntries.any { (it.typeReference?.typeElement as? KtUserType)?.referencedName in typeNames }

private fun KtClassOrObject.declaredFunctionCount() = declarations.count { it is KtNamedFunction && it.name != null }

private fun List<KtClassOrObject>.areLinkedByPrivateDeclarationsOf(file: KtFile): Boolean {
    val privateNames = file.privateTopLevelNames()
    return map { it.referencedNames() intersect privateNames }.mergeOverlapping().size <= 1
}

private fun KtFile.privateTopLevelNames() = declarations.filterIsInstance<KtNamedDeclaration>()
    .filter { it.isPrivate() }.mapNotNull { it.name }.toSet()

private fun List<Set<String>>.mergeOverlapping(): List<Set<String>> = fold(emptyList()) { groups, names ->
    val (overlapping, apart) = groups.partition { group -> group.any { it in names } }
    apart + listOf(overlapping.flatten().toSet() + names)
}

private fun KtClassOrObject.referencedNames() =
    collectDescendantsOfType<KtSimpleNameExpression>().map { it.getReferencedName() }.toSet()
