package io.github.krosstack.kscope.benchmark.semantic.cost

import io.github.krosstack.kscope.benchmark.context.identity.ContextBenchmarkIdentity
import io.github.krosstack.kscope.benchmark.context.identity.ContextPipelineIdentity
import io.github.krosstack.kscope.benchmark.realrepos.measurement.canonicalFuturePath
import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.KscopeJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.encodeToJsonElement
import java.nio.file.Files
import java.nio.file.Path

internal data class SemanticCostOptions(
    val corpus: Path,
    val index: Path,
    val output: Path,
    val pipeline: ContextPipelineIdentity,
    val machine: Map<String, String>? = null,
    val workload: Workload? = null,
) {
    @Serializable
    data class Workload(val identity: ContentHash, val queries: List<String>, val activeIndexing: Boolean = false)

    companion object {
        fun parse(args: List<String>): SemanticCostOptions {
            val flags = flags(args)
            fun path(flag: String) = Path.of(flags.getValue(flag)).toAbsolutePath().normalize()
            val corpus = path("--corpus").toRealPath()
            val index = path("--index").toRealPath()
            val output = canonicalFuturePath(path("--out"))
            require(
                !output.startsWith(corpus) && !corpus.startsWith(output) &&
                    !output.startsWith(
                        index,
                    ) && !index.startsWith(output),
            ) { "Output must be separate from corpus and index." }
            val pipeline = KscopeJson.decodeFromString<ContextPipelineIdentity>(Files.readString(path("--identity")))
            requireNotNull(pipeline.generation) { "A selected generation identity is required." }
            val machine = optionalInput(flags, "--machine")?.let(::readMachine)
            val workload = optionalInput(flags, "--workload")?.let(::readWorkload)
            return SemanticCostOptions(corpus, index, output, pipeline, machine, workload)
        }

        private fun optionalInput(flags: Map<String, String>, flag: String): Path? = flags[flag]?.let {
            Path.of(it).also { path -> require(Files.exists(path)) { "$flag input file does not exist." } }
        }

        private fun flags(args: List<String>): Map<String, String> {
            require(args.firstOrNull() == "semantic-cost") { "Expected semantic-cost." }
            val pairs = args.drop(1).chunked(2)
            require(pairs.all { it.size == 2 && it.last().isNotBlank() && !it.last().startsWith("--") })
            val flags = pairs.associate { it.first() to it.last() }
            val required = setOf("--corpus", "--index", "--out", "--identity")
            require(flags.size == pairs.size && flags.keys.containsAll(required)) { "Missing or duplicate flags." }
            require((required + setOf("--machine", "--workload")).containsAll(flags.keys)) { "Unknown cost flag." }
            return flags
        }

        private fun readMachine(path: Path): Map<String, String> {
            val machine = KscopeJson.decodeFromString<Map<String, String>>(Files.readString(path))
            require(validMachine(machine)) { "Incomplete reference machine descriptor." }
            return machine
        }

        private fun readWorkload(path: Path): Workload {
            val workload = KscopeJson.decodeFromString<Workload>(Files.readString(path))
            require(workload.queries.isNotEmpty() && workload.queries.all(String::isNotBlank)) {
                "A workload needs actual nonblank paraphrase queries."
            }
            require(
                workload.identity == ContextBenchmarkIdentity.fingerprint(
                    KscopeJson.encodeToJsonElement(workload.queries),
                ),
            ) {
                "Workload identity differs from its ordered queries."
            }
            return workload
        }

        private fun validMachine(machine: Map<String, String>): Boolean =
            machine.keys.containsAll(setOf("reference", "cpu", "cores", "ram", "os", "jvm")) &&
                machine.values.all(String::isNotBlank) && machine["reference"] in setOf("windows", "linux-four-core") &&
                (machine["cores"]?.toIntOrNull() ?: 0) > 0 &&
                (machine["reference"] != "linux-four-core" || machine["cores"] == "4")
    }
}
