package io.github.krosstack.kscope.benchmark.schema

/** Whether a result carries the level of the index that produced it, and whether that level can be null. */
internal enum class LevelField {
    NONE,
    REQUIRED,
    NULLABLE,
}
