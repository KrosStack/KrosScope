package io.github.krosstack.kscope.engine

/** How deep a scan goes. Each level includes what the shallower one computes. */
public enum class ScanLevel {
    SYNTAX,
    RESOLVED,
    EXACT,
}
