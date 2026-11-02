package io.github.krosstack.kscope.benchmark.agent

import java.security.MessageDigest
import java.util.HexFormat

internal object Fingerprint {
    fun of(parts: Sequence<ByteArray>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        parts.forEach { part ->
            digest.update(part.size.toString().toByteArray(Charsets.UTF_8))
            digest.update(SEPARATOR)
            digest.update(part)
        }
        return HexFormat.of().formatHex(digest.digest())
    }

    private const val SEPARATOR: Byte = 0
}
