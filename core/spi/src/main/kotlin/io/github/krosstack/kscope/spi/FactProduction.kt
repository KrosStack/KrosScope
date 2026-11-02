package io.github.krosstack.kscope.spi

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactType
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.model.Repository
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.SourceSetId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.nio.file.Path

@Serializable
public enum class AnalyzerCapability {
    @SerialName("declarations")
    DECLARATIONS,

    @SerialName("resolved-references")
    RESOLVED_REFERENCES,

    @SerialName("calls")
    CALLS,

    @SerialName("inheritance")
    INHERITANCE,

    @SerialName("contract-endpoints")
    CONTRACT_ENDPOINTS,

    @SerialName("script-outline")
    SCRIPT_OUTLINE,

    @SerialName("api-digest")
    API_DIGEST,
}

/** What makes a unit of a producer stale. */
public enum class InputBasis {
    /** The content of the artifacts it consumes, and the build model they belong to. */
    ARTIFACT_CONTENT,

    /**
     * The revision of the repository and the paths of its artifacts, not their content. The producer has one
     * [OwnershipKind.REPOSITORY] unit that holds every artifact whatever its type, so [ProducerDescriptor.consumes] is
     * not consulted. For producers that read the version-control history.
     */
    REPOSITORY_REVISION,
}

public data class ProducerDescriptor(
    public val producer: ProducerRef,
    public val consumes: Set<ArtifactType>,
    public val capabilities: Set<AnalyzerCapability>,
    public val granularity: OwnershipKind,
    public val inputs: InputBasis = InputBasis.ARTIFACT_CONTENT,
) {
    init {
        require(inputs == InputBasis.ARTIFACT_CONTENT || granularity == OwnershipKind.REPOSITORY) {
            "A producer whose input is the repository revision must own repository units"
        }
    }
}

/**
 * The repository of a scan: its record, with the revision that HEAD pointed to when the scan began, and the absolute
 * directory of the workspace root, which is the directory of the repository or one below it.
 */
public data class RepositoryCheckout(public val repository: Repository, public val root: Path)

public class AnalysisUnit(
    public val owner: OwnershipUnit,
    public val artifacts: List<Artifact>,
    public val sourceSet: SourceSet?,
    public val module: Module?,
)

/**
 * [units] are the units to produce. [artifacts] are all artifacts handed to capabilities, also those of units that are
 * not produced, for analyzers that need the whole workspace as context. [classpaths] are the absolute compile
 * classpath entries a build model provider resolved, per source set. The engine always sets [checkout]; only requests
 * that a test builds by hand leave it out.
 */
public data class ProduceRequest(
    public val units: List<AnalysisUnit>,
    public val model: BuildModel,
    public val files: WorkspaceFiles,
    public val syntheticSources: List<SyntheticSource>,
    public val classpaths: Map<SourceSetId, List<String>> = emptyMap(),
    public val artifacts: List<Artifact> = emptyList(),
    public val checkout: RepositoryCheckout? = null,
)

public fun interface FactSink {
    public fun accept(batch: FactBatch)
}

public interface FactProducer {
    public val descriptor: ProducerDescriptor

    /** The optional API digester for the sources this producer consumes. */
    public val apiDigester: ApiDigester? get() = null

    public fun produce(request: ProduceRequest, sink: FactSink)
}
