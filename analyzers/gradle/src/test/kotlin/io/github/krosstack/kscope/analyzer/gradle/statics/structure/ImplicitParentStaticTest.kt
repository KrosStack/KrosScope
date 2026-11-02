package io.github.krosstack.kscope.analyzer.gradle.statics.structure

import io.github.krosstack.kscope.analyzer.gradle.statics.StaticWorkspace
import io.github.krosstack.kscope.analyzer.gradle.statics.module
import io.github.krosstack.kscope.analyzer.gradle.statics.sourceSets
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ImplicitParentStaticTest {
    private val tree = StaticWorkspace("minimal")

    init {
        tree.write("parent/build.gradle", "plugins {\n    id 'java'\n}\n")
        tree.write("parent/src/main/java/Parent.java", "class Parent {}\n")
        tree.write("parent/child/build.gradle", "plugins {\n    id 'java'\n}\n")
    }

    @AfterTest
    fun cleanUp() {
        tree.close()
    }

    @Test
    fun `including a nested project also includes its parent project`() {
        tree.write("settings.gradle", "include ':parent:child'\n")
        val provided = tree.provided()
        assertEquals(listOf(":parent", ":parent:child"), provided.model.modules.map { it.path })
        assertEquals("parent", provided.module(":parent").directory.value)
        assertEquals(listOf("main"), provided.sourceSets(":parent").keys.toList())
    }

    @Test
    fun `a parent that is included as well is one project`() {
        tree.write("settings.gradle", "include ':parent:child'\ninclude ':parent'\n")
        assertEquals(listOf(":parent", ":parent:child"), tree.provided().model.modules.map { it.path })
    }
}
