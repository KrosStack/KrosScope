package io.github.krosstack.kscope.service.wiring

import io.github.krosstack.kscope.service.ProgressEvent
import io.github.krosstack.kscope.service.ProgressListener
import io.github.krosstack.kscope.service.ProgressStage
import io.github.krosstack.kscope.engine.progress.ProgressEvent as EngineEvent
import io.github.krosstack.kscope.engine.progress.ProgressListener as EngineListener
import io.github.krosstack.kscope.engine.progress.ProgressStage as EngineStage

internal fun EngineStage.toService(): ProgressStage = ProgressStage(id)

internal fun ProgressListener.toEngine(): EngineListener =
    if (this === ProgressListener.NONE) EngineListener.NONE else EngineListener { onEvent(it.toService()) }

private fun EngineEvent.toService(): ProgressEvent = when (this) {
    is EngineEvent.Started -> ProgressEvent.Started(stage.toService())
    is EngineEvent.Advanced -> ProgressEvent.Advanced(stage.toService(), done, total, unit)
    is EngineEvent.Finished -> ProgressEvent.Finished(stage.toService())
}
