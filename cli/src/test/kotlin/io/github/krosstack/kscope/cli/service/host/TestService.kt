package io.github.krosstack.kscope.cli.service.host

import io.github.krosstack.kscope.cli.service.home.ServiceDirectory
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicLong

internal class TestService(
    val directory: ServiceDirectory,
    val instance: ServiceInstance,
    val events: ServiceEvents,
    val clock: AtomicLong,
    val running: Future<*>,
)
