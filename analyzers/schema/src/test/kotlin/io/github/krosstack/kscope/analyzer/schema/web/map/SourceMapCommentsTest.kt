package io.github.krosstack.kscope.analyzer.schema.web.map

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SourceMapCommentsTest {
    @Test
    fun `a comment with a data URL is an inline map`() {
        assertTrue(SourceMapComments.isInline("a();\n//# sourceMappingURL=data:application/json;base64,eyJ9\n"))
        assertTrue(SourceMapComments.isInline("a();\n/*# sourceMappingURL=data:application/json,{} */"))
    }

    @Test
    fun `a comment with a file name is no inline map`() {
        assertFalse(SourceMapComments.isInline("a();\n//# sourceMappingURL=index.js.map\n"))
        assertFalse(SourceMapComments.isInline("a();\n"))
    }

    @Test
    fun `the last comment decides`() {
        val inlineThenFile = "//# sourceMappingURL=data:application/json,{}\nb();\n//# sourceMappingURL=a.js.map\n"
        val fileThenInline = "//# sourceMappingURL=a.js.map\nb();\n//# sourceMappingURL=data:application/json,{}\n"

        assertFalse(SourceMapComments.isInline(inlineThenFile))
        assertTrue(SourceMapComments.isInline(fileThenInline))
    }
}
