package io.github.krosstack.kscope.cli.service.host

internal class ReleaseCandidate(val loaded: Boolean, val lastRequestAt: Long, val release: () -> Unit)
