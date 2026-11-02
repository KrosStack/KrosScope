package io.github.krosstack.kscope.analyzer.schema.web.map

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SourceResolverTest {
    private val resolver = SourceResolver(mapDirectory = "web/dist/assets", packageDirectory = "web")

    private fun resolve(source: String, sourceRoot: String = "", resolver: SourceResolver = this.resolver) =
        resolver.resolve(sourceRoot, source)

    @Test
    fun `a relative source is resolved against the directory of the map and normalized`() {
        assertEquals("web/src/main.ts", resolve("../../src/main.ts"))
        assertEquals("web/dist/assets/a.ts", resolve("./a.ts"))
        assertEquals("web/src/b.ts", resolve("../../src/./x/../b.ts"))
        assertEquals("web/dist/assets/a.ts", resolve("a.ts"))
    }

    @Test
    fun `the source root is put before a relative source, with a slash between`() {
        assertEquals("web/src/main.ts", resolve("main.ts", sourceRoot = "../../src"))
        assertEquals("web/src/main.ts", resolve("main.ts", sourceRoot = "../../src/"))
        assertEquals("web/dist/assets/main.ts", resolve("main.ts", sourceRoot = "./"))
    }

    @Test
    fun `a source with a scheme or an absolute path ignores the source root and has no value`() {
        assertNull(resolve("/home/dev/app/src/main.ts", sourceRoot = "src"))
        assertNull(resolve("file:///home/dev/app/src/main.ts"))
        assertNull(resolve("https://example.com/main.ts"))
        assertNull(resolve("C:/work/app/main.ts"))
        assertNull(resolve("data:application/json;base64,e30="))
        assertNull(resolve("vite://src/main.ts"))
    }

    @Test
    fun `a path that leaves the repository has no value`() {
        assertNull(resolve("../../../../../elsewhere.ts"))
        assertNull(resolve("..\\..\\src\\main.ts"))
        assertEquals("elsewhere.ts", resolve("../../../elsewhere.ts"))
    }

    @Test
    fun `a webpack source is resolved against the directory of the package, without the prefix`() {
        assertEquals("web/src/main.ts", resolve("webpack://web-app/./src/main.ts"))
        assertEquals("web/src/main.ts", resolve("webpack:///./src/main.ts"))
        assertEquals("web/src/main.ts", resolve("webpack://@tasktide/web/./src/main.ts"))
        assertEquals("web/src/main.ts", resolve("webpack://web-app/src/main.ts"))
        assertEquals("web/src/main.ts", resolve("./src/main.ts", sourceRoot = "webpack://web-app/"))
    }

    @Test
    fun `a webpack source of a package in the repository root resolves to the root`() {
        val root = SourceResolver(mapDirectory = "dist", packageDirectory = "")

        assertEquals("src/main.ts", resolve("webpack://app/./src/main.ts", resolver = root))
    }

    @Test
    fun `a webpack source needs a known package, and does not leave the repository`() {
        val unknown = SourceResolver(mapDirectory = "dist", packageDirectory = null)

        assertNull(resolve("webpack://app/./src/main.ts", resolver = unknown))
        assertEquals("dist/a.ts", resolve("a.ts", resolver = unknown))
        assertNull(resolve("webpack://app/../../x.ts"))
    }
}
