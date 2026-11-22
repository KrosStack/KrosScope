package io.github.krosstack.kscope.service.status

import io.github.krosstack.kscope.engine.inspection.IndexInspection
import io.github.krosstack.kscope.engine.inspection.inspectIndex
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.ScanLevel
import io.github.krosstack.kscope.service.freshness.FreshnessCheck
import io.github.krosstack.kscope.service.freshness.ScanTimeFile
import io.github.krosstack.kscope.service.freshness.checkoutsOf
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

public const val STATUS_LIMIT_MILLIS: Long = 10_000
public const val STATUS_MAX_FILES: Int = 200_000
private const val STATUS_THREADS = 8

public fun KrosScope.status(
    limitMillis: Long = STATUS_LIMIT_MILLIS,
    maxFiles: Int = STATUS_MAX_FILES,
    now: () -> Instant = Instant::now,
): IndexStatus = when (val inspection = engine.inspectIndex()) {
    IndexInspection.NoIndex -> IndexStatus.NoIndex
    is IndexInspection.OtherFormat -> IndexStatus.OtherFormat(inspection.found, inspection.expected)
    is IndexInspection.Damaged -> IndexStatus.Damaged(inspection.reason)
    is IndexInspection.Readable -> present(inspection, limitMillis, maxFiles, now)
}

private fun KrosScope.present(
    inspection: IndexInspection.Readable,
    limitMillis: Long,
    maxFiles: Int,
    now: () -> Instant,
): IndexStatus.Present {
    val started = System.nanoTime()
    val scan = ScanTimeFile(indexDirectory).read()
    val checkouts = checkoutsOf(inspection.repositories) { true }
    val freshness = FreshnessCheck(limitMillis, maxThreads = STATUS_THREADS, maxFiles = maxFiles).of(checkouts, scan)
    return IndexStatus.Present(
        level = ScanLevel.of(inspection.level),
        scannedAt = freshness.scannedAt,
        ageSeconds = freshness.scannedAt?.let { Duration.between(Instant.parse(it), now()).seconds.coerceAtLeast(0) },
        freshness = freshness,
        repositories = inspection.repositories.size,
        checkMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
    )
}
