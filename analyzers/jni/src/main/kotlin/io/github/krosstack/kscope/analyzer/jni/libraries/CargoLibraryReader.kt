package io.github.krosstack.kscope.analyzer.jni.libraries

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.TextRange
import org.tomlj.Toml
import org.tomlj.TomlArray
import org.tomlj.TomlParseResult

private val dynamicCrateTypes = listOf("cdylib", "dylib")

/** A library name as a manifest states it, and where in the manifest it stands. */
private class DeclaredName(val written: String, val path: List<String>) {
    val library: String get() = if (path.first() == "lib") written else written.replace('-', '_')
}

/**
 * The library of a `Cargo.toml`, when its `crate-type` asks for a dynamic one: the `name` of the `[lib]` table, or the
 * name of the package with `-` turned into `_` when the table has none. A `cdylib` is firm; a `dylib` is heuristic,
 * because it is a Rust library and not the C library that `System.loadLibrary` expects. A manifest that does not parse
 * has no library.
 */
internal class CargoLibraryReader : LibraryTargetReader {
    override fun read(text: String): List<LibraryTarget> {
        val toml = Toml.parse(text)
        return if (toml.hasErrors()) emptyList() else listOfNotNull(targetOf(toml, text))
    }

    private fun targetOf(toml: TomlParseResult, text: String): LibraryTarget? {
        val crateType = dynamicCrateType(toml)
        val name = declaredName(toml)
        return if (crateType == null || name == null) null else libraryOf(name, crateType, toml, text)
    }

    private fun dynamicCrateType(toml: TomlParseResult): String? {
        val array = toml.get("lib.crate-type") as? TomlArray ?: toml.get("lib.crate_type") as? TomlArray
        return dynamicCrateTypes.firstOrNull { it in array?.toList().orEmpty() }
    }

    private fun declaredName(toml: TomlParseResult): DeclaredName? =
        (toml.get("lib.name") as? String)?.let { DeclaredName(it, listOf("lib", "name")) }
            ?: (toml.get("package.name") as? String)?.let { DeclaredName(it, listOf("package", "name")) }

    private fun libraryOf(name: DeclaredName, crateType: String, toml: TomlParseResult, text: String): LibraryTarget {
        val range = rangeOf(name, toml, text)
        return LibraryTarget(
            name = name.library,
            kind = LibraryKinds.CARGO,
            linkage = crateType,
            certainty = if (crateType == "cdylib") Certainty.EXACT else Certainty.HEURISTIC,
            signature = "crate-type = [\"$crateType\"], name = \"${name.library}\"",
            nameRange = range,
            declaration = range,
        )
    }

    private fun rangeOf(name: DeclaredName, toml: TomlParseResult, text: String): TextRange {
        val position = toml.inputPositionOf(name.path)
        val line = position?.line() ?: 1
        val lineText = text.lines().getOrElse(line - 1) { "" }
        val column = lineText.indexOf(name.written).takeIf { it >= 0 }?.plus(1) ?: position?.column() ?: 1
        return TextRange(line, column, line, column + name.written.length)
    }
}
