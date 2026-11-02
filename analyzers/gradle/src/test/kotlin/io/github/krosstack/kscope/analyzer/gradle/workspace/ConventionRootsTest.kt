package io.github.krosstack.kscope.analyzer.gradle.workspace

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.Platform
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.SourceRoot
import io.github.krosstack.kscope.model.SourceRootKind
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.Target
import io.github.krosstack.kscope.model.TargetId
import io.github.krosstack.kscope.spi.BuildModel
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun BuildModel.rootsOf(sourceSet: String) = sourceSets.single { it.name == sourceSet }
    .roots.map { Triple(it.directory.value, it.kind.value, it.generated) }

class ConventionRootsTest {
    private val workspace = RootsWorkspace()

    @AfterTest
    fun cleanUp() {
        workspace.close()
    }

    private fun supplement(module: Module, vararg sourceSets: SourceSet): BuildModel =
        workspace.supplement(workspace.model(listOf(module), sourceSets.toList()))

    @Test
    fun `a module whose only sources are in a conventional directory gets a main source set`() {
        workspace.file("sync/protocol/src/main/proto/sync.proto")
        val module = workspace.module(":sync:protocol", "sync/protocol")
        val target = Target(TargetId("r//:sync:protocol#target/jvm"), module.id, "jvm", Platform.JVM)
        val supplemented = workspace.supplement(workspace.model(listOf(module), emptyList(), listOf(target)))
        val main = supplemented.sourceSets.single()
        assertEquals("r//:sync:protocol@main", main.id.value)
        assertEquals(listOf(Triple("sync/protocol/src/main/proto", "proto", false)), supplemented.rootsOf("main"))
        assertEquals(listOf(target.id), main.targets)
        assertEquals(EvidenceClass.CONVENTION, main.evidenceClass)
        assertEquals(Certainty.HEURISTIC, main.certainty)
    }

    @Test
    fun `a module without any conventional directory gets no source set`() {
        workspace.file("lib/src/main/other/x.txt")
        assertTrue(supplement(workspace.module(":lib", "lib")).sourceSets.isEmpty())
    }

    @Test
    fun `a module that has source sets does not get a main one`() {
        workspace.file("lib/src/main/cpp/x.cpp")
        val lib = workspace.module(":lib", "lib")
        val supplemented = supplement(lib, workspace.sourceSet(lib, "test", emptyList()))
        assertEquals(listOf("test"), supplemented.sourceSets.map { it.name })
    }

    @Test
    fun `directories the model lacks are added to the source set they belong to and nothing is added twice`() {
        workspace.file("data/src/commonMain/kotlin/A.kt")
        workspace.file("data/src/commonMain/sqldelight/A.sq")
        workspace.file("data/src/commonMain/composeResources/strings.xml")
        workspace.file("data/src/commonMain/resources/r.txt")
        val data = workspace.module(":data", "data")
        val known = SourceRoot(RepoPath("data/src/commonMain/kotlin"), SourceRootKind("kotlin"), generated = false)
        val supplemented = supplement(data, workspace.sourceSet(data, "commonMain", listOf(known)))
        assertEquals(
            listOf(
                Triple("data/src/commonMain/composeResources", "compose.resources", false),
                Triple("data/src/commonMain/kotlin", "kotlin", false),
                Triple("data/src/commonMain/resources", "resources", false),
                Triple("data/src/commonMain/sqldelight", "sqldelight", false),
            ),
            supplemented.rootsOf("commonMain"),
        )
    }

    @Test
    fun `android directories and the manifest are roots`() {
        workspace.file("app/src/main/AndroidManifest.xml")
        workspace.file("app/src/main/res/values/strings.xml")
        workspace.file("app/src/main/aidl/Api.aidl")
        workspace.file("app/src/main/jni/jni.c")
        val app = workspace.module(":app", "app")
        val supplemented = supplement(app, workspace.sourceSet(app, "main", emptyList()))
        assertEquals(
            listOf(
                Triple("app/src/main/AndroidManifest.xml", "android.manifest", false),
                Triple("app/src/main/aidl", "aidl", false),
                Triple("app/src/main/jni", "jni", false),
                Triple("app/src/main/res", "android.res", false),
            ),
            supplemented.rootsOf("main"),
        )
    }

    @Test
    fun `roots of the model below the build directory stay generated`() {
        workspace.file("app/src/main/kotlin/A.kt")
        val app = workspace.module(":app", "app")
        val generated = SourceRoot(RepoPath("app/build/generated/ksp"), SourceRootKind("kotlin"), generated = true)
        val supplemented = supplement(app, workspace.sourceSet(app, "main", listOf(generated)))
        assertEquals(
            listOf(
                Triple("app/build/generated/ksp", "kotlin", true),
                Triple("app/src/main/kotlin", "kotlin", false),
            ),
            supplemented.rootsOf("main"),
        )
    }

    @Test
    fun `names are matched exactly whatever the case rules of the file system`() {
        workspace.file("lib/src/main/Kotlin/A.kt")
        val lib = workspace.module(":lib", "lib")
        val supplemented = supplement(lib, workspace.sourceSet(lib, "main", emptyList()))
        assertEquals(emptyList(), supplemented.rootsOf("main"))
    }

    @Test
    fun `a source directory of a modeled kind that the build removed is not restored by convention`() {
        workspace.file("lib/src/main/java/Old.java")
        workspace.file("lib/src/current/java/Current.java")
        val lib = workspace.module(":lib", "lib")
        val configured = SourceRoot(RepoPath("lib/src/current/java"), SourceRootKind("java"), generated = false)
        val supplemented = supplement(lib, workspace.sourceSet(lib, "main", listOf(configured)))
        assertEquals(listOf(Triple("lib/src/current/java", "java", false)), supplemented.rootsOf("main"))
    }
}
