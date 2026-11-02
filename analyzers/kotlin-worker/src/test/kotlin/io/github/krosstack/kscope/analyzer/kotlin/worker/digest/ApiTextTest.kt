package io.github.krosstack.kscope.analyzer.kotlin.worker.digest

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import io.github.krosstack.kscope.analyzer.kotlin.worker.aa.ScriptParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class ApiTextTest {
    @Test
    fun `removable bodies comments and whitespace do not change API text`() {
        ScriptParser().use { parser ->
            SameApiSamples.mutations().forEach { mutation ->
                assertEquals(text(parser, mutation.before), text(parser, mutation.after), mutation.name)
            }
        }
    }

    @Test
    fun `visible syntax and retained bodies change API text`() {
        ScriptParser().use { parser ->
            DifferentApiSamples.mutations().forEach { mutation ->
                assertNotEquals(text(parser, mutation.before), text(parser, mutation.after), mutation.name)
            }
        }
    }

    @Test
    fun `a first call is a contract only by that name or by an alias the file imports`() {
        ScriptParser().use { parser ->
            assertEquals(text(parser, "fun f() { c { a() }; a() }"), text(parser, "fun f() { c { a() }; b() }"))
            assertEquals(
                text(parser, "import x.other as c\nfun f() { c { a() }; a() }"),
                text(parser, "import x.other as c\nfun f() { c { a() }; b() }"),
            )
        }
    }

    @Test
    fun `leaf tokens and removable bodies each end in one newline`() {
        ScriptParser().use { parser ->
            assertEquals("\n\nfun\nf\n(\n)\n{}\n", text(parser, "fun f() { println(1) }"))
        }
    }

    private fun text(parser: ScriptParser, source: String): String {
        val file = parser.parse("Api.kt", source)
        assertFalse(PsiTreeUtil.hasErrorElements(file), source)
        assertEquals(emptyList(), PsiTreeUtil.collectElementsOfType(file, PsiErrorElement::class.java).toList())
        return ApiText().of(file)
    }
}
