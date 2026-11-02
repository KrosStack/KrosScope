package io.github.krosstack.kscope.engine.hashing

import io.github.krosstack.kscope.model.Fingerprint
import java.security.MessageDigest
import java.util.HexFormat

private const val SEPARATOR: Byte = 0

internal fun sha256(): MessageDigest = MessageDigest.getInstance("SHA-256")

internal fun ByteArray.toHex(): String = HexFormat.of().formatHex(this)

/** Hashes a sequence of parts so that no two different sequences share an input stream. */
internal class FingerprintBuilder {
    private val digest = sha256()

    fun add(part: String): FingerprintBuilder {
        val bytes = part.toByteArray(Charsets.UTF_8)
        digest.update(bytes.size.toString().toByteArray(Charsets.US_ASCII))
        digest.update(SEPARATOR)
        digest.update(bytes)
        return this
    }

    fun addAll(parts: Iterable<String>): FingerprintBuilder {
        parts.forEach { add(it) }
        return this
    }

    fun digestHex(): String = digest.digest().toHex()

    fun build(): Fingerprint = Fingerprint(digestHex())
}

internal fun fingerprintOf(vararg parts: String): Fingerprint = FingerprintBuilder().addAll(parts.asList()).build()

internal fun digestOf(parts: Iterable<String>): String = FingerprintBuilder().addAll(parts).digestHex()
