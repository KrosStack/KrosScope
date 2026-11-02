package io.github.krosstack.kscope.service.settings

import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.OpenOptions
import io.github.krosstack.kscope.service.ScanResult
import io.github.krosstack.kscope.service.TemporaryTree
import io.github.krosstack.kscope.service.analysis.SampleWorkspace
import io.github.krosstack.kscope.service.wiring.AnalyzerGroups
import io.github.krosstack.kscope.spi.graph.GraphStore
import java.nio.file.Files
import java.nio.file.Path

/** A checked-in sample scanned again and again with different analyzer switches in its `kscope.toml`. */
internal class AnalyzerSample(name: String) : AutoCloseable {
    val sample = SampleWorkspace(name)
    private val index: Path = Files.createTempDirectory("kscope-analyzer-index")

    init {
        Files.writeString(index.resolve("repository-id"), "fixed-repository-id")
    }

    fun switchOff(vararg kinds: AnalyzerKind) {
        val lines = kinds.joinToString("") { "${it.key} = false\n" }
        sample.write(SETTINGS_FILE, "[analyzers]\n$lines")
    }

    fun withoutFile() {
        Files.deleteIfExists(sample.root.resolve(SETTINGS_FILE))
    }

    fun <T> open(block: (KrosScope) -> T): T =
        KrosScope.open(sample.root, OpenOptions(indexDirectory = index)).use(block)

    fun scan(verify: Boolean = false): ScanResult = open { it.scan(verify = verify) }

    fun dump(): String = open { it.canonicalDump() }

    fun producers(): Set<String> = open { scope ->
        (scope.engine.graph() as GraphStore).units().map { it.producer.id }.toSet()
    }

    fun idsOf(kind: AnalyzerKind): Set<String> {
        val group = AnalyzerGroups.of(AnalyzerSettings.ALL).single { it.kind == kind }
        return (
            group.producers.map { it.descriptor.producer.id } +
                group.synthesizers.map { it.descriptor.producer.id } +
                group.linkers.map { it.descriptor.producer.id }
            ).toSet()
    }

    override fun close() {
        sample.close()
        TemporaryTree.delete(index)
    }

    private companion object {
        const val SETTINGS_FILE = "kscope.toml"
    }
}
