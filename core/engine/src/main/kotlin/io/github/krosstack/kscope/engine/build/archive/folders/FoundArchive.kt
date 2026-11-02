package io.github.krosstack.kscope.engine.build.archive.folders

import java.io.File
import java.nio.file.Path

/** A JAR or AAR that a registered folder holds, with the size and modified time that it had when it was found. */
internal class FoundArchive(val path: Path, val sizeBytes: Long, val modifiedMillis: Long) {
    val fileName: String get() = path.fileName.toString()

    /** Says which file this was and how it looked, to fold into the fingerprint of what was read from it. */
    val fingerprint: String
        get() = "archive ${path.toString().replace(File.separatorChar, '/')} $sizeBytes $modifiedMillis"
}
