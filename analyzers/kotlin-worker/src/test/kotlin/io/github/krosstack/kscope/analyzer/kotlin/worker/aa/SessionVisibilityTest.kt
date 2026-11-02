package io.github.krosstack.kscope.analyzer.kotlin.worker.aa

import io.github.krosstack.kscope.analyzer.kotlin.worker.SampleWorkspace
import io.github.krosstack.kscope.model.Platform
import io.github.krosstack.kscope.spi.worker.WorkerModule
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaCallResolutionSuccess
import org.jetbrains.kotlin.analysis.api.resolution.KaSymbolResolutionSuccess
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.resolution.KtResolvable
import org.jetbrains.kotlin.resolution.KtResolvableCall
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Checks with the compiler that the session sees what the module plan promises. */
class SessionVisibilityTest {
    private fun module(name: String, directory: String, platform: Platform = Platform.JVM) =
        SampleWorkspace.module(name, platform, SampleWorkspace.sourceSet("vis", name, directory))

    private fun resolves(modules: List<WorkerModule>, file: String, callee: String): Boolean =
        AnalysisSessions.open(modules).use { workspace ->
            val ktFile = workspace.sourceSets.flatMap { it.files }.map { it.psi }
                .filterIsInstance<KtFile>().first { it.name == file }
            val call = ktFile.collectDescendantsOfType<KtCallExpression>().first { it.calleeExpression?.text == callee }
            analyze(call) { (call as KtResolvableCall).tryResolveCall() is KaCallResolutionSuccess }
        }

    private val common = module("common", "visibility/common", Platform.COMMON)
    private val jvm = module("jvm", "visibility/jvm").copy(dependsOn = listOf("common"))
    private val main = module("main", "visibility/main")

    @Test
    fun `a module sees the declarations of the modules it refines`() {
        assertTrue(resolves(listOf(common, jvm), "Jvm.kt", "shared"))
    }

    @Test
    fun `a module without the refinement edge does not see them`() {
        assertFalse(resolves(listOf(common, jvm.copy(dependsOn = emptyList())), "Jvm.kt", "shared"))
    }

    @Test
    fun `a regular dependency is expanded with the refinement closure of its target`() {
        val app = module("app", "visibility/app").copy(dependencies = listOf("jvm"))
        assertTrue(resolves(listOf(common, jvm, app), "App.kt", "fromJvm"))
        assertTrue(resolves(listOf(common, jvm, app), "App.kt", "shared"))
    }

    @Test
    fun `a friend module sees internal declarations of the module it is a friend of`() {
        val test = module("test", "visibility/test").copy(dependencies = listOf("main"), friends = listOf("main"))
        assertTrue(resolves(listOf(main, test), "ApiUser.kt", "publicApi"))
        assertTrue(resolves(listOf(main, test), "ApiUser.kt", "internalApi"))
    }

    @Test
    fun `a module that is not a friend does not see internal declarations`() {
        val other = module("other", "visibility/test").copy(dependencies = listOf("main"))
        assertTrue(resolves(listOf(main, other), "ApiUser.kt", "publicApi"))
        assertFalse(resolves(listOf(main, other), "ApiUser.kt", "internalApi"))
    }

    @Test
    fun `a directory shared by two modules belongs to each of them`() {
        val friend = module("friend", "visibility/test").copy(dependencies = listOf("main"), friends = listOf("main"))
        val stranger = module("stranger", "visibility/test").copy(dependencies = listOf("main"))
        AnalysisSessions.open(listOf(main, friend, stranger)).use { workspace ->
            val ofFriend = workspace.sourceSets.single { it.source.sourceSet.value.endsWith("@friend") }
            assertEquals("friend", ofFriend.files.single().owner.moduleName)
            assertEquals(3, workspace.sourceSets.count { it.files.isNotEmpty() })
        }
    }

    @Test
    fun `synthetic sources are visible to the code of their source set`() {
        val generated = SampleWorkspace.module(
            "gen",
            Platform.ANDROID,
            SampleWorkspace.sourceSet(
                "gen",
                "main",
                "generated/main",
                syntheticSources = listOf(SampleWorkspace.androidResourceStub()),
            ),
        )
        AnalysisSessions.open(listOf(generated)).use { workspace ->
            val uses = workspace.sourceSets.single().files.single().psi as KtFile
            val reference = uses.collectDescendantsOfType<KtNameReferenceExpression>()
                .first { it.getReferencedName() == "app_name" }
            val resolution = analyze(reference) { (reference as KtResolvable).tryResolveSymbols() }
            assertTrue(resolution is KaSymbolResolutionSuccess)
        }
    }
}
