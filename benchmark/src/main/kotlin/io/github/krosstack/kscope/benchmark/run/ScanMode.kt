package io.github.krosstack.kscope.benchmark.run

import io.github.krosstack.kscope.service.ScanLevel

/**
 * How the workspace is scanned: from syntax alone, with resolution and without running its build, or with the build
 * trusted for its exact model. The label of the two older modes is kept for the commands that already use it.
 */
public enum class ScanMode(public val label: String, public val level: ScanLevel) {
    SYNTAX("syntax", ScanLevel.SYNTAX),
    DEFAULT("default", ScanLevel.RESOLVED),
    TRUSTED("trusted", ScanLevel.EXACT),
    ;

    public companion object {
        public fun parse(text: String): ScanMode? =
            entries.firstOrNull { it.label == text } ?: entries.firstOrNull { it.level.wireName == text }
    }
}
