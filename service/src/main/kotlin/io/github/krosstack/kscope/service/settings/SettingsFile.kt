package io.github.krosstack.kscope.service.settings

import io.github.krosstack.kscope.engine.WorkspaceConfig
import io.github.krosstack.kscope.service.ScanLevel
import org.tomlj.Toml
import org.tomlj.TomlArray
import org.tomlj.TomlParseResult
import org.tomlj.TomlVersion
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

/**
 * Reads `kscope.toml` in the root of a workspace as data. The file is read only when it is a regular file, not a
 * link, of at most [MAXIMUM_BYTES]; nothing in it is executed, expanded or followed.
 */
internal class SettingsFile(root: Path) {
    private val path = WorkspaceConfig.settingsFile(root)

    fun read(): SettingsReading = try {
        load()
    } catch (_: NoSuchFileException) {
        SettingsReading.Absent
    } catch (failure: IOException) {
        refused("cannot be read (${failure.javaClass.simpleName})")
    }

    fun freshness(): FreshnessReading = when (val reading = read()) {
        SettingsReading.Absent -> FreshnessReading()
        is SettingsReading.Loaded -> FreshnessReading(reading.settings.freshness)
        is SettingsReading.Invalid -> FreshnessReading(issues = reading.issues)
    }

    private fun load(): SettingsReading {
        val attributes = Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        return when {
            attributes.isSymbolicLink -> refused("must not be a symbolic link")
            !attributes.isRegularFile -> refused("must be a regular file")
            attributes.size() > MAXIMUM_BYTES -> refused(TOO_LARGE)
            else -> parse(boundedBytes())
        }
    }

    private fun boundedBytes(): ByteArray =
        Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS).use { it.readNBytes(MAXIMUM_BYTES + 1) }

    private fun parse(bytes: ByteArray): SettingsReading {
        val text = if (bytes.size > MAXIMUM_BYTES) null else decode(bytes)
        return when {
            bytes.size > MAXIMUM_BYTES -> refused(TOO_LARGE)
            text == null -> refused("must be UTF-8 text")
            else -> interpret(Toml.parse(text.removePrefix(BYTE_ORDER_MARK), TomlVersion.V1_0_0))
        }
    }

    private fun interpret(toml: TomlParseResult): SettingsReading {
        val issues = SettingsSyntax.issuesOf(toml).ifEmpty { SettingsValidation(toml).issues() }
        return if (issues.isEmpty()) {
            SettingsReading.Loaded(settingsOf(toml))
        } else {
            SettingsReading.Invalid(issues.take(MAXIMUM_ISSUES))
        }
    }

    private fun settingsOf(toml: TomlParseResult) = WorkspaceSettings(
        resources = ResourceSettings(
            workers = toml.getLong(SettingsGrammar.WORKERS)?.toInt(),
            workerHeapMib = toml.getLong(SettingsGrammar.WORKER_HEAP_MIB)?.toInt(),
        ),
        scan = ScanSettings(
            include = patternsOf(toml, SettingsGrammar.INCLUDE),
            exclude = patternsOf(toml, SettingsGrammar.EXCLUDE),
            tests = toml.getBoolean(SettingsGrammar.TESTS) ?: true,
            generated = patternsOf(toml, SettingsGrammar.GENERATED),
        ),
        analyzers = AnalyzerSettings(
            AnalyzerKind.entries.filter { toml.getBoolean(SettingsGrammar.ANALYZERS + it.key) == false }.toSet(),
        ),
        freshness = FreshnessSettings(
            watch = toml.getBoolean(SettingsGrammar.WATCH) ?: true,
            quietSeconds = toml.getLong(SettingsGrammar.QUIET_SECONDS)?.toInt() ?: FreshnessSettings.DEFAULT_QUIET,
        ),
        level = toml.getString(SettingsGrammar.LEVEL)?.let(ScanLevel::parse),
        components = toml.getTable("components")?.keySet()?.sorted()?.associateWith {
            toml.getBoolean(listOf("components", it)) == true
        }.orEmpty(),
    )

    private fun patternsOf(toml: TomlParseResult, path: List<String>): List<String> {
        val array = toml.get(path) as? TomlArray ?: return emptyList()
        return (0 until array.size()).map { array.get(it) as String }
    }

    private fun decode(bytes: ByteArray): String? = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        null
    }

    private fun refused(message: String) = SettingsReading.Invalid(listOf(SettingsIssue(null, null, message)))

    companion object {
        const val MAXIMUM_BYTES: Int = 64 * 1024
        const val MAXIMUM_ISSUES: Int = 10

        private const val TOO_LARGE = "must be at most 64 KiB"
        private const val BYTE_ORDER_MARK = "﻿"
    }
}
