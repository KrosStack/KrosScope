package io.github.krosstack.kscope.analyzer.kotlin.worker

import io.github.krosstack.kscope.analyzer.kotlin.worker.aa.ScriptParser
import io.github.krosstack.kscope.analyzer.kotlin.worker.declare.SyntaxFactPass
import io.github.krosstack.kscope.analyzer.kotlin.worker.digest.ApiDigestRequest
import io.github.krosstack.kscope.analyzer.kotlin.worker.outline.ScriptOutliner
import io.github.krosstack.kscope.analyzer.kotlin.worker.outline.SourceGroups
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.spi.worker.BatchParts
import io.github.krosstack.kscope.spi.worker.WorkerFile
import io.github.krosstack.kscope.spi.worker.WorkerProtocol
import io.github.krosstack.kscope.spi.worker.WorkerRequest
import io.github.krosstack.kscope.spi.worker.WorkerResponse
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * The work behind each request. Results go to [writer] as they are produced: the facts of a source set leave the worker
 * as soon as the source set is done, in messages of at most [maxFactsPerMessage] facts.
 */
internal class WorkerService(
    private val writer: ResponseWriter,
    private val maxFactsPerMessage: Int = BatchParts.MAX_FACTS_PER_MESSAGE,
) {
    private val facts = FactPass(WorkerIdentity.producer)
    private val parser = ScriptParser()
    private val outliner = ScriptOutliner()
    private val syntax = SyntaxFactPass(WorkerIdentity.producer, parser)

    fun ready(): WorkerResponse.Ready =
        WorkerResponse.Ready(WorkerProtocol.PROTOCOL_VERSION, WorkerIdentity.producer, WorkerIdentity.capabilities)

    fun analyze(request: WorkerRequest.Analyze) {
        facts.batchSequence(request.modules).forEach { batch ->
            BatchParts.split(request.requestId, batch, maxFactsPerMessage).forEach(writer::send)
        }
        writer.send(WorkerResponse.Completed(request.requestId))
    }

    fun declare(request: WorkerRequest.Declare) {
        syntax.batchSequence(request.modules).forEach { batch ->
            BatchParts.split(request.requestId, batch, maxFactsPerMessage).forEach(writer::send)
        }
        writer.send(WorkerResponse.Completed(request.requestId))
    }

    fun outlineScripts(request: WorkerRequest.OutlineScripts) {
        val parsed = request.scripts.map { script ->
            val text = readText(script) ?: return writer.send(unreadable(request.requestId, script))
            script to parser.parse(scriptName(script), text)
        }
        val sources = SourceGroups(parsed.filterNot { (_, file) -> file.isScript() })
        parsed.forEach { (script, file) ->
            val outline = if (file.isScript()) outliner.outline(file) else sources.outline(script, file)
            writer.send(WorkerResponse.Outline(request.requestId, script.ref, outline))
        }
        writer.send(WorkerResponse.Completed(request.requestId))
    }

    fun digestApi(request: WorkerRequest.DigestApi) = ApiDigestRequest(writer).answer(request)

    private fun readText(script: WorkerFile): String? = try {
        String(Files.readAllBytes(Path.of(script.absolutePath)), Charsets.UTF_8)
    } catch (_: IOException) {
        null
    }

    private fun unreadable(requestId: String, script: WorkerFile) = WorkerResponse.Failed(
        requestId,
        ReasonCode("kotlin.script-unreadable"),
        "The script ${script.ref.path.value} cannot be read",
    )

    private fun scriptName(script: WorkerFile): String = script.ref.path.value.substringAfterLast('/')
}
