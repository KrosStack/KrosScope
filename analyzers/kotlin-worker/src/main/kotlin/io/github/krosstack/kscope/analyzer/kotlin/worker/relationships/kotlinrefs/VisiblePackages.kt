package io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.kotlinrefs

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiPackage
import io.github.krosstack.kscope.analyzer.kotlin.worker.aa.JavaSourcePackages
import org.jetbrains.kotlin.analysis.api.platform.packages.KotlinPackageProviderFactory
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KaResolutionScopeProvider
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.name.FqName

/**
 * Tells whether a package exists for the module of the file being analysed: some Kotlin declaration, Java source or
 * library class in what the module sees lies in it or below it. The Analysis API answers for Kotlin sources within
 * that scope, but takes a package of Java classes and libraries from every root of the session, so a package of a
 * jar that only another module lists would exist for this one too.
 */
internal class VisiblePackages(private val module: KaModule, private val javaSources: JavaSourcePackages) {
    private val project = module.project
    private val scope = KaResolutionScopeProvider.getInstance(project).getResolutionScope(module)

    fun exist(name: FqName): Boolean = inKotlin(name) || inJavaSources(name) || inLibraries(name)

    private fun inKotlin(name: FqName): Boolean =
        KotlinPackageProviderFactory.getInstance(project).createPackageProvider(scope).doesKotlinOnlyPackageExist(name)

    private fun inJavaSources(name: FqName): Boolean = name.asString() in javaSources.inScopeOf(module, scope)

    private fun inLibraries(name: FqName): Boolean =
        JavaPsiFacade.getInstance(project).findPackage(name.asString())?.let(::hasClassesBelow) == true

    private fun hasClassesBelow(root: PsiPackage): Boolean =
        root.getClasses(scope).isNotEmpty() || root.getSubPackages(scope).any(::hasClassesBelow)
}
