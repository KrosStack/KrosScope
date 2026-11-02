package io.github.krosstack.kscope.analyzer.gradle

import io.github.krosstack.kscope.analyzer.gradle.mapping.MappedModel
import io.github.krosstack.kscope.analyzer.gradle.mapping.SampleModel
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.spi.BuildModel
import io.github.krosstack.kscope.spi.BuildModelResult
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestInstance
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs the real Gradle build of the reference workspace: it needs the declared Gradle distribution, the Android SDK
 * and the build dependencies, and takes minutes. Enabled with `-Pkscope.integration=true`.
 */
@Tag("gradle-integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GradleToolingIntegrationTest {
    private val fixture: Path = Path.of(checkNotNull(System.getProperty("kscope.fixture")))
    private val index: Path = Path.of(checkNotNull(System.getProperty("kscope.integration.index")))
    private val provider = GradleToolingProvider(index.also { it.toFile().deleteRecursively() })

    private val result: BuildModelResult by lazy {
        provider.provide(WorkspaceRequests.of(fixture)).also(::writeForInspection)
    }

    private val provided: BuildModelResult.Provided
        get() = result as? BuildModelResult.Provided ?: error("Gradle did not provide a model: $result")

    private val sample: SampleModel
        get() = SampleModel(MappedModel(provided.model, provided.classpaths, provided.diagnostics))

    private fun writeForInspection(result: BuildModelResult) {
        if (result !is BuildModelResult.Provided) return
        val json = KscopeJson.encodeToString(BuildModel.serializer(), result.model)
        Files.writeString(index.resolve("build-model.json"), json)
        val sizes = result.classpaths.entries.map { "${it.key.value} ${it.value.size}" }
        Files.writeString(index.resolve("classpaths.txt"), sizes.joinToString(System.lineSeparator()))
    }

    @Test
    fun `the reference workspace is modelled with its modules and build roots`() {
        assertEquals(listOf("tasktide//", "tasktide//build-logic"), provided.model.buildRoots.map { it.id.value })
        val expected = listOf(
            ":app", ":core:legacy", ":core:model", ":data:db", ":data:settings", ":desktop", ":feature:settings",
            ":feature:tasks", ":native:bridge", ":sync:engine", ":sync:protocol", ":sync:service", ":ui:shared",
        )
        val modules = provided.model.modules.filter { it.buildRoot.value == "tasktide//" }
        assertEquals(expected, modules.map { it.path })
    }

    @Test
    fun `the app module has its flavored variants and its android facts`() {
        val app = sample.module(":app")
        assertEquals("sample.tasktide.app", app.androidNamespace)
        assertEquals("36", app.attributes["android.compileSdk"])
        assertEquals("true", app.attributes["android.buildFeatures.viewBinding"])
        assertEquals("String", app.attributes["android.buildConfigField.SYNC_ENDPOINT"])
        val variants = provided.model.variants.filter { it.module == app.id }.map { it.name }
        assertEquals(listOf("demoDebug", "demoRelease", "fullDebug", "fullRelease"), variants)
    }

    @Test
    fun `kotlin multiplatform source sets keep their dependsOn edges and targets`() {
        val sourceSets = sample.sourceSets(":core:model")
        assertTrue(sourceSets.keys.containsAll(listOf("commonMain", "androidMain", "desktopMain")))
        val desktopMain = sourceSets.getValue("desktopMain")
        assertEquals(listOf("tasktide//:core:model@commonMain"), desktopMain.dependsOn.map { it.value })
        assertEquals(listOf("tasktide//:core:model#target/desktop"), desktopMain.targets.map { it.value })
        val targets = provided.model.targets.filter { it.module == sample.module(":core:model").id }
        assertEquals(listOf("android", "desktop"), targets.map { it.name })
    }

    @Test
    fun `project dependencies are module to module edges with their configuration`() {
        val appToModel = sample.moduleDependencies(":app").filter { it.first == ":core:model" }
        assertEquals(listOf(":core:model" to "implementation"), appToModel)
    }

    @Test
    fun `every source set that Gradle reports gets a compile classpath of entries that exist on disk`() {
        val classpaths = provided.classpaths
        val reported = provided.model.sourceSets.filter { it.evidenceClass != EvidenceClass.CONVENTION }
        val without = reported.map { it.id } - classpaths.keys
        assertTrue(without.isEmpty(), "source sets without a classpath: $without")
        classpaths.values.flatten().forEach { assertTrue(Files.exists(Path.of(it)), "missing classpath entry $it") }
    }

    @Test
    fun `an unchanged workspace is served from the model cache`() {
        provided
        val files = Files.list(index.resolve("gradle-model").resolve("models")).use { it.toList() }
        assertEquals(1, files.size)
        val again = GradleToolingProvider(index) { error("The cache should have answered") }
        assertTrue(again.provide(WorkspaceRequests.of(fixture)) is BuildModelResult.Provided)
    }
}
