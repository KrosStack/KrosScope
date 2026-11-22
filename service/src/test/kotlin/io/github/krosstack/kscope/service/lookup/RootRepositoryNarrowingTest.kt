package io.github.krosstack.kscope.service.lookup

import io.github.krosstack.kscope.retrieval.MlMode
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.OpenOptions
import io.github.krosstack.kscope.service.ScanLevel
import io.github.krosstack.kscope.service.ServiceJson
import io.github.krosstack.kscope.service.TestGit
import io.github.krosstack.kscope.service.registry.RegistrationOutcome
import io.github.krosstack.kscope.service.registry.RepositoryRegistration
import io.github.krosstack.kscope.service.registry.RepositoryRegistry
import io.github.krosstack.kscope.service.trust.MachineDirectory
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RootRepositoryNarrowingTest {
    @BeforeTest
    fun requireGit() = assumeTrue(TestGit.available(), "git is not available")

    private fun <T> inRootWithKit(block: (KrosScope) -> T): T {
        val base = Files.createTempDirectory("kscope-root-narrowing")
        try {
            val root = committed(base.resolve("shop"), "lib/src/main/kotlin/tasks/TaskStore.kt", "TaskStore")
            val kit = committed(base.resolve("external-kit"), "sync/TaskSync.kt", "TaskSync")
            val environment = mapOf(MachineDirectory.VARIABLE to base.resolve("home").toString())::get
            val registry = RepositoryRegistry.ofThisMachine(property = { null }, environment = environment)
            val outcome = RepositoryRegistration(root, registry).add(kit.toString(), name = "kit")
            assertTrue(outcome is RegistrationOutcome.Registered, "$outcome")
            return KrosScope.open(root, OpenOptions(level = ScanLevel.SYNTAX), environment).use { scope ->
                scope.scan()
                block(scope)
            }
        } finally {
            base.toFile().deleteRecursively()
        }
    }

    @Test
    fun `a repository at the root is named as info lists it`() {
        inRootWithKit { scope ->
            val narrowing = assertNotNull(scope.narrowingFor("lib/src/main/kotlin"))
            val listed = scope.info().repositories.single { it.directory.isEmpty() }

            assertEquals("shop", listed.name)
            assertEquals(listed.name, narrowing.name)
            assertTrue(narrowing.atRoot)
            assertEquals("kit", assertNotNull(scope.narrowingFor("kit")).name)
        }
    }

    @Test
    fun `a task within the repository at the root ranks only its files`() {
        inRootWithKit { scope ->
            val narrowing = assertNotNull(scope.narrowingFor("lib"))
            val task = "change the task store sync"
            val everywhere = scope.analyze(task, includeContextEstimates = false, ml = MlMode.OFF)
            val inside = scope.analyze(
                task,
                includeContextEstimates = false,
                ml = MlMode.OFF,
                within = narrowing.repository,
            )

            assertTrue(everywhere.files.any { it.artifact.repository != narrowing.repository })
            assertTrue(inside.files.isNotEmpty())
            assertTrue(inside.files.all { it.artifact.repository == narrowing.repository })
        }
    }

    @Test
    fun `the json of an answer limited to the root repository names it`() {
        inRootWithKit { scope ->
            val narrowing = assertNotNull(scope.narrowingFor("lib"))
            val answer = scope.symbolWithin("TaskStore", "TaskStore", narrowing)

            val json = ServiceJson.encode(answer.result, ScanLevel.SYNTAX, "location", search = answer.origin)

            assertTrue("\"narrowedTo\": \"shop\"" in json, json)
        }
    }

    private fun committed(directory: Path, file: String, name: String): Path {
        val target = directory.resolve(file)
        Files.createDirectories(target.parent)
        Files.writeString(target, "package ${file.substringBeforeLast('/').substringAfterLast('/')}\n\nclass $name\n")
        TestGit.run(directory, listOf("init", "-q"))
        TestGit.run(directory, listOf("add", "-A"))
        TestGit.run(directory, listOf("commit", "-q", "-m", "initial"))
        return directory
    }
}
