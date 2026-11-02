package io.github.krosstack.kscope.analyzer.gradle.statics.convention

import io.github.krosstack.kscope.analyzer.gradle.statics.StaticWorkspace
import io.github.krosstack.kscope.analyzer.gradle.statics.block
import io.github.krosstack.kscope.analyzer.gradle.statics.literalCall
import io.github.krosstack.kscope.analyzer.gradle.statics.module
import io.github.krosstack.kscope.analyzer.gradle.statics.problems
import io.github.krosstack.kscope.analyzer.gradle.statics.writeKotlinScript
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val SERIALIZATION = "org.jetbrains.kotlin.plugin.serialization"
private const val PARCELIZE = "org.jetbrains.kotlin.plugin.parcelize"

class ConventionScopeStaticTest {
    private val tree = StaticWorkspace("minimal")

    @AfterTest
    fun cleanUp() {
        tree.close()
    }

    private fun build(directory: String, conventionPlugin: String) {
        val settings = "rootProject.name = '$directory'\ninclude ':lib'\nincludeBuild 'build-logic'\n"
        tree.write("$directory/settings.gradle", settings)
        tree.writeKotlinScript(
            "$directory/build-logic/src/main/kotlin/acme.library.gradle.kts",
            block("plugins", literalCall("id", conventionPlugin)),
        )
        tree.write("$directory/lib/build.gradle", "plugins {\n    id 'acme.library'\n}\n")
    }

    @Test
    fun `independent builds that define a convention plugin of the same id each apply their own`() {
        build("a", SERIALIZATION)
        build("b", PARCELIZE)
        val provided = tree.provided()
        assertEquals(listOf("acme.library", SERIALIZATION), provided.module(":lib", "tasktide//a").plugins)
        assertEquals(listOf("acme.library", PARCELIZE), provided.module(":lib", "tasktide//b").plugins)
    }

    @Test
    fun `a build does not apply a convention plugin of a build it does not include`() {
        build("a", SERIALIZATION)
        tree.write("b/settings.gradle", "rootProject.name = 'b'\ninclude ':lib'\n")
        tree.write("b/lib/build.gradle", "plugins {\n    id 'acme.library'\n}\n")
        val provided = tree.provided()
        assertEquals(listOf("acme.library"), provided.module(":lib", "tasktide//b").plugins)
        assertTrue(provided.problems("gradle.static.plugin-not-analysed").any { "acme.library" in it.message })
    }

    @Test
    fun `a convention plugin applies the plugins of the build its own build includes`() {
        build("a", "acme.base")
        tree.write("a/build-logic/settings.gradle", "includeBuild '../base-logic'\n")
        tree.writeKotlinScript(
            "a/base-logic/src/main/kotlin/acme.base.gradle.kts",
            block("plugins", literalCall("id", SERIALIZATION)),
        )
        build("b", PARCELIZE)
        val provided = tree.provided()
        assertEquals(
            listOf("acme.base", "acme.library", SERIALIZATION),
            provided.module(":lib", "tasktide//a").plugins,
        )
        assertEquals(listOf("acme.library", PARCELIZE), provided.module(":lib", "tasktide//b").plugins)
        assertTrue(provided.problems("gradle.static.plugin-not-analysed").none { "acme.base" in it.message })
    }
}
