package io.github.krosstack.kscope.cli.status

import com.github.ajalt.clikt.testing.CliktCommandTestResult
import io.github.krosstack.kscope.cli.runKscope
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import kotlin.io.path.isRegularFile
import kotlin.io.path.relativeTo
import kotlin.streams.asSequence

private val sideFiles = listOf("-shm", "-wal")

internal fun masked(text: String): String = text.replace(Regex("scanned \\d+[smhd] ago"), "scanned <age> ago")

internal fun runStatus(root: Path, vararg more: String): CliktCommandTestResult =
    runKscope("status", "--workspace", root.toString(), *more)

internal fun levelIn(json: JsonObject): String = json.getValue("indexLevel").jsonPrimitive.content

internal fun markIndexExact(root: Path) {
    DriverManager.getConnection("jdbc:sqlite:${root.resolve(".kscope/index.db")}").use { connection ->
        connection.createStatement().use {
            it.executeUpdate(
                "UPDATE producer SET name = 'kscope.build.gradle.tooling' " +
                    "WHERE name = 'kscope.build.gradle.static'",
            )
        }
    }
}

internal fun setUserVersion(index: Path, version: Int) {
    DriverManager.getConnection("jdbc:sqlite:$index").use { connection ->
        connection.createStatement().use { it.execute("PRAGMA user_version = $version") }
    }
}

private fun isSideFile(file: Path): Boolean = sideFiles.any { file.fileName.toString().endsWith(it) }

internal fun snapshot(root: Path): Map<String, Int> = Files.walk(root).use { paths ->
    paths.asSequence().filter { it.isRegularFile() && !isSideFile(it) }
        .associate { it.relativeTo(root).toString() to Files.readAllBytes(it).contentHashCode() }
}
