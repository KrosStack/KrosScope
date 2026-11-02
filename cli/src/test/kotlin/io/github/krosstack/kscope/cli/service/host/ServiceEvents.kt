package io.github.krosstack.kscope.cli.service.host

import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class ServiceEvents : (Path, WorkspaceState) -> Unit {
    private val lock = ReentrantLock()
    private val changed = lock.newCondition()
    private val states = mutableMapOf<Path, WorkspaceState>()
    private val texts = mutableMapOf<Path, MutableList<String>>()

    override fun invoke(path: Path, state: WorkspaceState) = lock.withLock {
        states[path] = state
        texts.getOrPut(path) { mutableListOf() }.add(state.text)
        changed.signalAll()
    }

    fun await(path: Path, accepted: (WorkspaceState) -> Boolean): WorkspaceState = lock.withLock {
        var remaining = TimeUnit.SECONDS.toNanos(WAIT_SECONDS)
        var found = states[path]?.takeIf(accepted)
        while (found == null) {
            check(remaining > 0) { "$path never reached the awaited state; it is ${states[path]}" }
            remaining = changed.awaitNanos(remaining)
            found = states[path]?.takeIf(accepted)
        }
        found
    }

    fun history(path: Path): List<String> = lock.withLock { texts[path].orEmpty().toList() }

    private companion object {
        const val WAIT_SECONDS = 60L
    }
}
