@file:JvmName("Main")

package io.github.krosstack.kscope.semantic.worker

import io.github.krosstack.kscope.semantic.worker.tokenizer.TokenizerFile
import io.github.krosstack.kscope.semantic.worker.tokenizer.WordPieceTokenizer
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.nio.file.Path

public fun main(args: Array<String>) {
    val protocol = FileOutputStream(FileDescriptor.out).bufferedWriter(Charsets.UTF_8)
    System.setOut(System.err)
    require(args.size == 1) { "Expected model directory" }
    val files = ModelFiles(Path.of(args.single()))
    val tokenizer = WordPieceTokenizer(TokenizerFile(files.tokenizer), files.descriptor.maxTokens)
    OnnxEmbedder(files, tokenizer).use { embedder ->
        WorkerLoop(
            System.`in`.bufferedReader(Charsets.UTF_8),
            protocol,
            files.descriptor,
            tokenizer,
        )
            .run(embedder::embed)
    }
}
