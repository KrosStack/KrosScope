package io.github.krosstack.kscope.analyzer.kotlin.worker.aa

import com.intellij.mock.MockComponentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.ResolveScopeManager
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KaResolutionScopeProvider
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinProjectStructureProvider
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import java.util.concurrent.ConcurrentHashMap

/**
 * Gives the code of a source module the scope that the Analysis API gives Kotlin: the module, the modules it refines
 * and the ones it depends on, with their libraries. The Java front end resolves a name inside the resolve scope of
 * the file that uses it, and the standalone project environment makes that scope every file of the session. Without
 * this, Java code resolves into modules it cannot see and what a module resolves to depends on which other modules
 * share its session. A file that exists only in memory, such as the copy the Java front end makes to infer a diamond,
 * counts as the file it was copied from or made for. Code outside a source module, such as the classes of a library,
 * keeps the scope of the session.
 */
internal class ModuleResolveScopes private constructor(
    private val project: Project,
    private val ofSession: ResolveScopeManager,
) : ResolveScopeManager() {
    private val scopes = ConcurrentHashMap<KaSourceModule, GlobalSearchScope>()

    override fun getResolveScope(element: PsiElement): GlobalSearchScope {
        val module = physicalFileOf(element)?.let { KotlinProjectStructureProvider.getModule(project, it, null) }
        val source = module as? KaSourceModule
        return source?.let { scopes.computeIfAbsent(it, ::scopeOf) } ?: ofSession.getResolveScope(element)
    }

    override fun getDefaultResolveScope(file: VirtualFile): GlobalSearchScope = ofSession.getDefaultResolveScope(file)

    override fun getUseScope(element: PsiElement): GlobalSearchScope = ofSession.getUseScope(element)

    private fun physicalFileOf(element: PsiElement): PsiFile? = generateSequence(element.containingFile) { file ->
        file.originalFile.takeUnless { it === file } ?: file.context?.containingFile
    }.take(MAX_CONTEXT_DEPTH).firstOrNull { it.isPhysical }

    private fun scopeOf(module: KaSourceModule): GlobalSearchScope =
        KaResolutionScopeProvider.getInstance(project).getResolutionScope(module)

    companion object {
        private const val MAX_CONTEXT_DEPTH = 8

        /** The project container refuses a second service under the same key, so the session's own goes first. */
        fun install(project: Project) {
            require(project is MockComponentManager) { "The standalone session has no project that takes services" }
            val container = project.picoContainer
            val key = ResolveScopeManager::class.java.name
            val ofSession = getInstance(project)
            container.unregisterComponent(key)
            container.registerComponentInstance(key, ModuleResolveScopes(project, ofSession))
        }
    }
}
