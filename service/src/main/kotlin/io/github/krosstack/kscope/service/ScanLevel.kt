package io.github.krosstack.kscope.service

import io.github.krosstack.kscope.engine.ScanLevel as EngineLevel

/** How deep a scan goes. [EXACT] runs the build of the workspace, so it needs the user's say. */
public enum class ScanLevel(public val wireName: String, internal val inEngine: EngineLevel) {
    SYNTAX("syntax", EngineLevel.SYNTAX),
    RESOLVED("resolved", EngineLevel.RESOLVED),
    EXACT("exact", EngineLevel.EXACT),
    ;

    public companion object {
        public val wireNames: List<String> = entries.map { it.wireName }

        public fun parse(name: String): ScanLevel? = entries.firstOrNull { it.wireName == name }

        internal fun of(level: EngineLevel): ScanLevel = entries.first { it.inEngine == level }
    }
}
