package io.github.krosstack.kscope.service.lookup

import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.OpenOptions
import io.github.krosstack.kscope.service.ScanLevel
import io.github.krosstack.kscope.service.TestGit
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager

internal object IndexedFolders {
    fun scanned(folder: Path): Path {
        Files.createDirectories(folder)
        Files.writeString(folder.resolve("Notes.md"), "# Notes\n")
        KrosScope.open(folder, OpenOptions(level = ScanLevel.SYNTAX)).use { it.scan() }
        return folder
    }

    fun withoutFiles(folder: Path): Path {
        Files.createDirectories(folder)
        KrosScope.open(folder, OpenOptions(level = ScanLevel.SYNTAX)).use { it.scan() }
        return folder
    }

    fun ofAnotherVersion(folder: Path): Path {
        scanned(folder)
        DriverManager.getConnection("jdbc:sqlite:${folder.resolve(".kscope/index.db")}").use { connection ->
            connection.createStatement().use { it.execute("PRAGMA user_version = 32767") }
        }
        return folder
    }

    fun repository(root: Path, name: String) {
        val directory = Files.createDirectories(root.resolve(name).resolve("docs"))
        Files.writeString(directory.resolve("$name.md"), "# $name\n")
        TestGit.run(root.resolve(name), listOf("init", "-q"))
        TestGit.run(root.resolve(name), listOf("add", "-A"))
        TestGit.run(root.resolve(name), listOf("commit", "-q", "-m", "initial"))
    }
}
