package io.github.krosstack.kscope.cli.progress

import io.github.krosstack.kscope.service.ProgressEvent
import io.github.krosstack.kscope.service.ProgressStage

/** What one stage has reported so far. [total] is null while it is not known. */
internal class StageProgress(val stage: ProgressStage, val startedAt: Long) {
    var done: Long = 0
    var total: Long? = null
    var unit: String = ""
    var lastHeartbeatAt: Long = startedAt

    fun hasCounts(): Boolean = unit.isNotEmpty()

    fun update(event: ProgressEvent.Advanced) {
        done = event.done
        total = event.total
        unit = event.unit
    }
}
