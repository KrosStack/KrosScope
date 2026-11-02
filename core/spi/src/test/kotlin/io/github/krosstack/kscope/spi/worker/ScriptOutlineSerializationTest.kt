package io.github.krosstack.kscope.spi.worker

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class ScriptOutlineSerializationTest {
    private val range = TextRange(1, 1, 2, 2)
    private val ref = ArtifactRef(RepositoryId("r"), RepoPath("build-logic/src/main/kotlin/P.kt"))

    private fun call(name: String, typeArguments: List<String> = emptyList(), inlined: Boolean = false) =
        ScriptCall(name, null, emptyList(), emptyList(), emptyMap(), range, typeArguments, inlined)

    @Test
    fun `an outline of a script is written without the fields of plugin classes`() {
        val json = KscopeJson.encodeToString(ScriptOutline.serializer(), ScriptOutline(listOf(call("plugins"))))
        assertFalse("pluginClasses" in json)
        assertFalse("typeArguments" in json)
        assertFalse("inlined" in json)
    }

    @Test
    fun `an outline written before the fields of plugin classes existed is read`() {
        val old = """{"calls":[{"name":"plugins","receiver":null,"arguments":[],"body":[],"assignments":{},""" +
            """"range":{"startLine":1,"startColumn":1,"endLine":2,"endColumn":2}}]}"""
        val outline = KscopeJson.decodeFromString(ScriptOutline.serializer(), old)
        assertEquals(listOf(call("plugins")), outline.calls)
        assertEquals(emptyList(), outline.pluginClasses)
    }

    @Test
    fun `plugin classes travel in an outline response`() {
        val plugin = PluginClassOutline(
            "P",
            listOf(call("val", listOf("Project")), call("helper", inlined = true)),
            range,
            listOf("deep"),
        )
        val response = WorkerResponse.Outline("o-1", ref, ScriptOutline(emptyList(), listOf(plugin)))
        val decoded = WorkerProtocol.decodeResponse(WorkerProtocol.encode(response))
        assertEquals(response, assertIs<WorkerResponse.Outline>(decoded))
    }
}
