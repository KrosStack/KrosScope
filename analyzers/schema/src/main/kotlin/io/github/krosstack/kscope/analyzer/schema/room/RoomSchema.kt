package io.github.krosstack.kscope.analyzer.schema.room

import io.github.krosstack.kscope.analyzer.schema.Span
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

internal class RoomField(
    val fieldPath: String,
    val columnName: String,
    val affinity: String,
    val isNotNull: Boolean,
    val span: Span,
)

internal class RoomEntity(val tableName: String, val createSql: String, val fields: List<RoomField>, val span: Span)

/** The entities of one exported version of a Room database. */
internal class RoomSchema(val version: Int, val entities: List<RoomEntity>)

private val tableNameKey = Regex(""""tableName"\s*:\s*"((?:[^"\\]|\\.)*)"""")
private val columnNameKey = Regex(""""columnName"\s*:\s*"((?:[^"\\]|\\.)*)"""")

/** Reads the JSON that Room exports to its schema directory, and finds where the entities and fields are in it. */
internal object RoomSchemaReader {
    /** Null when the text is not a Room schema export. */
    fun read(text: String): RoomSchema? {
        val database = try {
            (Json.parseToJsonElement(text) as? JsonObject)?.get("database") as? JsonObject
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
        val entities = (database?.get("entities") as? JsonArray)?.filterIsInstance<JsonObject>()
        if (database == null || entities == null) return null
        val starts = tableNameKey.findAll(text).toList()
        val version = (database["version"] as? JsonPrimitive)?.intOrNull ?: 0
        val parsed = entities.mapIndexedNotNull { index, entity -> entityOf(entity, text, starts, index) }
        return RoomSchema(version, parsed)
    }

    private fun entityOf(entity: JsonObject, text: String, starts: List<MatchResult>, index: Int): RoomEntity? {
        val tableName = entity.string("tableName") ?: return null
        val start = starts.getOrNull(index)?.range?.first ?: 0
        val end = starts.getOrNull(index + 1)?.range?.first ?: text.length
        val columns = columnNameKey.findAll(text.substring(start, end)).toList()
        val fieldObjects = (entity["fields"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
        val fields = fieldObjects.mapIndexedNotNull { i, field ->
            fieldOf(field, columns.getOrNull(i), start)
        }
        val span = Span(start, fields.lastOrNull()?.span?.end ?: start)
        val createSql = entity.string("createSql").orEmpty().replace("\${TABLE_NAME}", tableName)
        return RoomEntity(tableName, createSql, fields, span)
    }

    private fun fieldOf(field: JsonObject, key: MatchResult?, offset: Int): RoomField? {
        val columnName = field.string("columnName") ?: return null
        val group = key?.groups?.get(1)?.range
        val span = group?.let { Span(offset + it.first, offset + it.last + 1) } ?: Span(offset, offset)
        return RoomField(
            fieldPath = field.string("fieldPath") ?: columnName,
            columnName = columnName,
            affinity = field.string("affinity").orEmpty(),
            isNotNull = (field["notNull"] as? JsonPrimitive)?.contentOrNull == "true",
            span = span,
        )
    }

    private fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull
}
