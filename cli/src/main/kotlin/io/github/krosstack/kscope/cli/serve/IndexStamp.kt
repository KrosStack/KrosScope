package io.github.krosstack.kscope.cli.serve

/** What tells that the index changed: another instance of the service, another revision of it, or another state. */
internal data class IndexStamp(val generation: Long, val revision: Long, val indexed: Boolean)
