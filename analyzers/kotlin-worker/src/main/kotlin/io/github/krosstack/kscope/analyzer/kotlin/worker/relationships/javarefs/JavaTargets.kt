package io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.javarefs

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.util.JavaPsiRecordUtil
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.Access
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.Target
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.WorkspaceIds
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.libraries.JavaLibraryIds
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor

private const val CONSTRUCTOR = "constructor"
private const val GENERATED = "generated"

/**
 * Maps what a Java reference resolves to onto the symbol model. Java code sees Kotlin declarations through light
 * classes, which are mapped back to the Kotlin declaration they were made from: accessors to their property, the
 * `INSTANCE` field to its object, a file facade (`FooKt`) to the Kotlin file that holds its declarations. Locals,
 * parameters and packages map to nothing, and so does a facade that merges several files.
 */
internal class JavaTargets(private val ids: WorkspaceIds) {
    fun map(element: PsiElement, access: Access, withLibrary: Boolean): Target? = when {
        element is PsiTypeParameter -> null
        element is KtLightElement<*, *> -> ofLightElement(element, access)
        element is PsiCompiledElement -> libraryTarget(element, withLibrary)
        element is PsiMember -> ofSourceMember(element, access)
        else -> null
    }

    private fun ofLightElement(light: KtLightElement<*, *>, access: Access): Target? =
        when (val origin = light.kotlinOrigin) {
            is KtPropertyAccessor -> workspace(origin.property)
            is KtClassOrObject -> ofClassOrigin(origin, light)
            is KtDeclaration -> ids.of(origin)?.let { Target.Workspace(it, fieldSubKind(light, origin, access)) }
            else -> (light as? KtLightClassForFacade)?.let(::ofFacade)
        }

    private fun ofFacade(facade: KtLightClassForFacade): Target? =
        facade.files.singleOrNull()?.let(ids::artifactOf)?.let { Target.FileFacade(it) }

    private fun ofClassOrigin(origin: KtClassOrObject, light: KtLightElement<*, *>): Target? {
        val subKind = when {
            light is PsiMethod && light.isConstructor -> CONSTRUCTOR
            light is PsiMethod -> GENERATED
            else -> null
        }
        val isInterface = origin is KtClass && origin.isInterface()
        return ids.of(origin)?.let { Target.Workspace(it, subKind, isInterface) }
    }

    private fun fieldSubKind(light: KtLightElement<*, *>, origin: KtDeclaration, access: Access): String? =
        access.subKind.takeIf { light is PsiField && (origin is KtProperty || origin is KtParameter) }

    private fun workspace(declaration: KtDeclaration): Target? = ids.of(declaration)?.let { Target.Workspace(it) }

    private fun ofSourceMember(member: PsiMember, access: Access): Target? {
        val owner = member as? PsiClass ?: member.containingClass
        val component = (member as? PsiMethod)?.let(JavaPsiRecordUtil::getRecordComponentForAccessor)
        return when {
            component != null && !member.isPhysical -> ids.of(component)?.let { Target.Workspace(it) }
            !member.isPhysical && owner != null -> generatedMember(owner, member)
            else -> declared(member, access)
        }
    }

    private fun generatedMember(owner: PsiClass, member: PsiMember): Target? {
        val subKind = if (member is PsiMethod && member.isConstructor) CONSTRUCTOR else GENERATED
        return ids.of(owner)?.let { Target.Workspace(it, subKind, owner.isInterface) }
    }

    private fun declared(member: PsiMember, access: Access): Target? {
        val subKind = access.subKind.takeIf { member is PsiField && member !is PsiEnumConstant }
        return ids.of(member)?.let { Target.Workspace(it, subKind, (member as? PsiClass)?.isInterface == true) }
    }

    private fun libraryTarget(element: PsiElement, withLibrary: Boolean): Target? {
        if (!withLibrary) return null
        return JavaLibraryIds.idOf(element)?.let { Target.Library(it, (element as? PsiClass)?.isInterface == true) }
    }
}
