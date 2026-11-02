package io.github.krosstack.kscope.service.structure

import io.github.krosstack.kscope.engine.structure.StructureScanner
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.StructureKind
import io.github.krosstack.kscope.service.StructureReport
import io.github.krosstack.kscope.service.TestGit
import io.github.krosstack.kscope.service.describeStructure
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StructureIgnoreReportTest {
    @Test
    fun `an ignored clone leaves a single build and serializes one skipped directory`() = inStructureDirectory { root ->
        assumeTrue(TestGit.available())
        TestGit.run(root, listOf("init", "-q"))
        root.writeText("settings.gradle.kts")
        root.writeText(".gitignore", "scratch/\n")
        root.writeText("scratch/clone/settings.gradle.kts")
        root.writeText("scratch/clone/package.json")
        TestGit.run(root.resolve("scratch/clone"), listOf("init", "-q"))

        val result = KrosScope.describeStructure(root)

        assertEquals(StructureKind.SINGLE_BUILD, result.conclusion.kind)
        assertEquals(listOf("."), result.gradleBuilds.map { it.directory })
        assertTrue(result.nestedRepositories.isEmpty())
        assertTrue(result.projects.isEmpty())
        val json = Json.encodeToJsonElement(result).jsonObject
        assertEquals(1, json.getValue("ignoredDirectoriesSkipped").jsonPrimitive.int)
    }

    @Test
    fun `a visible clone retains the several repositories conclusion`() = inStructureDirectory { root ->
        assumeTrue(TestGit.available())
        TestGit.run(root, listOf("init", "-q"))
        root.writeText("settings.gradle.kts")
        root.writeText("scratch/clone/settings.gradle.kts")
        TestGit.run(root.resolve("scratch/clone"), listOf("init", "-q"))

        val result = KrosScope.describeStructure(root)

        assertEquals(StructureKind.SEVERAL_REPOSITORIES, result.conclusion.kind)
        assertEquals("A directory holding 2 repositories.", result.conclusion.summary)
        assertEquals(listOf("scratch/clone"), result.nestedRepositories.map { it.directory })
        assertEquals(listOf(".", "scratch/clone"), result.gradleBuilds.map { it.directory })
    }

    @Test
    fun `two repositories outside a work tree keep the same conclusion`() = inStructureDirectory { root ->
        assumeTrue(TestGit.available())
        root.writeText("first/settings.gradle.kts")
        root.writeText("second/settings.gradle.kts")
        root.writeText(".gitignore", "second/\n")
        TestGit.run(root.resolve("first"), listOf("init", "-q"))
        TestGit.run(root.resolve("second"), listOf("init", "-q"))

        val result = KrosScope.describeStructure(root)

        assertNull(result.git)
        assertEquals("A directory holding 2 repositories.", result.conclusion.summary)
        assertEquals(listOf("first", "second"), result.nestedRepositories.map { it.directory })
    }

    @Test
    fun `the ignore fallback notice survives service JSON serialization`() = inStructureDirectory { root ->
        val notice = "Git ignore rules were not applied: Git is unavailable."
        val findings = StructureScanner(0).scan(root).copy(ignoreRulesNotice = notice)

        val result = StructureReport.of(findings, 0)

        assertEquals(notice, result.ignoreRulesNotice)
        val json = Json.encodeToJsonElement(result).jsonObject
        assertEquals(notice, json.getValue("ignoreRulesNotice").jsonPrimitive.content)
    }
}
