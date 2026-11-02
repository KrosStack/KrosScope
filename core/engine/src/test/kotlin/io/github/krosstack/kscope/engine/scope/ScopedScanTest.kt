package io.github.krosstack.kscope.engine.scope

import io.github.krosstack.kscope.engine.TestWorkspace
import io.github.krosstack.kscope.engine.artifactPaths
import io.github.krosstack.kscope.engine.assertMatchesRebuild
import io.github.krosstack.kscope.engine.canonicalDump
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScopedScanTest {
    private val repositoryId = "fixed-repository-id"
    private val appOnly = ScanScope(include = listOf("app/**"))

    private fun TestWorkspace.engineWith(scope: () -> ScanScope) = engine(scope = scope).also {
        seedRepositoryId(repositoryId)
    }

    private fun scanned(scope: ScanScope): Pair<Set<String>, String> = TestWorkspace("gradle-multi").use { workspace ->
        workspace.engineWith { scope }.use { engine ->
            engine.update()
            engine.artifactPaths() to engine.canonicalDump()
        }
    }

    @Test
    fun `a full scan a narrow scan and a full scan again end where they began`() =
        TestWorkspace("gradle-multi").use { workspace ->
            var scope = ScanScope.ALL
            workspace.engineWith { scope }.use { engine ->
                engine.update()
                val first = engine.canonicalDump()
                val allPaths = engine.artifactPaths()

                scope = appOnly
                engine.update()
                val narrowPaths = engine.artifactPaths()
                assertTrue(narrowPaths.size < allPaths.size)
                assertTrue("README.md" !in narrowPaths)
                assertTrue("core/src/commonMain/kotlin/com/example/core/Greeter.kt" !in narrowPaths)

                scope = ScanScope.ALL
                engine.update()
                assertEquals(first, engine.canonicalDump())
                assertEquals(allPaths, engine.artifactPaths())
            }
        }

    @Test
    fun `a narrow scan on top of a full index equals a clean narrow scan`() {
        val clean = scanned(appOnly)
        TestWorkspace("gradle-multi").use { workspace ->
            var scope = ScanScope.ALL
            workspace.engineWith { scope }.use { engine ->
                engine.update()
                scope = appOnly
                engine.update()
                assertEquals(clean.first, engine.artifactPaths())
                assertEquals(clean.second, engine.canonicalDump())
                engine.assertMatchesRebuild()
            }
        }
    }

    @Test
    fun `an unchanged scope reworks nothing and the order of its patterns does not matter`() =
        TestWorkspace("gradle-multi").use { workspace ->
            var scope = ScanScope(exclude = listOf("docs", "scripts"))
            workspace.engineWith { scope }.use { engine ->
                engine.update()
                scope = ScanScope(exclude = listOf("scripts", "docs", "docs"))
                val report = engine.update()
                assertEquals(0, report.unitsProduced)
                assertEquals(0, report.unitsRemoved)
            }
        }

    @Test
    fun `build files outside include stay readable unless exclude names them`() =
        TestWorkspace("gradle-multi").use { workspace ->
            workspace.engineWith { appOnly }.use { engine ->
                engine.update()
                val paths = engine.artifactPaths()
                listOf("settings.gradle.kts", "build.gradle.kts", "gradle.properties", "gradle/libs.versions.toml")
                    .forEach { assertTrue(it in paths, "$it is missing from $paths") }
                assertTrue("core/build.gradle.kts" in paths)
                assertTrue("README.md" !in paths && "docs/guide.md" !in paths)
            }
            val excludingProperties = ScanScope(include = listOf("app/**"), exclude = listOf("gradle.properties"))
            workspace.engineWith { excludingProperties }.use { engine ->
                engine.update()
                assertTrue("gradle.properties" !in engine.artifactPaths())
                assertTrue("settings.gradle.kts" in engine.artifactPaths())
            }
        }

    @Test
    fun `exclude removes a directory and everything below it`() = TestWorkspace("gradle-multi").use { workspace ->
        workspace.engineWith { ScanScope(exclude = listOf("**/src/commonTest", "docs")) }.use { engine ->
            engine.update()
            val paths = engine.artifactPaths()
            assertTrue(paths.none { it.startsWith("docs/") || "/src/commonTest/" in it }, "$paths")
            assertTrue("core/src/commonMain/kotlin/com/example/core/Greeter.kt" in paths)
        }
    }

    @Test
    fun `tests off drops the files of source sets that are not main`() =
        TestWorkspace("gradle-multi").use { workspace ->
            workspace.engineWith { ScanScope(tests = false) }.use { engine ->
                engine.update()
                val paths = engine.artifactPaths()
                assertTrue(paths.none { "/src/test/" in it || "/src/androidTest/" in it || "/src/commonTest/" in it })
                assertTrue("app/src/main/kotlin/com/example/app/Main.kt" in paths)
                engine.assertMatchesRebuild()
            }
        }
}
