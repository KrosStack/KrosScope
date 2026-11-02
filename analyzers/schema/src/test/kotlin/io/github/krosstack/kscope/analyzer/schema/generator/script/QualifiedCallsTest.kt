package io.github.krosstack.kscope.analyzer.schema.generator.script

import io.github.krosstack.kscope.analyzer.schema.lexing.KotlinTokenizer
import kotlin.test.Test
import kotlin.test.assertEquals

private fun scriptOf(text: String) = ScriptTokens(KotlinTokenizer(text).tokens())

private fun named(text: String, vararg names: String): List<QualifiedCall> =
    QualifiedCalls(scriptOf(text)).named(names.toSet())

private fun declared(text: String, keyword: String): List<QualifiedCall> =
    QualifiedCalls(scriptOf(text)).declaredBy(keyword)

private fun List<QualifiedCall>.described(): List<String> = map { call ->
    val types = call.typeArguments.joinToString("") { it.text }
    val arguments = call.arguments.joinToString("; ") { group -> group.joinToString(" ") { it.text } }
    "${call.receiver.orEmpty()}.${call.name.text}<$types>($arguments)"
}

class QualifiedCallsTest {
    @Test
    fun `a call with a receiver, type arguments and a block is found with its arguments as tokens`() {
        val calls = named("""tasks.register<Copy>("web", Copy::class) { from("a") }""", "register")

        assertEquals(listOf("tasks.register<Copy>(\"web\"; Copy : : class)"), calls.described())
    }

    @Test
    fun `the calls are found at any depth of the script, with and without a receiver, in the order of the script`() {
        val script = """
            subprojects {
                afterEvaluate {
                    tasks.named("a") { }
                }
            }
            project.copy { }
            named("b") { }
        """.trimIndent()

        assertEquals(
            listOf("tasks.named<>(\"a\")", "project.copy<>()", ".named<>(\"b\")"),
            named(script, "named", "copy").described(),
        )
    }

    @Test
    fun `a call without a block is not found and neither is a call with another name`() {
        val script = """
            tasks.named("a")
            tasks.register<Copy>("b")
            tasks.create("c") { }
            copy(1, 2)
        """.trimIndent()

        assertEquals(emptyList(), named(script, "named", "register", "copy"))
    }

    @Test
    fun `the receiver is the token before the dot of the name and is null when the name has no dot`() {
        val script = "a.b.c { }\nc { }\nx().c { }"

        assertEquals(listOf("b", null, ")"), named(script, "c").map { it.receiver })
    }

    @Test
    fun `nested type arguments are skipped as a whole`() {
        val calls = named("""tasks.named<Map<String, List<Copy>>>("x") { }""", "named")

        assertEquals(listOf("""tasks.named<Map<String,List<Copy>>>("x")"""), calls.described())
    }

    @Test
    fun `an unclosed list of type arguments or arguments leaves no call`() {
        assertEquals(emptyList(), named("tasks.named<Copy(\"x\") { }", "named"))
        assertEquals(emptyList(), named("tasks.named(\"x\" { }", "named"))
    }

    @Test
    fun `the block of a call is the block that follows its arguments`() {
        val text = "tasks.named(\"x\") { from(\"a\") }"
        val script = scriptOf(text)
        val call = QualifiedCalls(script).named(setOf("named")).single()

        val inside = ScriptCalls(script).within(call.block.interior)

        assertEquals(listOf("from"), inside.map { it.name.text })
    }

    @Test
    fun `a call that a keyword declares is found by the identifier after the keyword`() {
        val script = """
            task web(type: Copy) { from 'x' }
            task plain { }
            task('quoted', type: Copy) { }
            task nested(type: Sync, dependsOn: 'web') { }
        """.trimIndent()

        assertEquals(
            listOf(".web<>(type : Copy)", ".plain<>()", ".nested<>(type : Sync; dependsOn : 'web')"),
            declared(script, "task").described(),
        )
    }
}
