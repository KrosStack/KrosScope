package io.github.krosstack.kscope.service.estimate

internal enum class CacheState {
    NONE,
    COLD,
    WARM,
    DAMAGED,
}
