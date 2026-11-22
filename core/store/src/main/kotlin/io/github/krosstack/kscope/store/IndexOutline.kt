package io.github.krosstack.kscope.store

import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.model.Repository

public data class UnitKind(public val producer: ProducerRef, public val kind: OwnershipKind)

public data class IndexOutline(public val repositories: List<Repository>, public val units: Set<UnitKind>)

public sealed interface IndexRead {
    public data class Found(public val outline: IndexOutline) : IndexRead

    public data class Unusable(public val state: IndexState) : IndexRead
}
