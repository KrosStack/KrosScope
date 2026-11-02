package io.github.krosstack.kscope.analyzer.gradle.statics

import io.github.krosstack.kscope.analyzer.gradle.statics.plugin.GradleProperties
import kotlin.test.Test
import kotlin.test.assertEquals

class GradlePropertiesTest {
    @Test
    fun `a line without a separator is a key with an empty value and never fails`() {
        val text = "bare\ngroup=org.example\nversion 1.0\n# comment\n"

        assertEquals(mapOf("bare" to "", "group" to "org.example", "version" to "1.0"), GradleProperties.parse(text))
    }

    @Test
    fun `a malformed escape gives no properties instead of failing`() {
        assertEquals(emptyMap(), GradleProperties.parse("""group=\uZZZZ"""))
    }
}
