package io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.libraries

import com.intellij.openapi.vfs.VirtualFile
import io.github.krosstack.kscope.model.SymbolEcosystem
import org.jetbrains.kotlin.analysis.api.projectStructure.KaBuiltinsModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibraryModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule

/** The ecosystem and coordinate of a library symbol; the coordinate is null when it cannot be told from the jar. */
internal data class LibraryOrigin(val ecosystem: SymbolEcosystem, val coordinate: String?)

private val gradleCacheJar = Regex(""".*/files-2\.1/([^/]+)/([^/]+)/[^/]+/[^/]+/[^/]+$""")
private val kotlinStdlibJar = Regex("""(?:.*/)?(kotlin-stdlib[A-Za-z0-9-]*?)-\d[^/]*\.jar$""")
private val localMavenJar = Regex(""".*/repository/(.+)/([^/]+)/([^/]+)/([^/]+\.jar)$""")

/** Derives where a library symbol comes from, without reading anything but the location of its jar. */
internal object LibraryOrigins {
    private val jdk = LibraryOrigin(SymbolEcosystem.JDK, null)
    private val kotlinStandardLibrary = LibraryOrigin(SymbolEcosystem.MAVEN, "org.jetbrains.kotlin:kotlin-stdlib")
    private val unknown = LibraryOrigin(SymbolEcosystem.UNKNOWN, null)

    fun of(module: KaModule): LibraryOrigin = when (module) {
        is KaLibraryModule -> if (module.isSdk) jdk else ofJar(module.binaryRoots.firstOrNull()?.toString())
        is KaBuiltinsModule -> kotlinStandardLibrary
        else -> unknown
    }

    fun of(file: VirtualFile?): LibraryOrigin = when {
        file == null -> unknown
        file.fileSystem.protocol == "jrt" -> jdk
        else -> ofJar(file.path.substringBefore("!/"))
    }

    fun ofJar(location: String?): LibraryOrigin {
        val path = location?.replace('\\', '/') ?: return unknown
        val coordinate = gradleCacheCoordinate(path) ?: kotlinStdlibCoordinate(path) ?: localMavenCoordinate(path)
        return if (coordinate == null) unknown else LibraryOrigin(SymbolEcosystem.MAVEN, coordinate)
    }

    private fun gradleCacheCoordinate(path: String): String? =
        gradleCacheJar.matchEntire(path)?.destructured?.let { (group, artifact) -> "$group:$artifact" }

    private fun kotlinStdlibCoordinate(path: String): String? =
        kotlinStdlibJar.matchEntire(path)?.groupValues?.get(1)?.let { "org.jetbrains.kotlin:$it" }

    private fun localMavenCoordinate(path: String): String? =
        localMavenJar.matchEntire(path)?.destructured?.let { (group, artifact) ->
            "${group.replace('/', '.')}:$artifact"
        }
}
