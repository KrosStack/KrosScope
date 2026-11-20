package io.github.krosstack.kscope.service.semantic

import io.github.krosstack.kscope.service.semantic.query.QueryDeadline
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** How long the worker may take to start, to embed one batch, and to answer a query or a cap request. */
internal data class WorkerDeadlines(
    val batch: Duration = 120.seconds,
    val query: Duration = 10.seconds,
    val startup: Duration = 120.seconds,
    val absoluteQuery: QueryDeadline? = null,
)
