package io.github.krosstack.kscope.analyzer.kotlin.worker.aa

import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import io.github.krosstack.kscope.analyzer.kotlin.worker.SampleWorkspace
import io.github.krosstack.kscope.model.Platform
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What a Java file may resolve into is the module of the file, also for the copies the Java front end makes. */
class ModuleResolveScopesTest {
    private val a = SampleWorkspace.singleModule("a", "hidden/a", Platform.JVM)
    private val b = SampleWorkspace.singleModule("b", "hidden/b", Platform.JVM)

    private fun javaFile(workspace: AnalysisWorkspace, name: String): PsiJavaFile = workspace.sourceSets
        .flatMap { sourceSet -> sourceSet.files }
        .map { file -> file.psi }
        .filterIsInstance<PsiJavaFile>()
        .first { file -> file.name == name }

    @Test
    fun `the scope of a Java file holds the files of its module and not those of an unrelated one`() {
        AnalysisSessions.open(listOf(a, b)).use { workspace ->
            val scope = javaFile(workspace, "Caller.java").resolveScope
            assertTrue(scope.contains(javaFile(workspace, "Caller.java").virtualFile))
            assertFalse(scope.contains(javaFile(workspace, "Context.java").virtualFile))
        }
    }

    @Test
    fun `a copy of a Java file that exists only in memory has the scope of the file it was copied from`() {
        AnalysisSessions.open(listOf(a, b)).use { workspace ->
            val caller = javaFile(workspace, "Caller.java")
            val copy = caller.copy() as PsiFile
            assertFalse(copy.isPhysical)
            assertTrue(copy.resolveScope.contains(caller.virtualFile))
            assertFalse(copy.resolveScope.contains(javaFile(workspace, "Context.java").virtualFile))
        }
    }
}
