package io.github.krosstack.kscope.spi

import io.github.krosstack.kscope.model.BuildRoot
import io.github.krosstack.kscope.model.BuildRootId
import io.github.krosstack.kscope.model.BuildSystem
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.SourceSetId
import kotlinx.serialization.KSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SpiSerializationTest {
    private val buildModel = BuildModel(
        buildRoots = listOf(BuildRoot(BuildRootId("r//"), RepositoryId("r"), RepoPath(""), BuildSystem.GRADLE)),
        modules = emptyList(),
        targets = emptyList(),
        variants = emptyList(),
        sourceSets = emptyList(),
        dependencies = emptyList(),
    )

    private val diagnostic = Diagnostic(DiagnosticSeverity.INFO, ReasonCode("gradle.static-model"), "Inferred", null)

    @Test
    fun `build model survives a JSON round trip`() {
        assertRoundTrip(BuildModel.serializer(), buildModel)
    }

    @Test
    fun `every build model result survives a JSON round trip`() {
        val results = listOf(
            BuildModelResult.Provided(buildModel, listOf(diagnostic)),
            BuildModelResult.Provided(buildModel, emptyList(), facts = listOf(Fact.OfDiagnostic(diagnostic))),
            BuildModelResult.NotApplicable,
            BuildModelResult.Failed(ReasonCode("gradle.tooling-failed"), "The build could not be configured"),
        )
        results.forEach { assertRoundTrip(BuildModelResult.serializer(), it) }
    }

    @Test
    fun `classpaths are runtime data and never serialized`() {
        val classpaths = mapOf(SourceSetId("r//:app@main") to listOf("/sdk/android.jar"))
        val provided = BuildModelResult.Provided(buildModel, emptyList(), classpaths)
        val encoded = KscopeJson.encodeToString(BuildModelResult.serializer(), provided)
        assertFalse("android.jar" in encoded)
        val decoded = KscopeJson.decodeFromString(BuildModelResult.serializer(), encoded)
        assertEquals(provided.copy(classpaths = emptyMap()), decoded)
    }

    @Test
    fun `build model results use stable lowercase discriminators`() {
        val encoded = KscopeJson.encodeToString(BuildModelResult.serializer(), BuildModelResult.NotApplicable)
        assertEquals("{\"type\":\"not-applicable\"}", encoded)
    }

    @Test
    fun `synthetic sources survive a JSON round trip`() {
        val source = SyntheticSource("gen/R.kt", "kotlin", "object R", SourceSetId("r//:app@main"), "android.resources")
        assertRoundTrip(SyntheticSource.serializer(), source)
    }

    @Test
    fun `analyzer capabilities are written as lowercase kebab names`() {
        AnalyzerCapability.entries.forEach { assertRoundTrip(AnalyzerCapability.serializer(), it) }
        val encoded = KscopeJson.encodeToString(AnalyzerCapability.serializer(), AnalyzerCapability.RESOLVED_REFERENCES)
        assertEquals("\"resolved-references\"", encoded)
    }

    private fun <T> assertRoundTrip(serializer: KSerializer<T>, value: T) {
        val encoded = KscopeJson.encodeToString(serializer, value)
        assertEquals(value, KscopeJson.decodeFromString(serializer, encoded))
    }
}
