package io.github.krosstack.kscope.benchmark.schema

import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.model.context.TokenCount
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(ExperimentalSerializationApi::class)
class DescriptorSchemasTest {
    @Test
    fun `token counts publish their nonnegative model constraint`() {
        val document = DescriptorSchemas.document("sample", Sample.serializer().descriptor)
        val properties = resolve(document, document).getValue("properties").jsonObject

        assertEquals("0", properties.getValue("tokens").jsonObject["minimum"]?.jsonPrimitive?.content)
    }

    @Test
    fun `colliding serialized names fail instead of sharing an incorrect definition`() {
        assertFailsWith<IllegalArgumentException> {
            DescriptorSchemas.document("collision", Collision.serializer().descriptor)
        }
    }

    @Test
    fun `inline identifiers and nullable values retain their json scalar shape`() {
        val document = DescriptorSchemas.document("sample", Sample.serializer().descriptor)
        val properties = resolve(document, document).getValue("properties").jsonObject

        assertEquals(
            "integer",
            resolve(document, properties.getValue("tokens").jsonObject)["type"]?.jsonPrimitive?.content,
        )
        assertEquals("1", properties.getValue("schemaVersion").jsonObject.getValue("const").jsonPrimitive.content)
        val optional = properties.getValue("note").jsonObject.getValue("anyOf").jsonArray
        assertEquals(
            setOf("string", "null"),
            optional.map { it.jsonObject.getValue("type").jsonPrimitive.content }.toSet(),
        )
    }

    @Test
    fun `enums retain serialized names and sealed nodes require their discriminator`() {
        val document = DescriptorSchemas.document("sample", Sample.serializer().descriptor)
        val properties = resolve(document, document).getValue("properties").jsonObject
        val level = resolve(document, properties.getValue("level").jsonObject)
        val levels = level.getValue("enum").jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("minimal", "recommended", "deep", "full"), levels)
        val node = resolve(document, properties.getValue("node").jsonObject)
        val variants = node.getValue("oneOf").jsonArray.map { resolve(document, it.jsonObject) }
        val discriminators = variants.map {
            it.getValue("properties").jsonObject.getValue("type").jsonObject
                .getValue("const").jsonPrimitive.content
        }
        assertContains(discriminators, "artifact")
        variants.forEach {
            assertContains(
                it.getValue("required").jsonArray.map { value -> value.jsonPrimitive.content },
                "type",
            )
        }
    }

    @Test
    fun `a result read from an index may name the location of that index`() {
        val read = DescriptorSchemas.document("sample", Sample.serializer().descriptor, LevelField.REQUIRED)
        val other = DescriptorSchemas.document("sample", Sample.serializer().descriptor)
        val shape = resolve(read, read)

        val location = shape.getValue("properties").jsonObject.getValue("indexLocation").jsonObject
        assertEquals("string", location.getValue("type").jsonPrimitive.content)
        assertEquals(false, "indexLocation" in shape.getValue("required").jsonArray.map { it.jsonPrimitive.content })
        assertEquals(false, "indexLocation" in resolve(other, other).getValue("properties").jsonObject)
    }

    @Test
    fun `a result of a search that can be limited to a repository may say how it was limited`() {
        val limited = DescriptorSchemas.document(
            "sample",
            Sample.serializer().descriptor,
            LevelField.REQUIRED,
            narrowed = true,
        )
        val plain = DescriptorSchemas.document("sample", Sample.serializer().descriptor, LevelField.REQUIRED)
        val shape = resolve(limited, limited)
        val properties = shape.getValue("properties").jsonObject
        val required = shape.getValue("required").jsonArray.map { it.jsonPrimitive.content }

        val strings = listOf("narrowedTo", "outsideNarrowing", "widenedFrom")
        fun typeOf(name: String) = properties.getValue(name).jsonObject.getValue("type").jsonPrimitive.content
        strings.forEach { assertEquals("string", typeOf(it)) }
        assertEquals("boolean", typeOf("incomplete"))
        assertEquals(emptyList(), required.filter { it in strings + "incomplete" })
        assertEquals(false, "narrowedTo" in resolve(plain, plain).getValue("properties").jsonObject)
    }

    @Test
    fun `only the schemas of analysis, context, symbol and node-resolution have the fields of a limited search`() {
        val names = listOf("narrowedTo", "outsideNarrowing", "widenedFrom", "incomplete")
        names.forEach { name ->
            val documents = ApiSchemas.documents().filterValues { "\"$name\"" in it }

            assertEquals(setOf("analysis", "context", "node-resolution", "symbol"), documents.keys, name)
        }
    }

    @Test
    fun `a result that can come from an index that is behind may carry the freshness of that index`() {
        val dated = DescriptorSchemas.document("sample", Sample.serializer().descriptor, LevelField.REQUIRED, true)
        val plain = DescriptorSchemas.document("sample", Sample.serializer().descriptor, LevelField.REQUIRED)
        val shape = resolve(dated, dated)

        val freshness = resolve(dated, shape.getValue("properties").jsonObject.getValue("indexFreshness").jsonObject)
        assertContains(freshness.getValue("properties").jsonObject.keys, "scannedAt")
        assertEquals(false, "indexFreshness" in shape.getValue("required").jsonArray.map { it.jsonPrimitive.content })
        assertEquals(false, "indexFreshness" in resolve(plain, plain).getValue("properties").jsonObject)
    }

    private fun resolve(document: JsonObject, value: JsonObject): JsonObject {
        val reference = value["\$ref"]?.jsonPrimitive?.content ?: return value
        return document.getValue("\$defs").jsonObject.getValue(reference.substringAfterLast('/')).jsonObject
    }

    @Serializable
    private data class Sample(val tokens: TokenCount, val level: ContextLevel, val node: NodeRef, val note: String?)

    @Serializable
    private data class Collision(val first: First, val second: Second)

    @Serializable
    @SerialName("a-b")
    private data class First(val text: String)

    @Serializable
    @SerialName("a_b")
    private data class Second(val count: Int)
}
