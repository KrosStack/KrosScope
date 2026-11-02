package io.github.krosstack.kscope.analyzer.kotlin.worker.digest

import com.intellij.psi.util.PsiTreeUtil
import io.github.krosstack.kscope.analyzer.kotlin.worker.ResponseWriter
import io.github.krosstack.kscope.analyzer.kotlin.worker.aa.ScriptParser
import io.github.krosstack.kscope.spi.worker.WorkerFile
import io.github.krosstack.kscope.spi.worker.WorkerRequest
import io.github.krosstack.kscope.spi.worker.WorkerResponse
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat

/** Answers every source of a request: with its API digest, or with none when the source has no digest. */
internal class ApiDigestRequest(private val writer: ResponseWriter) {
    fun answer(request: WorkerRequest.DigestApi) {
        ScriptParser().use { parser ->
            request.sources.forEach { source ->
                writer.send(WorkerResponse.ApiDigest(request.requestId, source.ref, digest(source, parser)))
            }
        }
        writer.send(WorkerResponse.Completed(request.requestId))
    }

    private fun digest(source: WorkerFile, parser: ScriptParser): String? {
        val name = source.ref.path.value.substringAfterLast('/')
        if (!name.endsWith(EXTENSION, ignoreCase = true)) return null
        val parsedAs = name.dropLast(EXTENSION.length) + EXTENSION
        val file = readText(source)?.let { parser.parse(parsedAs, it) }
        return file?.takeUnless { it.isScript() || PsiTreeUtil.hasErrorElements(it) }?.let {
            val bytes = ApiText().of(it).toByteArray(Charsets.UTF_8)
            HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
        }
    }

    private fun readText(source: WorkerFile): String? = try {
        Files.readString(Path.of(source.absolutePath), Charsets.UTF_8)
    } catch (_: IOException) {
        null
    } catch (_: InvalidPathException) {
        null
    } catch (_: SecurityException) {
        null
    }

    private companion object {
        const val EXTENSION = ".kt"
    }
}
