package io.github.krosstack.kscope.service.semantic

import io.github.krosstack.kscope.retrieval.MlMode
import io.github.krosstack.kscope.retrieval.SemanticStatus
import io.github.krosstack.kscope.service.KrosScope

/** Where the semantic index of the workspace stands. [MlMode.OFF] always answers off. */
public fun KrosScope.semanticStatus(ml: MlMode = MlMode.ON): SemanticStatus =
    if (ml == MlMode.OFF) SemanticStatus.OFF else semantic.status

/** A sentence about a semantic job that is preparing, deferred or failed, or null when there is nothing to say. */
public fun KrosScope.semanticNotice(): String? = semantic.notice

/**
 * Moves a semantic job by one bounded step; call it between requests, on the thread that owns [this].
 * Returns true while more work can be done at once, false when finished, waiting for deferred publication,
 * failed, unavailable or off.
 */
public fun KrosScope.advanceSemanticIndex(): Boolean = semantic.advance(semanticMode)
