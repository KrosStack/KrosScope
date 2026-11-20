package io.github.krosstack.kscope.service.semantic

import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.OpenOptions
import java.nio.file.Files
import java.nio.file.Path

internal object SemanticTestWorkspace {
    const val LIB = "lib/src/main/kotlin/Lib.kt"
    const val OTHER = "lib/src/main/kotlin/Other.kt"

    fun create(root: Path) {
        write(root, "settings.gradle.kts", "include(\":lib\")\n")
        write(root, "lib/build.gradle.kts", "")
        write(root, LIB, "class Lib {\n    fun first() = 1\n    fun second() = 2\n}\n")
        write(root, OTHER, "class Other {\n    fun third() = 3\n}\n")
    }

    fun write(root: Path, relative: String, text: String) {
        val file = root.resolve(relative)
        Files.createDirectories(file.parent)
        Files.writeString(file, text)
    }

    fun open(root: Path, components: SemanticComponents, mode: SemanticIndexingMode): KrosScope =
        KrosScope.open(root, OpenOptions(semanticIndexing = mode), { null }, semanticComponents = components)
}
