package io.github.krosstack.kscope.cli.serve.map

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal fun JsonObject.text(name: String): String = getValue(name).jsonPrimitive.content

internal fun JsonObject.flag(name: String): Boolean = getValue(name).jsonPrimitive.boolean

internal fun JsonObject.count(name: String): Int = getValue(name).jsonArray.size

internal val JsonObject.version: Int get() = getValue("version").jsonPrimitive.int

internal fun HttpAnswer.labels(): List<String> = json().getValue("nodes").jsonArray.map { it.jsonObject.text("label") }

internal fun HttpAnswer.nodeId(label: String): String =
    json().getValue("nodes").jsonArray.map { it.jsonObject }.first { it.text("label") == label }.text("id")

internal fun EventStream.untilIndexed(): JsonObject =
    generateSequence { nextState() }.first { it.flag("indexed") && it.version > 0 }
