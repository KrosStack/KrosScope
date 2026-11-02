package io.github.krosstack.kscope.cli.serve.watch

import io.github.krosstack.kscope.model.Fingerprint
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.level.automaticUpdatesBlocked
import io.github.krosstack.kscope.service.level.indexIsShallow
import io.github.krosstack.kscope.service.settings.FreshnessReading
import java.nio.file.Path
import java.time.Instant

internal data class WatchSettings(
    val timing: WatchTiming = WatchTiming(),
    val stamp: ((Path) -> Fingerprint)? = null,
    val git: ((Path) -> (() -> Boolean))? = null,
    val onPoll: () -> Unit = {},
    val requiresTrust: (KrosScope) -> Boolean = { it.automaticUpdatesBlocked() },
    val isShallow: (KrosScope) -> Boolean = { it.indexIsShallow() },
    val freshness: (Path) -> FreshnessReading = KrosScope::freshnessOf,
    val wallClock: () -> Instant = Instant::now,
)
