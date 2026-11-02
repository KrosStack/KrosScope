package io.github.krosstack.kscope.cli.serve

import io.github.krosstack.kscope.service.trust.MachineDirectory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat

internal class ServeLock private constructor(private val outside: LockedFile?, private val inside: LockedFile) :
    AutoCloseable {
    override fun close() {
        try {
            inside.close()
        } finally {
            outside?.close()
        }
    }

    companion object {
        fun acquire(workspace: Path): ServeLock {
            val outside = acquireOutside(workspace)
            try {
                return ServeLock(outside, LockedFile.acquire(workspace.resolve(".kscope/serve.lock")))
            } catch (failure: IOException) {
                outside?.close()
                throw failure
            }
        }

        // A lock inside .kscope is lost with the directory, and another server could then start beside this one.
        // Where the lock outside cannot be opened, the lock inside alone guards the workspace.
        private fun acquireOutside(workspace: Path): LockedFile? {
            val file = outsideFile(workspace) ?: return null
            return try {
                LockedFile.acquire(file)
            } catch (running: ServerAlreadyRunning) {
                throw running
            } catch (_: IOException) {
                null
            }
        }

        private fun outsideFile(workspace: Path): Path? = try {
            val directory = MachineDirectory.resolve({ System.getProperty(it) }, { System.getenv(it) }).resolve("locks")
            val name = MessageDigest.getInstance("SHA-256").digest(workspace.toRealPath().toString().toByteArray())
            Files.createDirectories(directory).resolve(HexFormat.of().formatHex(name) + ".lock")
        } catch (_: IOException) {
            null
        }
    }
}
