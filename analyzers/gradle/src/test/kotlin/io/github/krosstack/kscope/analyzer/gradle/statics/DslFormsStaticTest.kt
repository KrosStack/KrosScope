package io.github.krosstack.kscope.analyzer.gradle.statics

import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.SourceSet
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Script forms that real builds use for dependencies and for the source set hierarchy. */
class DslFormsStaticTest {
    private val tree = StaticWorkspace("dsl-forms")
    private val forms = tree.provided()

    @AfterTest
    fun cleanUp() {
        tree.close()
    }

    private fun targetOf(edge: Relationship): String {
        val target = (edge.resolution as Resolution.Resolved).target
        return when (target) {
            is NodeRef.OfExternal -> target.coordinate
            is NodeRef.OfModule -> target.id.value
            else -> error("Unexpected target $target")
        }
    }

    private fun declaredBy(path: String) = forms.edgesOf(path).map {
        Triple(it.attributes["configuration"], targetOf(it), it.site?.range?.startLine)
    }

    @Test
    fun `the dependencies block of the kotlin extension declares the project dependencies like the top-level one`() {
        val declared = declaredBy(":jvmapp")
        assertEquals(
            listOf(
                Triple("implementation", "$ROOT:lib", 7),
                Triple("implementation", "maven:org.slf4j:slf4j-api", 8),
                Triple("testImplementation", "maven:org.jetbrains.kotlin:kotlin-test", 15),
            ),
            declared.sortedBy { it.third },
        )
    }

    @Test
    fun `a configure block over a list of source sets configures each of them`() {
        val edges = forms.dependsOnOf(":shared")
        assertEquals(listOf("commonMain"), edges.getValue("nonWebMain"))
        assertEquals(listOf("commonMain", "nonWebMain"), edges.getValue("jvmMain"))
        assertEquals(listOf("appleMain", "nonWebMain"), edges.getValue("iosMain"))
    }

    @Test
    fun `a source set that a script declares compiles for the targets of the source sets that depend on it`() {
        val shared = forms.sourceSets(":shared")
        assertEquals(listOf("iosArm64", "iosSimulatorArm64", "jvm"), targetNames(shared.getValue("nonWebMain")))
        assertEquals(listOf("jvm"), targetNames(shared.getValue("jvmMain")))
        assertEquals(emptyList(), forms.sourceSets(":loop").getValue("nonWebMain").targets)
    }

    private fun targetNames(sourceSet: SourceSet) = sourceSet.targets.map { it.value.substringAfterLast('/') }

    @Test
    fun `only the module whose hierarchy edges cannot be read is warned about`() {
        val warnings = forms.problems("gradle.static.custom-hierarchy-not-analysed")
        assertEquals(listOf("loop/build.gradle.kts"), warnings.map { it.location?.artifact?.path?.value })
    }

    @Test
    fun `a dependsOn edge that no source set block owns is reported instead of dropped`() {
        val warning = forms.problems("gradle.static.custom-hierarchy-not-analysed").single()
        assertEquals(DiagnosticSeverity.WARNING, warning.severity)
        assertEquals(13, warning.location?.range?.startLine)
        assertEquals(listOf("commonMain"), forms.dependsOnOf(":loop").getValue("nonWebMain"))
    }
}
