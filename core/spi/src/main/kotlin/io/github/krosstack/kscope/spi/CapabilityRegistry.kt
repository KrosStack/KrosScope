package io.github.krosstack.kscope.spi

/**
 * Immutable. Each kind of capability is ordered by id, and ids are unique within a kind. The kinds that store facts
 * share no producer id, because the units a producer owns are stored under its id whatever the kind.
 */
public class CapabilityRegistry(
    detectors: List<ArtifactDetector> = emptyList(),
    buildModelProviders: List<BuildModelProvider> = emptyList(),
    synthesizers: List<SurfaceSynthesizer> = emptyList(),
    producers: List<FactProducer> = emptyList(),
    linkers: List<BoundaryLinker> = emptyList(),
) {
    public val detectors: List<ArtifactDetector> = orderedById("artifact detector", detectors) { it.id }

    public val buildModelProviders: List<BuildModelProvider> =
        orderedById("build model provider", buildModelProviders) { it.producer.id }

    public val synthesizers: List<SurfaceSynthesizer> =
        orderedById("surface synthesizer", synthesizers) { it.descriptor.producer.id }

    public val producers: List<FactProducer> =
        orderedById("fact producer", producers) { it.descriptor.producer.id }

    public val linkers: List<BoundaryLinker> =
        orderedById("boundary linker", linkers) { it.descriptor.producer.id }.also(::requireOneLinkerPerNamespace)

    init {
        requireDistinctAcrossKinds(
            "build model provider" to this.buildModelProviders.map { it.producer.id },
            "surface synthesizer" to this.synthesizers.map { it.descriptor.producer.id },
            "fact producer" to this.producers.map { it.descriptor.producer.id },
            "boundary linker" to this.linkers.map { it.descriptor.producer.id },
        )
    }
}

private fun requireDistinctAcrossKinds(vararg kinds: Pair<String, List<String>>) {
    val kindsById = kinds.flatMap { (kind, ids) -> ids.map { it to kind } }.groupBy({ it.first }, { it.second })
    kindsById.toSortedMap().forEach { (id, usedBy) ->
        require(usedBy.size == 1) { "Producer id $id is used by a ${usedBy.joinToString(" and a ")}" }
    }
}

private fun requireOneLinkerPerNamespace(linkers: List<BoundaryLinker>) {
    val claimed = linkers.flatMap { linker -> linker.namespaces.map { it to linker.descriptor.producer.id } }
    val contested = claimed.groupBy({ it.first }, { it.second }).filterValues { it.size > 1 }.keys.minOrNull()
    require(contested == null) { "Symbolic reference namespace $contested is claimed by more than one linker" }
}

private fun <T> orderedById(kind: String, capabilities: List<T>, idOf: (T) -> String): List<T> {
    val duplicate = capabilities.groupBy(idOf).filterValues { it.size > 1 }.keys.minOrNull()
    require(duplicate == null) { "Duplicate $kind id: $duplicate" }
    return capabilities.sortedBy(idOf)
}
