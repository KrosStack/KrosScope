package io.github.krosstack.kscope.analyzer.gradle.tooling

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat

/** The injected plugin jar and the init script that applies it, extracted below the index directory. */
internal class PluginAssets(val jar: Path, val initScript: Path, val hash: String) {
    companion object {
        private const val JAR_RESOURCE = "/kscope/gradle/gradle-model-plugin.jar"
        private const val INIT_SCRIPT_RESOURCE = "/kscope/gradle/kscope-model.init.gradle"
        private const val HASH_LENGTH = 16

        /** The files are named by the hash of their content, so a changed plugin never reuses a stale copy. */
        fun extract(gradleModelDirectory: Path): PluginAssets {
            val jarBytes = resource(JAR_RESOURCE)
            val scriptBytes = resource(INIT_SCRIPT_RESOURCE)
            val hash = hashOf(jarBytes, scriptBytes)
            val directory = gradleModelDirectory.resolve("plugin").resolve(hash.take(HASH_LENGTH))
            Files.createDirectories(directory)
            val jar = directory.resolve("kscope-gradle-model.jar").also { writeIfDifferent(it, jarBytes) }
            val script = directory.resolve("kscope-model.init.gradle").also { writeIfDifferent(it, scriptBytes) }
            return PluginAssets(jar, script, hash)
        }

        private fun resource(name: String): ByteArray {
            val stream = checkNotNull(PluginAssets::class.java.getResourceAsStream(name)) { "Missing resource $name" }
            return stream.use { it.readBytes() }
        }

        private fun writeIfDifferent(target: Path, bytes: ByteArray) {
            if (!Files.isRegularFile(target) || !Files.readAllBytes(target).contentEquals(bytes)) {
                Files.write(target, bytes)
            }
        }

        private fun hashOf(vararg contents: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256")
            contents.forEach { digest.update(it) }
            return HexFormat.of().formatHex(digest.digest())
        }
    }
}
