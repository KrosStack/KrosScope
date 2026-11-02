package io.github.krosstack.kscope.engine.build.archive

import io.github.krosstack.kscope.engine.build.ModuleSpec
import io.github.krosstack.kscope.engine.build.WorkspaceModelDraft
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.SourceSetRole
import java.nio.file.Files
import java.nio.file.Path

/**
 * An application with the module `:client` and a library with the module `:text` that builds `text-1.0.0.jar`, as
 * two repositories below a temporary directory that stands for the checkouts.
 */
internal class JvmLibraryWorkspace : AutoCloseable {
    val base: Path = Files.createTempDirectory("kscope-jvm-libraries")
    val draft = WorkspaceModelDraft()
    val client: ModuleId
    val text: ModuleId
    val mavenLocal: Path = base.resolve("m2")

    init {
        draft.repository("application")
        draft.repository("library")
        client = draft.module("application", ":client")
        text = draft.module("library", ":text", ModuleSpec(archives = "text-1.0.0.jar"))
    }

    fun textSources(vararg files: String) =
        draft.sources(text, SourceSetRole.MAIN, "text/src/main/kotlin", files.toList())

    fun consumerJar(vararg entries: String): Path =
        JarFixtures.write(base.resolve("application/client/libs/text-1.0.0.jar"), *entries)

    fun builtJar(vararg entries: String): Path =
        JarFixtures.write(base.resolve("library/text/build/libs/text-1.0.0.jar"), *entries)

    fun links(vararg folders: Path): JvmLibraryLinks = draft.resolveJvmLibraries(base, folders.toList())

    /** The client depends on `org.example:text:1.0.0`, which the sources of `:text` in `org/example/text` build. */
    fun dependOnPublishedText() {
        draft.dependsOn(client, "org.example:text", "1.0.0")
        textSources("org/example/text/Slugs.kt")
    }

    fun published(folder: Path, vararg entries: String): Path =
        JarFixtures.write(folder.resolve("org/example/text/1.0.0/text-1.0.0.jar"), *entries)

    /** The module `:ui` of the library builds `ui-2.0.aar` from `org/example/ui/Panel.kt`; the client depends on it. */
    fun uiModule(): ModuleId {
        val ui = draft.module("library", ":ui", ModuleSpec(archives = "ui-2.0.aar"))
        draft.sources(ui, SourceSetRole.MAIN, "ui/src/main/kotlin", listOf("org/example/ui/Panel.kt"))
        draft.dependsOn(client, "org.example:ui", "2.0")
        return ui
    }

    override fun close() {
        base.toFile().deleteRecursively()
    }
}
