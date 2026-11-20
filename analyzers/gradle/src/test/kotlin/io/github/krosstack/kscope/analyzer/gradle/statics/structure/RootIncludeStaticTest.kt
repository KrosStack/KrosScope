package io.github.krosstack.kscope.analyzer.gradle.statics.structure

import io.github.krosstack.kscope.analyzer.gradle.statics.StaticWorkspace
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RootIncludeStaticTest {
    private val tree = StaticWorkspace("minimal")

    @AfterTest
    fun cleanUp() {
        tree.close()
    }

    @Test
    fun `including the root explicitly models it once alongside its child projects`() {
        tree.write("settings.gradle", "include ':', ':child'\n")
        tree.write("build.gradle", "plugins { id 'org.jetbrains.kotlin.jvm' }\n")
        tree.write("child/build.gradle", "plugins { id 'java' }\n")

        val model = tree.provided().model

        assertEquals(listOf("tasktide//:", "tasktide//:child"), model.modules.map { it.id.value })
        assertEquals(listOf("tasktide//:@main", "tasktide//:child@main"), model.sourceSets.map { it.id.value })
        assertEquals(
            listOf("tasktide//:#target/jvm", "tasktide//:child#target/jvm"),
            model.targets.map { it.id.value },
        )
    }
}
