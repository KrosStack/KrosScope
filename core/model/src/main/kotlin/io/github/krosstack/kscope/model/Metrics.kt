package io.github.krosstack.kscope.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonUnquotedLiteral
import java.math.BigDecimal

/**
 * A numeric property of a node, such as `history.commits`. [name] is namespaced by the producer; the producer of the
 * batch that holds the metric is its provenance. A metric is a number and never a point in time.
 */
@Serializable
public data class Metric(
    public val node: NodeRef,
    public val name: String,
    @Serializable(with = MetricValueSerializer::class)
    public val value: Double,
) {
    init {
        require(value.isFinite()) { "The value of metric $name must be finite" }
    }
}

/** The names and attribute keys of the Git history signals that `analyzers/history` emits and retrieval reads. */
public object HistorySignals {
    public const val COMMITS: String = "history.commits"
    public const val AUTHORS: String = "history.authors"

    /** 0 for the artifact that changed most recently; one more for each artifact that changed earlier. */
    public const val RECENCY_RANK: String = "history.recency-rank"

    /** Attribute keys of `CO_CHANGES_WITH` relationships. */
    public const val SUPPORT: String = "support"
    public const val CONFIDENCE: String = "confidence"
}

/** Renders a number the same way on every run: integral values without a fraction, never in exponent form. */
internal object MetricNumbers {
    fun render(value: Double): String = BigDecimal(value.toString()).stripTrailingZeros().toPlainString()
}

internal object MetricValueSerializer : KSerializer<Double> {
    override val descriptor = PrimitiveSerialDescriptor("kscope.MetricValue", PrimitiveKind.DOUBLE)

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: Double) {
        if (encoder is JsonEncoder) {
            encoder.encodeJsonElement(JsonUnquotedLiteral(MetricNumbers.render(value)))
        } else {
            encoder.encodeDouble(value)
        }
    }

    override fun deserialize(decoder: Decoder): Double = decoder.decodeDouble()
}
