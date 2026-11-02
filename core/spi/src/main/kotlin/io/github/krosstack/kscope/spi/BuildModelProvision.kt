package io.github.krosstack.kscope.spi

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.BuildRoot
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Repository
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.model.Target
import io.github.krosstack.kscope.model.Variant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
public data class BuildModel(
    public val buildRoots: List<BuildRoot>,
    public val modules: List<Module>,
    public val targets: List<Target>,
    public val variants: List<Variant>,
    public val sourceSets: List<SourceSet>,
    public val dependencies: List<Relationship>,
)

@Serializable
public sealed interface BuildModelResult {
    /**
     * [facts] are facts about the build that are not part of the [model], such as the symbols of version catalog
     * entries and the references to them; the engine stores them with the model. [classpaths] holds the absolute
     * compile classpath entries of source sets. They are runtime data of this process, never part of the model, and
     * are not serialized.
     */
    @Serializable
    @SerialName("provided")
    public data class Provided(
        public val model: BuildModel,
        public val diagnostics: List<Diagnostic>,
        @Transient public val classpaths: Map<SourceSetId, List<String>> = emptyMap(),
        public val facts: List<Fact> = emptyList(),
    ) : BuildModelResult

    @Serializable
    @SerialName("not-applicable")
    public data object NotApplicable : BuildModelResult

    @Serializable
    @SerialName("failed")
    public data class Failed(public val reason: ReasonCode, public val message: String) : BuildModelResult
}

public class BuildModelRequest(
    public val repository: Repository,
    public val artifacts: List<Artifact>,
    public val files: WorkspaceFiles,
    public val trusted: Boolean,
)

public interface BuildModelProvider {
    public val producer: ProducerRef

    /** True when the provider executes build logic. */
    public val requiresTrust: Boolean

    public fun provide(request: BuildModelRequest): BuildModelResult
}
