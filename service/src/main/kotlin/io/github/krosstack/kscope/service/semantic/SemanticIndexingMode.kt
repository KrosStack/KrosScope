package io.github.krosstack.kscope.service.semantic

/** How a scan treats the semantic index: finish it before returning, leave it to later calls, or leave it alone. */
public enum class SemanticIndexingMode { FOREGROUND, DEFERRED, OFF }
