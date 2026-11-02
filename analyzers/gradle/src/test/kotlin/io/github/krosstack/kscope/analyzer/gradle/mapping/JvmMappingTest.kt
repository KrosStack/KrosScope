package io.github.krosstack.kscope.analyzer.gradle.mapping

import io.github.krosstack.kscope.model.ModuleTrait
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Platform
import io.github.krosstack.kscope.model.SourceSetRole
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JvmMappingTest {
    private val kotlinJvm = SampleModel.load("kotlin-jvm.json")
    private val javaLibrary = SampleModel.load("java-library.json", Path.of("/work/javalib"), "javalib")
    private val withIncludedBuild = SampleModel.load("included-build.json")

    @Test
    fun `a kotlin jvm module has the jvm target whatever the kotlin plugin calls its target`() {
        val engine = kotlinJvm.module(":sync:engine")
        assertEquals(setOf(ModuleTrait.KOTLIN_JVM, ModuleTrait.JAVA), engine.traits)
        val targets = kotlinJvm.model.targets.filter { it.module == engine.id }
        assertEquals(listOf("jvm" to Platform.JVM), targets.map { it.name to it.platform })
        val main = kotlinJvm.sourceSets(":sync:engine").getValue("main")
        assertEquals(listOf("tasktide//:sync:engine#target/jvm"), main.targets.map { it.value })
    }

    @Test
    fun `main and test source sets carry their roles and classpaths`() {
        val sourceSets = kotlinJvm.sourceSets(":sync:engine")
        assertEquals(listOf("main", "test"), sourceSets.keys.toList())
        assertEquals(SourceSetRole.MAIN, sourceSets.getValue("main").role)
        assertEquals(SourceSetRole.UNIT_TEST, sourceSets.getValue("test").role)
        assertTrue(kotlinJvm.classpath(":sync:engine@main").isNotEmpty())
        assertTrue(kotlinJvm.classpath(":sync:engine@test").size >= kotlinJvm.classpath(":sync:engine@main").size)
    }

    @Test
    fun `java and kotlin directories of one source set are merged into its roots`() {
        val roots = kotlinJvm.sourceSets(":core:legacy").getValue("main").roots
        assertEquals(
            listOf("core/legacy/src/main/java" to "java", "core/legacy/src/main/kotlin" to "kotlin"),
            roots.map { it.directory.value to it.kind.value },
        )
    }

    @Test
    fun `project dependencies of a groovy build are edges between modules`() {
        assertEquals(listOf(":core:model" to "api"), kotlinJvm.moduleDependencies(":core:legacy"))
    }

    @Test
    fun `the wire plugin is recorded as an attribute`() {
        assertEquals("true", kotlinJvm.module(":sync:protocol").attributes["wire.kotlin"])
    }

    @Test
    fun `a java library has the java trait and the jvm target`() {
        val lib = javaLibrary.model.modules.single { it.path == ":lib" }
        assertEquals(setOf(ModuleTrait.JAVA), lib.traits)
        val targets = javaLibrary.model.targets.filter { it.module == lib.id }
        assertEquals(listOf("jvm" to Platform.JVM), targets.map { it.name to it.platform })
    }

    @Test
    fun `a java library has a source set for each java source set`() {
        val lib = javaLibrary.model.modules.single { it.path == ":lib" }
        val sourceSets = javaLibrary.model.sourceSets.filter { it.module == lib.id }.associateBy { it.name }
        assertEquals(listOf("main", "test", "testFixtures"), sourceSets.keys.toList())
        assertEquals(SourceSetRole.TEST_FIXTURES, sourceSets.getValue("testFixtures").role)
        assertEquals(
            listOf("lib/src/main/java" to "java", "lib/src/main/resources" to "resources"),
            sourceSets.getValue("main").roots.map { it.directory.value to it.kind.value },
        )
    }

    @Test
    fun `a dependency on a project that is not a module is dropped`() {
        val lib = javaLibrary.model.modules.single { it.path == ":lib" }
        val configurations = setOf("api", "implementation")
        val edges = javaLibrary.model.dependencies.filter { it.attributes["configuration"] in configurations }
        assertEquals(configurations, edges.map { it.attributes.getValue("configuration") }.toSet())
        assertEquals(listOf(lib.id), edges.map { (it.from as NodeRef.OfModule).id }.distinct())
        assertEquals(2, edges.size)
    }

    @Test
    fun `an included build is another build root whose modules are build logic`() {
        val roots = withIncludedBuild.model.buildRoots
        assertEquals(listOf("tasktide//", "tasktide//build-logic"), roots.map { it.id.value })
        val buildLogic = withIncludedBuild.model.modules.single { it.buildRoot.value == "tasktide//build-logic" }
        assertEquals("tasktide//build-logic:", buildLogic.id.value)
        assertEquals(":", buildLogic.path)
        assertEquals("build-logic", buildLogic.directory.value)
        assertTrue(ModuleTrait.BUILD_LOGIC in buildLogic.traits)
        assertTrue("org.gradle.kotlin.kotlin-dsl" in buildLogic.plugins)
        assertTrue(ModuleTrait.BUILD_LOGIC !in withIncludedBuild.module(":sync:engine").traits)
    }
}
