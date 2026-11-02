package io.github.krosstack.kscope.engine.build.archive

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TreeFilterTest {
    private fun filter(include: String? = null, exclude: String? = null): TreeFilter {
        val attributes = mapOf("tree.include" to include, "tree.exclude" to exclude)
        return TreeFilter.of(attributes.mapNotNull { (key, value) -> value?.let { key to it } }.toMap())
    }

    @Test
    fun `a tree without patterns keeps every file`() {
        assertTrue(filter().keeps("a.jar"))
        assertTrue(filter().keeps("nested/deeper/a.jar"))
    }

    @Test
    fun `star and question mark stay within one name`() {
        val filter = filter(include = "lib-?.jar\nx*.jar")
        assertTrue(filter.keeps("lib-1.jar"))
        assertTrue(filter.keeps("xyz.jar"))
        assertFalse(filter.keeps("lib-10.jar"))
        assertFalse(filter.keeps("nested/xyz.jar"))
    }

    @Test
    fun `a double star spans any number of directories, none included`() {
        val filter = filter(include = "**/*.jar")
        assertTrue(filter.keeps("a.jar"))
        assertTrue(filter.keeps("one/two/a.jar"))
        assertFalse(filter.keeps("one/a.zip"))
    }

    @Test
    fun `a file is kept when some include matches and no exclude does`() {
        val filter = filter(include = "*.jar\nnested/*.jar", exclude = "*-sources.jar")
        assertTrue(filter.keeps("a.jar"))
        assertTrue(filter.keeps("nested/a.jar"))
        assertFalse(filter.keeps("a-sources.jar"))
        assertFalse(filter.keeps("other/a.jar"))
    }

    @Test
    fun `an excluded directory hides what lies below it and a trailing slash means everything below`() {
        assertFalse(filter(exclude = "old").keeps("old/a.jar"))
        assertFalse(filter(exclude = "old/").keeps("old/deeper/a.jar"))
        assertTrue(filter(exclude = "old").keeps("older/a.jar"))
    }

    @Test
    fun `patterns are case sensitive`() {
        assertEquals(false, filter(include = "*.JAR").keeps("a.jar"))
    }
}
