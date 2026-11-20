package io.github.krosstack.kscope.analyzer.gradle.statics.structure

import io.github.krosstack.kscope.analyzer.gradle.statics.StaticWorkspace
import io.github.krosstack.kscope.analyzer.gradle.statics.block
import io.github.krosstack.kscope.analyzer.gradle.statics.literalCall
import io.github.krosstack.kscope.analyzer.gradle.statics.module
import io.github.krosstack.kscope.analyzer.gradle.statics.writeKotlinScript
import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.spi.worker.ScriptArgument
import io.github.krosstack.kscope.spi.worker.ScriptCall
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

private fun database(name: String, vararg body: ScriptCall) = ScriptCall(
    "create",
    null,
    listOf(ScriptArgument(null, name, "\"$name\"")),
    body.toList(),
    mapOf("packageName" to "\"com.example\""),
    TextRange(1, 1, 1, 1),
)

class SqlDelightSourceDirectoriesTest {
    private val tree = StaticWorkspace("roots")

    @AfterTest
    fun cleanUp() {
        tree.close()
    }

    @Test
    fun `the source directories each database configures are attributes of the module`() {
        tree.writeKotlinScript(
            "db/build.gradle.kts",
            block(
                "sqldelight",
                block(
                    "databases",
                    database("DatabaseA", literalCall("srcDirs", "src/main/projectA/")),
                    database("DatabaseB", literalCall("srcDirs", "src/main/projectB")),
                    database("DatabaseC"),
                ),
            ),
        )

        val attributes = tree.provided().module(":db").attributes.filterKeys { it.startsWith("sqldelight.") }

        assertEquals(
            mapOf(
                "sqldelight.database.DatabaseA.package" to "com.example",
                "sqldelight.database.DatabaseA.srcDirs" to "src/main/projectA",
                "sqldelight.database.DatabaseB.package" to "com.example",
                "sqldelight.database.DatabaseB.srcDirs" to "src/main/projectB",
                "sqldelight.database.DatabaseC.package" to "com.example",
            ),
            attributes,
        )
    }

    @Test
    fun `directories are normalized, and a database with a directory that cannot be resolved is marked unread`() {
        val mixed = ScriptCall(
            "srcDirs",
            null,
            listOf(ScriptArgument(null, "src/a", "\"src/a\""), ScriptArgument(null, null, "sharedDirectory")),
            emptyList(),
            emptyMap(),
            TextRange(1, 1, 1, 1),
        )
        tree.writeKotlinScript(
            "db/build.gradle.kts",
            block(
                "sqldelight",
                block(
                    "databases",
                    database("DatabaseA", mixed),
                    database("DatabaseB", literalCall("srcDirs", "./src/b/../c/")),
                    database("DatabaseC", literalCall("srcDir", "../outside")),
                ),
            ),
        )

        val attributes = tree.provided().module(":db").attributes.filterKeys { it.contains(".srcDirs") }

        assertEquals(
            mapOf(
                "sqldelight.database.DatabaseA.srcDirsUnread" to "true",
                "sqldelight.database.DatabaseB.srcDirs" to "src/c",
                "sqldelight.database.DatabaseC.srcDirsUnread" to "true",
            ),
            attributes,
        )
    }
}
