package io.github.krosstack.kscope.analyzer.schema.web.map

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SourceMapReaderTest {
    @Test
    fun `a map gives its file, its source root and its sources in order`() {
        val text = """{"version":3,"file":"index.js","sourceRoot":"src/","sources":["../a.ts","b.ts"],"mappings":""}"""
        val map = checkNotNull(SourceMapReader.read(text))

        assertEquals("index.js", map.file)
        assertEquals("src/", map.sourceRoot)
        assertEquals(listOf("../a.ts", "b.ts"), map.sources)
    }

    @Test
    fun `a map without a file or a source root has none`() {
        val map = checkNotNull(SourceMapReader.read("""{"version":3,"sources":[]}"""))

        assertNull(map.file)
        assertEquals("", map.sourceRoot)
        assertEquals(emptyList(), map.sources)
    }

    @Test
    fun `a byte order mark and a security prefix before the JSON are skipped`() {
        val body = """{"version":3,"sources":["a.ts"]}"""

        assertEquals(listOf("a.ts"), SourceMapReader.read(Char(0xFEFF) + body)?.sources)
        assertEquals(listOf("a.ts"), SourceMapReader.read(")]}'\n$body")?.sources)
    }

    @Test
    fun `escapes in a source are decoded and the content of the sources is ignored`() {
        val text = """{"version":3,"sources":["a\"b.ts","c\\d.ts"],"sourcesContent":["let x = '{'; // \"]"]}"""

        assertEquals(listOf("a\"b.ts", "c\\d.ts"), SourceMapReader.read(text)?.sources)
    }

    @Test
    fun `text that is no source map has no value`() {
        assertNull(SourceMapReader.read(""))
        assertNull(SourceMapReader.read("not json"))
        assertNull(SourceMapReader.read("[1,2]"))
        assertNull(SourceMapReader.read("""{"version":3}"""))
        assertNull(SourceMapReader.read("""{"version":3,"sources":"a.ts"}"""))
        assertNull(SourceMapReader.read("""{"version":3,"sections":[{"offset":{"line":0,"column":0},"map":{}}]}"""))
    }

    @Test
    fun `a map cut short still gives the sources that were read`() {
        assertEquals(listOf("a.ts", "b.t"), SourceMapReader.read("""{"version":3,"sources":["a.ts","b.t""")?.sources)
    }
}
