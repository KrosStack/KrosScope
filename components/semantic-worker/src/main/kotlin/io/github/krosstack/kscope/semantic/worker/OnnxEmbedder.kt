package io.github.krosstack.kscope.semantic.worker

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import io.github.krosstack.kscope.semantic.worker.tokenizer.WordPieceTokenizer
import java.nio.LongBuffer
import kotlin.math.sqrt

internal class OnnxEmbedder(files: ModelFiles, private val tokenizer: WordPieceTokenizer) : AutoCloseable {
    private val environment = OrtEnvironment.getEnvironment()
    private val options = OrtSession.SessionOptions().apply {
        setIntraOpNumThreads(THREADS)
        setInterOpNumThreads(1)
    }
    private val session = try {
        environment.createSession(files.model.toString(), options)
    } catch (failure: ai.onnxruntime.OrtException) {
        options.close()
        throw failure
    }
    private val dimensions = files.descriptor.dimensions

    fun embed(texts: List<String>): List<FloatArray> {
        val encodings = texts.map { tokenizer.encode(it).ids }
        val width = encodings.maxOf { it.size }
        val ids = LongArray(texts.size * width) { tokenizer.pad }
        val mask = LongArray(ids.size)
        encodings.forEachIndexed { row, tokens ->
            tokens.copyInto(ids, row * width)
            mask.fill(1, row * width, row * width + tokens.size)
        }
        val shape = longArrayOf(texts.size.toLong(), width.toLong())
        OnnxTensor.createTensor(environment, LongBuffer.wrap(ids), shape).use { input ->
            OnnxTensor.createTensor(environment, LongBuffer.wrap(mask), shape).use { attention ->
                OnnxTensor.createTensor(environment, LongBuffer.wrap(LongArray(ids.size)), shape).use { types ->
                    return run(input, attention, types, width, texts.size)
                }
            }
        }
    }

    private fun run(
        input: OnnxTensor,
        attention: OnnxTensor,
        types: OnnxTensor,
        width: Int,
        count: Int,
    ): List<FloatArray> {
        val inputs = mapOf("input_ids" to input, "attention_mask" to attention, "token_type_ids" to types)
        session.run(inputs).use { result ->
            val output = result[0] as OnnxTensor
            check(output.info.shape.contentEquals(longArrayOf(count.toLong(), width.toLong(), dimensions.toLong())))
            val values = output.floatBuffer
            return List(count) { row ->
                val vector = FloatArray(dimensions) { column -> values.get(row * width * dimensions + column) }
                val norm = sqrt(vector.sumOf { it.toDouble() * it })
                check(norm.isFinite() && norm > 0) { "Invalid embedding norm" }
                FloatArray(dimensions) { (vector[it] / norm).toFloat() }
            }
        }
    }

    override fun close() {
        session.close()
        options.close()
    }

    companion object {
        const val BATCH_SIZE = 16
        private const val THREADS = 2
    }
}
