package io.github.krosstack.kscope.cli.service.host

import io.github.krosstack.kscope.cli.serve.ServeAnswer
import io.github.krosstack.kscope.cli.serve.ServeClient
import io.github.krosstack.kscope.cli.serve.ServeRequest
import io.github.krosstack.kscope.cli.serve.ServeServer
import io.github.krosstack.kscope.cli.serve.ServeSettings
import io.github.krosstack.kscope.cli.serve.watch.WatchSettings
import io.github.krosstack.kscope.cli.serve.watch.WatchTiming
import io.github.krosstack.kscope.cli.service.home.ServiceDirectory
import io.github.krosstack.kscope.cli.service.home.ServiceLimits
import io.github.krosstack.kscope.cli.service.home.WorkspacePaths
import io.github.krosstack.kscope.model.Fingerprint
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

internal fun quietWatch() = WatchSettings(
    timing = WatchTiming(pollMillis = 60_000),
    stamp = { Fingerprint("steady") },
    git = { { false } },
    requiresTrust = { false },
)

internal fun <T> withTestService(
    home: Path,
    workspaces: List<Path>,
    settings: ServiceSettings = ServiceSettings(serve = ServeSettings(watch = quietWatch())),
    limits: ServiceLimits = ServiceLimits(),
    block: (TestService) -> T,
): T {
    val directory = ServiceDirectory(home)
    directory.file.write(workspaces.map(WorkspacePaths::key), limits)
    val events = ServiceEvents()
    val clock = AtomicLong()
    val instance = ServiceInstance.open(directory, settings.copy(clock = clock::get, onChange = events))
    val executor = Executors.newSingleThreadExecutor()
    val running = executor.submit { instance.run() }
    try {
        return block(TestService(directory, instance, events, clock, running))
    } finally {
        instance.requestStop()
        running.get(STOP_SECONDS, TimeUnit.SECONDS)
        executor.shutdownNow()
        instance.close()
    }
}

internal fun TestService.awaitState(root: Path, accepted: (WorkspaceState) -> Boolean): WorkspaceState =
    events.await(WorkspacePaths.real(root), accepted)

internal fun TestService.awaitServed(root: Path): ServeServer =
    (awaitState(root) { it is WorkspaceState.Served } as WorkspaceState.Served).server

internal fun TestService.awaitReleased(root: Path): ServeServer =
    (awaitState(root) { it is WorkspaceState.Served && !it.server.loaded } as WorkspaceState.Served).server

internal fun TestService.awaitLoaded(root: Path): ServeServer =
    (awaitState(root) { it is WorkspaceState.Served && it.server.loaded } as WorkspaceState.Served).server

internal fun TestService.control(action: String): ServeAnswer {
    val stdout = StringBuilder()
    val stderr = StringBuilder()
    val request = ServeRequest(token = instance.metadata.token, action = action)
    val code = ServeClient.exchange(instance.metadata, request, requireCompatible = false) { chunk ->
        if (chunk.stream == "stdout") stdout.append(chunk.text) else stderr.append(chunk.text)
    }
    return ServeAnswer(stdout.toString(), stderr.toString(), checkNotNull(code))
}

internal fun TestService.awaitEnd() {
    running.get(STOP_SECONDS, TimeUnit.SECONDS)
}

internal fun TestService.retryAfter(seconds: Long) {
    clock.addAndGet(TimeUnit.SECONDS.toNanos(seconds))
    instance.retryNow()
}

private const val STOP_SECONDS = 60L
