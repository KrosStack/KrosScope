package sample.tasktide.nativebridge

import sample.tasktide.core.model.Task

fun Task.checksum(): Int = Checksum.crc32(checksumPayload())

internal fun Task.checksumPayload(): ByteArray = "$id|$title|${priority.level}|$updatedAt".encodeToByteArray()
