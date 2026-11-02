package io.github.krosstack.kscope.analyzer.schema.web.copy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal fun described(script: String): List<String> = CopyScriptReader(script).sources().map {
    "${it.task} ${it.path.anchor.name} ${it.path.relative}" + it.into?.let { into -> " -> $into" }.orEmpty()
}

class CopyBlockFormsTest {
    @Test
    fun `the processResources of a Kotlin build script is a copy block, with and without the tasks receiver`() {
        val script = """
            tasks.processResources {
                from("${'$'}rootDir/../web/dist") {
                    into("static")
                }
            }
            processResources {
                from("assets")
            }
            project.tasks.processResources {
                from("${'$'}projectDir/more")
            }
        """.trimIndent()

        assertEquals(
            listOf(
                "processResources BUILD_ROOT ../web/dist -> static",
                "processResources PLAIN assets",
                "processResources MODULE more",
            ),
            described(script),
        )
    }

    @Test
    fun `tasks named in a block are copy blocks whatever their type argument`() {
        val script = """
            tasks.named("processResources") { from("a") }
            tasks.named<ProcessResources>("processResources") { from("b") }
            tasks.named<Copy>("webCopy") { from("c") }
            tasks.named("jar") { from("d") }
            project.tasks.named("e") { from("e") }
        """.trimIndent()

        assertEquals(
            listOf(
                "processResources PLAIN a",
                "processResources PLAIN b",
                "webCopy PLAIN c",
                "jar PLAIN d",
                "e PLAIN e",
            ),
            described(script),
        )
    }

    @Test
    fun `a registered task is a copy block when it is a Copy or a Sync, by a type argument or a second argument`() {
        val script = """
            tasks.register<Copy>("typed") { from("a") }
            tasks.register<Sync>("synced") { from("b") }
            tasks.register("classed", Copy::class) { from("c") }
            tasks.register("javaClassed", Sync::class.java) { from("d") }
            tasks.register<Jar>("jarred") { from("e") }
            tasks.register("untyped") { from("f") }
            tasks.register("other", Delete::class) { from("g") }
        """.trimIndent()

        assertEquals(
            listOf("typed PLAIN a", "synced PLAIN b", "classed PLAIN c", "javaClassed PLAIN d"),
            described(script),
        )
    }

    @Test
    fun `the copy and sync calls of the project are copy blocks and the calls on other receivers are not`() {
        val script = """
            tasks.register("x") {
                doLast {
                    copy { from("a") }
                    project.copy { from("b") }
                    sync { from("c") }
                    fileOperations.copy { from("d") }
                    something.copy { from("e") }
                }
            }
        """.trimIndent()

        assertEquals(listOf("copy PLAIN a", "copy PLAIN b", "sync PLAIN c"), described(script))
    }

    @Test
    fun `a Groovy build script gives its copy blocks in the forms of Groovy`() {
        val script = """
            tasks.register('registered', Copy) {
                from "${'$'}rootDir/../web/dist"
                into 'static'
            }
            task declared(type: Copy) {
                from("${'$'}projectDir/assets")
            }
            task jarred(type: Jar) { from 'x' }
            task plain { from 'y' }
            processResources {
                from('resources') { into 'res' }
            }
        """.trimIndent()

        assertEquals(
            listOf(
                "registered BUILD_ROOT ../web/dist -> static",
                "declared MODULE assets",
                "processResources PLAIN resources -> res",
            ),
            described(script),
        )
    }

    @Test
    fun `a copy block inside another block is found, and its from calls are not those of the block around it`() {
        val script = """
            tasks.register<Copy>("outer") {
                from("a")
                doLast {
                    copy { from("b") }
                }
            }
            subprojects {
                afterEvaluate {
                    tasks.named("inner") { from("c") }
                }
            }
        """.trimIndent()

        assertEquals(listOf("outer PLAIN a", "copy PLAIN b", "inner PLAIN c"), described(script))
    }

    @Test
    fun `a task whose name is not a literal is not a copy block`() {
        val script = """
            tasks.named(taskName) { from("a") }
            tasks.register<Copy>("web" + suffix) { from("b") }
            tasks.register<Copy>("${'$'}prefix-web") { from("c") }
        """.trimIndent()

        assertEquals(emptyList(), described(script))
    }

    @Test
    fun `a block that is no copy block has no sources`() {
        val script = """
            publishing { publications { from(components["java"]) } }
            tasks.withType<Test> { from("a") }
            from("b")
        """.trimIndent()

        assertEquals(emptyList(), described(script))
    }

    @Test
    fun `code in comments and in strings is no copy block`() {
        val script = """
            // tasks.processResources { from("a") }
            /* processResources { from("b") } */
            val text = "tasks.processResources { from(\"c\") }"
            val raw = ${"\"\"\""}processResources { from("d") }${"\"\"\""}
        """.trimIndent()

        assertEquals(emptyList(), described(script))
    }

    @Test
    fun `only a script that mentions a from and a block can have sources`() {
        assertTrue(CopyScriptReader.mayHaveSources("processResources { from(\"a\") }"))
        assertTrue(CopyScriptReader.mayHaveSources("tasks.named(\"jar\") { from(\"a\") }"))
        assertFalse(CopyScriptReader.mayHaveSources("plugins { id(\"java\") }\nprocessResources { }"))
        assertFalse(CopyScriptReader.mayHaveSources("plugins { id(\"java\") }\nfrom(x)"))
    }
}
