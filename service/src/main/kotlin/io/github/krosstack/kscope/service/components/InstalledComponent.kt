package io.github.krosstack.kscope.service.components

import io.github.krosstack.kscope.model.KscopeJson
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

public data class InstalledComponent(
    public val name: String,
    public val version: String,
    public val directory: Path,
    public val files: List<String>,
    public val problem: String? = null,
) {
    public companion object {
        internal const val MARKER = "kscope-component.json"

        public fun read(directory: Path): InstalledComponent? {
            val marker = directory.resolve(MARKER)
            if (!Files.isDirectory(directory, NOFOLLOW_LINKS) || !unlinked(directory)) return null
            return try {
                if (!Files.exists(marker, NOFOLLOW_LINKS)) {
                    damaged(directory, "Missing component marker")
                        .takeIf { directory.fileName.toString().contains(".replacing-") }
                } else {
                    require(Files.isRegularFile(marker, NOFOLLOW_LINKS)) { "Invalid component marker" }
                    val json = KscopeJson.parseToJsonElement(Files.readString(marker)).jsonObject
                    require(json.keys == setOf("name", "version", "files")) { "Invalid component marker" }
                    val component = InstalledComponent(
                        json.getValue("name").jsonPrimitive.content,
                        json.getValue("version").jsonPrimitive.content,
                        directory,
                        json.getValue("files").jsonArray.map { it.jsonPrimitive.content },
                    )
                    require(validSegment(component.name) && validSegment(component.version))
                    require(component.files.all(::relative))
                    require(component.name == directory.parent.fileName.toString()) { "Marker names another component" }
                    val folder = directory.fileName.toString()
                    require(folder == component.version || folder.startsWith("${component.version}.replacing-")) {
                        "Marker names another version"
                    }
                    component
                }
            } catch (_: SerializationException) {
                damaged(directory, "Invalid component marker")
            } catch (failure: IllegalArgumentException) {
                val problem = failure.message.orEmpty().takeIf { it.startsWith("Marker names another") }
                damaged(directory, problem ?: "Invalid component marker")
            } catch (failure: IOException) {
                damaged(directory, "Cannot read component marker: ${failure.message}")
            }
        }

        private fun damaged(directory: Path, problem: String): InstalledComponent = InstalledComponent(
            directory.parent.fileName.toString(),
            directory.fileName.toString().substringBefore(".replacing-"),
            directory,
            emptyList(),
            problem,
        )

        public fun write(component: InstalledComponent) {
            val json = buildJsonObject {
                put("name", component.name)
                put("version", component.version)
                putJsonArray("files") { component.files.forEach { add(it) } }
            }
            Files.writeString(component.directory.resolve(MARKER), json.toString())
        }

        internal fun validSegment(value: String): Boolean = value.matches(Regex("[a-zA-Z0-9][a-zA-Z0-9._-]*")) &&
            !value.endsWith('.') &&
            !value.substringBefore('.').matches(Regex("(?i)(con|prn|aux|nul|com[0-9]|lpt[0-9])"))

        internal fun relative(value: String): Boolean = value.split('/').all(::validSegment)

        internal fun unlinked(path: Path): Boolean = try {
            val absolute = path.toAbsolutePath().normalize()
            val existing = generateSequence(absolute) { it.parent }.first { Files.exists(it, NOFOLLOW_LINKS) }
            generateSequence(existing) { it.parent }.all {
                val attributes = Files.readAttributes(it, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
                !attributes.isSymbolicLink && !attributes.isOther
            }
        } catch (_: IOException) {
            false
        }

        internal fun delete(path: Path, boundary: Path = path.parent.toRealPath()) {
            if (!Files.exists(path, NOFOLLOW_LINKS)) return
            Files.walkFileTree(
                path,
                object : SimpleFileVisitor<Path>() {
                    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                        if (attrs.isSymbolicLink || attrs.isOther) {
                            deleteEntry(dir, boundary)
                            return FileVisitResult.SKIP_SUBTREE
                        }
                        inside(dir.toRealPath(), boundary)
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                        deleteEntry(file, boundary)
                        return FileVisitResult.CONTINUE
                    }

                    override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                        if (exc != null) throw exc
                        inside(dir.toRealPath(), boundary)
                        Files.delete(dir)
                        return FileVisitResult.CONTINUE
                    }
                },
            )
        }

        private fun deleteEntry(path: Path, boundary: Path) {
            inside(path.parent.toRealPath().resolve(path.fileName), boundary)
            if (!Files.isSymbolicLink(path) && !Files.isDirectory(path, NOFOLLOW_LINKS)) {
                inside(path.toRealPath(), boundary)
            }
            Files.delete(path)
        }

        private fun inside(path: Path, boundary: Path) {
            if (!path.startsWith(boundary)) throw IOException("Path outside component directory: $path")
        }
    }
}
