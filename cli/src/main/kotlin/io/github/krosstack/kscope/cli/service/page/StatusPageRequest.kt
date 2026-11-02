package io.github.krosstack.kscope.cli.service.page

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

internal data class StatusPageRequest(val method: String, val target: String, val host: String?) {
    companion object {
        const val MAX_HEAD_BYTES = 8_192

        fun read(input: InputStream): StatusPageRequest? = readHead(input)?.let(::parse)

        private fun parse(head: String): StatusPageRequest? {
            val lines = head.split("\r\n")
            val parts = lines.first().split(' ')
            val host = lines.drop(1).firstNotNullOfOrNull(::hostOf)
            return if (parts.size == REQUEST_LINE_PARTS) StatusPageRequest(parts[0], parts[1], host) else null
        }

        private fun hostOf(line: String): String? {
            val name = line.substringBefore(':', "")
            return if (name.equals("host", ignoreCase = true)) line.substringAfter(':').trim() else null
        }

        private fun readHead(input: InputStream): String? {
            val bytes = ByteArrayOutputStream()
            while (bytes.size() < MAX_HEAD_BYTES) {
                val next = input.read()
                if (next < 0) return null
                bytes.write(next)
                if (endsHead(bytes)) return bytes.toString(Charsets.ISO_8859_1).removeSuffix(HEAD_END)
            }
            throw IOException("Request head exceeds the limit")
        }

        private fun endsHead(bytes: ByteArrayOutputStream): Boolean {
            val size = bytes.size()
            if (size < HEAD_END.length) return false
            val tail = bytes.toByteArray().copyOfRange(size - HEAD_END.length, size)
            return String(tail, Charsets.ISO_8859_1) == HEAD_END
        }

        private const val REQUEST_LINE_PARTS = 3
        private const val HEAD_END = "\r\n\r\n"
    }
}
