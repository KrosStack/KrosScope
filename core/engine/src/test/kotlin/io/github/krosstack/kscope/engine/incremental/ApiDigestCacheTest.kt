package io.github.krosstack.kscope.engine.incremental

import io.github.krosstack.kscope.engine.store
import io.github.krosstack.kscope.model.KscopeJson
import kotlinx.serialization.json.JsonObject
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApiDigestCacheTest {
    @Test
    fun `a rebuild deletes the cache before digesting again`() = apiWorkspace().use { workspace ->
        val cache = workspace.indexDirectory.resolve("api-digests.json")
        val producer = ApiRecordingProducer()
        workspace.engine(apiRegistry(producer)).use { engine ->
            engine.update()
            assertTrue(Files.exists(cache))
            producer.apiDigester.calls.clear()
            producer.apiDigester.beforeDigest = { assertFalse(Files.exists(cache)) }

            engine.rebuild()

            assertEquals(listOf(listOf(APP_SOURCE, LIB_SOURCE)), producer.apiDigester.calls)
            assertTrue(Files.exists(cache))
        }
    }

    @Test
    fun `a malformed cache is empty and replaced`() = apiWorkspace().use { workspace ->
        val cache = workspace.indexDirectory.resolve("api-digests.json")
        val producer = ApiRecordingProducer()
        workspace.engine(apiRegistry(producer)).use { engine ->
            engine.update()
            Files.writeString(cache, "{broken")
            producer.apiDigester.calls.clear()

            assertEquals(0, engine.update().unitsProduced)

            assertEquals(listOf(listOf(APP_SOURCE, LIB_SOURCE)), producer.apiDigester.calls)
            assertTrue(KscopeJson.parseToJsonElement(Files.readString(cache)) is JsonObject)
        }
    }

    @Test
    fun `oversized and unassigned artifacts are never digested`() = apiWorkspace().use { workspace ->
        workspace.write("lib/src/main/kotlin/Large.kt", "large API\n" + "x".repeat(500))
        workspace.write("Loose.kt", "loose API\n")
        val producer = ApiRecordingProducer()
        workspace.engine(apiRegistry(producer), maxArtifactBytes = 100).use { engine ->
            engine.update()
            assertEquals(listOf(listOf(APP_SOURCE, LIB_SOURCE)), producer.apiDigester.calls)
        }
    }

    @Test
    fun `saving prunes unused entries and preserves an unchanged cache`() = apiWorkspace().use { workspace ->
        val cache = workspace.indexDirectory.resolve("api-digests.json")
        val producer = ApiRecordingProducer()
        workspace.engine(apiRegistry(producer)).use { engine ->
            engine.update()
            val initial = Files.readString(cache)
            val timestamp = FileTime.fromMillis(1000)
            Files.setLastModifiedTime(cache, timestamp)
            engine.update()
            assertEquals(timestamp, Files.getLastModifiedTime(cache))
            workspace.write(LIB_SOURCE, "lib API\nbody two\n")
            engine.update()
            val changed = Files.readString(cache)
            assertTrue(initial != changed)
            assertEquals(2, (KscopeJson.parseToJsonElement(changed) as JsonObject).size)
            workspace.delete(LIB_SOURCE)
            engine.update()
            assertEquals(1, (KscopeJson.parseToJsonElement(Files.readString(cache)) as JsonObject).size)
        }
    }

    @Test
    fun `fresh scans have identical fingerprints and cache bytes`() {
        val scans = List(2) {
            apiWorkspace().use { workspace ->
                val producer = ApiRecordingProducer()
                workspace.engine(apiRegistry(producer)).use { engine ->
                    engine.update()
                    val fingerprints = engine.store.units().filter { it.producer == producer.descriptor.producer }
                        .associate { it.owner to it.fingerprint }
                    fingerprints to Files.readString(workspace.indexDirectory.resolve("api-digests.json"))
                }
            }
        }
        assertEquals(scans.first(), scans.last())
    }
}
