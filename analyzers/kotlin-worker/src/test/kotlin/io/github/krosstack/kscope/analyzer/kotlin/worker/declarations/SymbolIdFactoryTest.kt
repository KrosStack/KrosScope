package io.github.krosstack.kscope.analyzer.kotlin.worker.declarations

import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMember
import com.intellij.psi.util.PsiTreeUtil
import io.github.krosstack.kscope.analyzer.kotlin.worker.SampleWorkspace
import io.github.krosstack.kscope.analyzer.kotlin.worker.WorkerIdentity
import io.github.krosstack.kscope.analyzer.kotlin.worker.aa.AnalysisSessions
import io.github.krosstack.kscope.analyzer.kotlin.worker.aa.OwnedFile
import io.github.krosstack.kscope.analyzer.kotlin.worker.symbols
import io.github.krosstack.kscope.model.SymbolId
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The factory is what a resolving pass uses to name targets, so it must agree with the declaration pass. */
class SymbolIdFactoryTest {
    private val samples = mapOf(
        "shapes" to "shapes/main",
        "overloads" to "overloads/main",
        "constructors" to "constructors/main",
        "flags" to "flags/main",
        "mixed" to "mixed/main",
        "docs" to "docs/main",
        "tests" to "tests/test",
    )

    private fun declarationIds(factory: SymbolIdFactory, file: OwnedFile): List<SymbolId?> {
        val psi: PsiFile = file.psi
        val module = file.owner.moduleName
        return when (psi) {
            is KtFile -> psi.collectDescendantsOfType<KtDeclaration>().map { factory.idOf(it, module) }
            is PsiJavaFile -> members(psi).map { factory.idOf(it, module) }
            else -> emptyList()
        }
    }

    private fun members(file: PsiJavaFile): Collection<PsiMember> =
        PsiTreeUtil.findChildrenOfType(file, PsiMember::class.java)

    @Test
    fun `the factory names every declaration exactly as the declaration pass did`() {
        samples.forEach { (module, directory) ->
            AnalysisSessions.open(listOf(SampleWorkspace.singleModule(module, directory))).use { workspace ->
                val batches = DeclarationPass(WorkerIdentity.producer).batchesOf(workspace)
                val emitted = batches.symbols().map { it.id }.toSet()
                val factory = SymbolIdFactory(workspace.owners)
                val named = workspace.sourceSets.flatMap { it.files }.flatMap { declarationIds(factory, it) }
                assertTrue(emitted.isNotEmpty())
                assertEquals(emitted, named.filterNotNull().toSet(), "Ids differ in the $module sample")
            }
        }
    }

    @Test
    fun `local declarations have no id`() {
        AnalysisSessions.open(listOf(SampleWorkspace.singleModule("shapes", "shapes/main"))).use { workspace ->
            val factory = SymbolIdFactory(workspace.owners)
            val file = workspace.sourceSets.single().files.single().psi as KtFile
            val localNames = setOf("Hidden", "inner", "nested")
            val locals = file.collectDescendantsOfType<KtDeclaration>().filter { it.name in localNames }
            assertEquals(3, locals.size)
            assertTrue(locals.all { factory.idOf(it) == null })
        }
    }
}
