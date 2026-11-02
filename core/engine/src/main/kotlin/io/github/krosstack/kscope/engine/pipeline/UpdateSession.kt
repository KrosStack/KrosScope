package io.github.krosstack.kscope.engine.pipeline

import io.github.krosstack.kscope.engine.progress.ProgressListener
import io.github.krosstack.kscope.engine.progress.ProgressStage
import io.github.krosstack.kscope.engine.progress.advance
import io.github.krosstack.kscope.engine.progress.stage
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.Fingerprint
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.spi.graph.GraphStore
import java.util.concurrent.atomic.AtomicInteger

internal data class UnitKey(val producer: ProducerRef, val owner: OwnershipUnit)

internal data class PlannedUnit(val producer: ProducerRef, val owner: OwnershipUnit, val fingerprint: Fingerprint) {
    val key = UnitKey(producer, owner)
}

/** Counts the units of one stage that are done and reports each step to the listener. */
internal class UnitProgress(
    private val listener: ProgressListener,
    private val stage: ProgressStage,
    private val total: Int,
    private val unit: String,
) {
    private val done = AtomicInteger()

    fun advance(count: Int = 0) {
        listener.advance(stage, done.addAndGet(count).toLong(), total.toLong(), unit)
    }
}

/** Everything the stages of one update share. */
internal class UpdateSession(val store: GraphStore, val progress: ProgressListener = ProgressListener.NONE) {
    val ledger = UnitLedger(store)
    val timer = StageTimer()
    val notices = mutableListOf<Diagnostic>()

    /** Runs [block] as a reported stage whose time is recorded under the id of the stage. */
    fun <T> stage(stage: ProgressStage, block: () -> T): T = progress.stage(stage) { timer.time(stage.id, block) }
}
