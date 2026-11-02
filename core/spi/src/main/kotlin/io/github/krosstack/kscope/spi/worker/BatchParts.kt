package io.github.krosstack.kscope.spi.worker

import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.OwnershipUnit

/**
 * A fact batch travels as one message per [MAX_FACTS_PER_MESSAGE] facts, so that the size of a protocol line depends
 * on the number of facts it holds and not on the size of the source set they came from. The messages of one batch
 * follow each other, carry the producer, owner and fingerprint of the batch, count their [WorkerResponse.Batch.part]
 * from zero and mark the final one with [WorkerResponse.Batch.last].
 */
public object BatchParts {
    public const val MAX_FACTS_PER_MESSAGE: Int = 500

    /** The messages that carry [batch], with at most [maxFacts] facts each; an empty batch is one message. */
    public fun split(
        requestId: String,
        batch: FactBatch,
        maxFacts: Int = MAX_FACTS_PER_MESSAGE,
    ): List<WorkerResponse.Batch> {
        require(maxFacts > 0) { "A message holds at least one fact" }
        if (batch.facts.size <= maxFacts) return listOf(WorkerResponse.Batch(requestId, batch))
        val slices = batch.facts.chunked(maxFacts)
        return slices.mapIndexed { index, slice ->
            WorkerResponse.Batch(requestId, batch.copy(facts = slice), index, index == slices.lastIndex)
        }
    }

    /** Thrown by [Assembler] for a sequence of messages that is not a sequence of whole batches. */
    public class OutOfSequence(message: String) : IllegalStateException(message)

    /** Joins the messages of each batch again; one assembler serves one request. */
    public class Assembler {
        private var header: FactBatch? = null
        private var nextPart = 0
        private val facts = mutableListOf<Fact>()

        /** The whole batch when [message] completes one, else null. Throws [OutOfSequence] on a broken sequence. */
        public fun accept(message: WorkerResponse.Batch): FactBatch? {
            ensure(message.part == nextPart) {
                "Part ${message.part} of ${owner(message)} came where part $nextPart was due"
            }
            if (message.part == 0) {
                header = message.batch
            } else {
                ensure(sameBatch(requireNotNull(header), message.batch)) {
                    "Part ${message.part} of ${owner(message)} belongs to another batch than part 0"
                }
            }
            facts += message.batch.facts
            if (!message.last) {
                nextPart++
                return null
            }
            return requireNotNull(header).copy(facts = facts.toList()).also { reset() }
        }

        /** The owner of a batch whose messages have started but not ended, or null between batches. */
        public fun incomplete(): OwnershipUnit? = if (nextPart > 0) header?.owner else null

        private fun reset() {
            header = null
            nextPart = 0
            facts.clear()
        }

        private fun sameBatch(first: FactBatch, other: FactBatch) = first.producer == other.producer &&
            first.owner == other.owner && first.inputFingerprint == other.inputFingerprint

        private fun owner(message: WorkerResponse.Batch) = message.batch.owner.key

        private fun ensure(condition: Boolean, message: () -> String) {
            if (!condition) throw OutOfSequence(message())
        }
    }
}
