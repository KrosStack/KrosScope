package io.github.krosstack.kscope.service.analysis

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.OpenOptions
import org.junit.jupiter.api.Tag
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun Relationship.certaintyOf(): Certainty? = (resolution as? Resolution.Resolved)?.certainty

/** Scans the reference workspace with the Gradle build trusted. Enabled with `-Pkscope.integration=true`. */
@Tag("gradle-integration")
class TrustedScanIntegrationTest {
    private val fixture = Path.of(checkNotNull(System.getProperty("kscope.fixture")))

    @Test
    fun `a trusted scan models the workspace exactly and a second scan reuses the cached model`() {
        val index = Files.createTempDirectory("kscope-trusted-scan")
        try {
            KrosScope.open(fixture, OpenOptions(trustBuild = true, indexDirectory = index)).use { scope ->
                val first = scope.scan()
                val structure = scope.info().structure
                assertEquals(0, first.diagnostics.errors)
                assertEquals(14, structure.modules)
                assertEquals(2, structure.buildRoots)
                assertTrue(structure.variants > 0 && structure.targets > 0)
                assertEquals(34L, scope.info().symbolsByKind["gradle.catalog.library"])
                assertTrue(scope.info().coverage.any { it.relation == "references" && it.evidence == "build-model" })
                val alternatives = scope.graph().edges(RelationKind.BINDS, "variant-alternative")
                assertTrue(alternatives.isNotEmpty())
                assertEquals(setOf(EvidenceClass.BUILD_MODEL), alternatives.map { it.evidenceClass }.toSet())
                assertEquals(setOf(Certainty.EXACT), alternatives.map { it.certaintyOf() }.toSet())
                val second = scope.scan()
                assertEquals(0, second.unitsProduced)
                assertTrue(second.stageMillis.getValue("build-model") < first.stageMillis.getValue("build-model"))
            }
        } finally {
            index.toFile().deleteRecursively()
        }
    }

    @Test
    fun `an untrusted scan of the same workspace reads the build statically and models the same variants`() {
        val index = Files.createTempDirectory("kscope-untrusted-scan")
        try {
            KrosScope.open(fixture, OpenOptions(indexDirectory = index)).use { scope ->
                scope.scan()
                val structure = scope.info().structure
                assertEquals(14, structure.modules)
                assertEquals(14, structure.variants)
            }
        } finally {
            index.toFile().deleteRecursively()
        }
    }
}
