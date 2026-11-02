package io.github.krosstack.kscope.spi

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.ArtifactType

/**
 * Digests the API of source artifacts: equal digests for two versions of a source mean that every other artifact
 * produces identical facts whichever version is present. [version] changes whenever the digest rules change.
 */
public interface ApiDigester {
    public val version: String
    public val consumes: Set<ArtifactType>
    public fun digest(sources: List<Artifact>, files: WorkspaceFiles): ApiDigests
}

/**
 * What a digester found for the sources it was given: [digests] for the sources that have an API digest, and
 * [withoutDigest] for those it examined and found to have none, which count with their whole content. A source in
 * neither was not examined, as when the digester could not run: it counts with its whole content for now and is to
 * be asked about again.
 */
public data class ApiDigests(public val digests: Map<ArtifactRef, String>, public val withoutDigest: Set<ArtifactRef>) {
    public companion object {
        public val NONE: ApiDigests = ApiDigests(emptyMap(), emptySet())
    }
}
