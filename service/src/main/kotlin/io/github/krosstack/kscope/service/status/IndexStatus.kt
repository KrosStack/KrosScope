package io.github.krosstack.kscope.service.status

import io.github.krosstack.kscope.service.ScanLevel
import io.github.krosstack.kscope.service.freshness.IndexFreshness

public sealed interface IndexStatus {
    public data object NoIndex : IndexStatus

    public data class OtherFormat(public val found: Int, public val expected: Int) : IndexStatus

    public data class Damaged(public val reason: String) : IndexStatus

    public data class Present(
        public val level: ScanLevel,
        public val scannedAt: String?,
        public val ageSeconds: Long?,
        public val freshness: IndexFreshness,
        public val repositories: Int,
        public val checkMillis: Long,
    ) : IndexStatus
}
