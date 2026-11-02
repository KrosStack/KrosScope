package io.github.krosstack.kscope.benchmark.workspace

/** A line of a file, the file named as `<repository directory>/<path in the repository>`. */
internal data class Place(val file: String, val line: Int)

/** One end of a link that a scan produced: a place of a declaration or use, or a module of a repository. */
internal sealed interface LinkEnd {
    val repository: String
    val label: String

    data class At(val place: Place) : LinkEnd {
        override val repository: String get() = place.file.substringBefore('/')
        override val label: String get() = "${place.file}:${place.line}"
    }

    data class OfModule(override val repository: String, val path: String) : LinkEnd {
        override val label: String get() = "$repository $path"
    }
}

/** A link between two ends, with the evidence class its relationship states, by its wire name. */
internal data class ProducedLink(val from: LinkEnd, val to: LinkEnd, val evidence: String) {
    val crossesRepositories: Boolean get() = from.repository != to.repository
}
