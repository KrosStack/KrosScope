package io.github.krosstack.kscope.analyzer.gradle.statics.structure

import io.github.krosstack.kscope.analyzer.gradle.GradleStaticProvider
import io.github.krosstack.kscope.analyzer.gradle.WorkspaceRequests
import io.github.krosstack.kscope.analyzer.gradle.statics.CheckedInOutlines
import io.github.krosstack.kscope.spi.BuildModelResult
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SharedCatalogStaticTest {
    @TempDir
    lateinit var root: Path

    @BeforeTest
    fun createBuilds() {
        write("settings.gradle", "includeBuild('build-logic')")
        write("build.gradle", "dependencies { implementation(libs.check) }")
        write("gradle/libs.versions.toml", "[libraries]\ncheck = 'org.example:check:1.0'")
        write("shared/gradle/libs.versions.toml", "[libraries]\ncheck = 'org.example:other:2.0'")
        write(
            "build-logic/settings.gradle",
            """
            dependencyResolutionManagement {
                versionCatalogs {
                    create('libs') { from(files('../gradle/libs.versions.toml')) }
                }
            }
            """.trimIndent(),
        )
        write("build-logic/build.gradle", "dependencies { implementation(libs.check) }")
    }

    @Test
    fun `a shared catalog emits each declaration once without colliding with another file`() {
        val facts = facts()
        assertEquals(2, facts.symbols.size)
        assertEquals(2, facts.symbols.map { it.id }.distinct().size)
        assertEquals(1, facts.symbols.count { it.declaration?.artifact?.path?.value == "gradle/libs.versions.toml" })
    }

    @Test
    fun `root and included build accessors resolve to the same unambiguous declaration`() {
        val facts = facts()
        val rootTarget = facts.targetsOf("build.gradle").single().first
        val includedTarget = facts.targetsOf("build-logic/build.gradle").single().first
        assertEquals(rootTarget, includedTarget)
        assertEquals(
            listOf("gradle/libs.versions.toml"),
            facts.symbols.filter { it.id == rootTarget }.map { it.declaration?.artifact?.path?.value },
        )
    }

    @Test
    fun `two builds importing different files under the same name resolve distinct declarations`() {
        write(
            "build-logic/settings.gradle",
            """
            dependencyResolutionManagement {
                versionCatalogs {
                    create('libs') { from(files('../shared/gradle/libs.versions.toml')) }
                }
            }
            """.trimIndent(),
        )
        val facts = facts()
        val targets = listOf("build.gradle", "build-logic/build.gradle").map { facts.targetsOf(it).single().first }
        assertEquals(2, targets.distinct().size)
        val paths = targets.map { target ->
            facts.symbols.single { it.id == target }.declaration?.artifact?.path?.value
        }
        assertEquals(listOf("gradle/libs.versions.toml", "shared/gradle/libs.versions.toml"), paths)
    }

    @Test
    fun `importing the same file under a different name preserves its declaration`() {
        write(
            "build-logic/settings.gradle",
            """
            dependencyResolutionManagement {
                versionCatalogs {
                    create('tools') { from(files('../gradle/libs.versions.toml')) }
                }
            }
            """.trimIndent(),
        )
        write("build-logic/build.gradle", "dependencies { implementation(tools.check) }")
        val facts = facts()
        val declaration = facts.symbols.single { it.declaration?.artifact?.path?.value == "gradle/libs.versions.toml" }
        assertEquals(listOf(declaration.id), facts.targetsOf("build.gradle").map { it.first })
        assertEquals(listOf(declaration.id), facts.targetsOf("build-logic/build.gradle").map { it.first })
    }

    private fun facts(): CatalogFacts = CatalogFacts(
        assertIs<BuildModelResult.Provided>(
            GradleStaticProvider(CheckedInOutlines).provide(WorkspaceRequests.of(root, trusted = false)),
        ),
    )

    private fun write(path: String, text: String) {
        val file = root.resolve(path)
        Files.createDirectories(file.parent)
        Files.writeString(file, text)
    }
}
