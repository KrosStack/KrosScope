package io.github.krosstack.kscope.analyzer.kotlin.worker.outline

import io.github.krosstack.kscope.analyzer.kotlin.worker.SampleWorkspace
import io.github.krosstack.kscope.analyzer.kotlin.worker.aa.ScriptParser
import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.spi.worker.ScriptCall
import io.github.krosstack.kscope.spi.worker.ScriptOutline
import java.nio.file.Files

/** Parses and outlines scripts with one parsing environment, which the test closes. */
class OutlineFixture : AutoCloseable {
    private val parser = ScriptParser()

    fun ofSample(relativePath: String): ScriptOutline {
        val path = SampleWorkspace.samplesRoot.resolve(relativePath)
        return ofText(path.fileName.toString(), Files.readString(path))
    }

    fun ofText(fileName: String, text: String): ScriptOutline = ScriptOutliner().outline(parser.parse(fileName, text))

    /** The outlines of the Kotlin sources below a sample directory, outlined together as one source set. */
    fun ofSources(directory: String): Map<String, ScriptOutline> {
        val root = SampleWorkspace.samplesRoot.resolve(directory)
        val paths = Files.walk(root).use { stream -> stream.filter { it.toString().endsWith(".kt") }.toList() }
        val parsed = paths.sorted().map { path ->
            val name = root.relativize(path).toString().replace('\\', '/')
            name to parser.parse(path.fileName.toString(), Files.readString(path))
        }
        val outliner = SourceOutliner(parsed.map { it.second })
        return parsed.associate { (path, file) -> path to outliner.outline(file) }
    }

    override fun close() = parser.close()
}

fun List<ScriptCall>.named(name: String) = filter { it.name == name }

fun ScriptCall.literals() = arguments.map { it.literal }

/** One line per plugin class, in the order of the sources. */
fun Map<String, ScriptOutline>.pluginClassLines(): String = entries.joinToString("") { (path, outline) ->
    outline.pluginClasses.joinToString("") { "$path ${KscopeJson.encodeToString(it)}\n" }
}
