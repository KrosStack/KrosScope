package io.github.krosstack.kscope.engine.inspection

import io.github.krosstack.kscope.engine.ScanEngine
import io.github.krosstack.kscope.engine.ScanLevel
import io.github.krosstack.kscope.engine.trustedBuildProducers
import io.github.krosstack.kscope.model.Repository
import io.github.krosstack.kscope.store.IndexProbe
import io.github.krosstack.kscope.store.IndexState

public sealed interface IndexInspection {
    public data object NoIndex : IndexInspection

    public data class OtherFormat(public val found: Int, public val expected: Int) : IndexInspection

    public data class Damaged(public val reason: String) : IndexInspection

    public data class Readable(public val repositories: List<Repository>, public val level: ScanLevel) : IndexInspection
}

public fun ScanEngine.hasAnyIndex(): Boolean = IndexProbe.inspect(indexFile) != IndexState.Absent

public fun ScanEngine.inspectIndex(): IndexInspection = IndexInspector.inspect(indexFile) { trustedBuildProducers() }
