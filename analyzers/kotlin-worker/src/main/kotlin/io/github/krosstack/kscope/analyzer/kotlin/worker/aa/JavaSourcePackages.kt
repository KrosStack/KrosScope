package io.github.krosstack.kscope.analyzer.kotlin.worker.aa

import com.intellij.psi.PsiJavaFile
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import java.util.concurrent.ConcurrentHashMap

/**
 * The packages of the Java files of a session that a module sees, each with the packages above it. The Java front end
 * lists the packages of directories, and the scope of a source module holds files, not directories, so the packages
 * of Java sources have to be read from the files themselves.
 */
internal class JavaSourcePackages(private val files: List<PsiJavaFile>) {
    private val seenBy = ConcurrentHashMap<KaModule, Set<String>>()

    fun inScopeOf(module: KaModule, scope: GlobalSearchScope): Set<String> = seenBy.computeIfAbsent(module) {
        files.filter { file -> file.virtualFile?.let(scope::contains) == true }
            .flatMapTo(hashSetOf()) { file -> withParents(file.packageName) }
    }

    private fun withParents(packageName: String): List<String> =
        packageName.split('.').filter { it.isNotEmpty() }.runningReduce { parent, name -> "$parent.$name" }
}
