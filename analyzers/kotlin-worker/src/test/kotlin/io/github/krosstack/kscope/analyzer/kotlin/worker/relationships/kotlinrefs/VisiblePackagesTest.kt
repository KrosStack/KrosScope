package io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.kotlinrefs

import io.github.krosstack.kscope.analyzer.kotlin.worker.SampleWorkspace
import io.github.krosstack.kscope.analyzer.kotlin.worker.aa.AnalysisSessions
import io.github.krosstack.kscope.model.Platform
import io.github.krosstack.kscope.spi.worker.WorkerModule
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import kotlin.test.Test
import kotlin.test.assertEquals

/** A package exists for a module when something it sees lies in it or below it, whatever kind of source that is. */
class VisiblePackagesTest {
    private val own = SampleWorkspace.singleModule("own", "javapackage", Platform.JVM)
    private val asking = SampleWorkspace.singleModule("asking", "hidden/a", Platform.JVM)

    private fun existing(modules: List<WorkerModule>, file: String, names: List<String>): List<String> =
        AnalysisSessions.open(modules).use { workspace ->
            val ktFile = workspace.sourceSets.flatMap { it.files }.map { it.psi }
                .filterIsInstance<KtFile>().first { it.name == file }
            analyze(ktFile) {
                val packages = VisiblePackages(useSiteModule, workspace.javaPackages)
                names.filter { packages.exist(FqName(it)) }
            }
        }

    @Test
    fun `a package that only Java sources of the module fill exists with the packages above it`() {
        val names = listOf("acme", "acme.legacy", "acme.legacy.deeper", "acme.other", "nowhere")
        assertEquals(listOf("acme", "acme.legacy"), existing(listOf(own), "Use.kt", names))
    }

    @Test
    fun `a package that only Java sources of an unrelated module fill does not exist`() {
        val names = listOf("acme", "acme.legacy")
        assertEquals(emptyList(), existing(listOf(asking, own), "Qualified.kt", names))
        val dependent = asking.copy(dependencies = listOf("own"))
        assertEquals(names, existing(listOf(dependent, own), "Qualified.kt", names))
    }

    @Test
    fun `a package of the standard library and the JDK exists`() {
        val names = listOf("java.util", "kotlin.collections", "java")
        assertEquals(names, existing(listOf(own), "Use.kt", names))
    }
}
