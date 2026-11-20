package io.github.krosstack.kscope.analyzer.schema.proto

import io.github.krosstack.kscope.analyzer.schema.ModuleSpec
import io.github.krosstack.kscope.analyzer.schema.Render
import io.github.krosstack.kscope.analyzer.schema.TestWorkspace
import io.github.krosstack.kscope.analyzer.schema.facts
import io.github.krosstack.kscope.analyzer.schema.symbols
import kotlin.test.Test
import kotlin.test.assertEquals

private const val PATH = "lib/src/main/proto/choice_group.proto"

private val SOURCE = """
    syntax = "proto2";
    package sample.schema.choicegroup;
    message ChoiceGroups {
      oneof value {
        string first_value = 1;
        string second_value = 2;
        string value = 3;
      }
    }
""".trimIndent()

class ProtoOneOfIdentityTest {
    @Test
    fun `a oneof and a field of the same name in one message keep distinct ids`() {
        val workspace = TestWorkspace(mapOf(PATH to SOURCE), listOf(ModuleSpec(":lib")))
        val symbols = workspace.produce(ProtoProducer()).facts().symbols()

        val ids = symbols.map { Render.id(it.id) }
        assertEquals(ids.size, ids.toSet().size)
        val named = symbols.filter { it.qualifiedName == "sample.schema.choicegroup.ChoiceGroups.value" }
        assertEquals(setOf("proto.oneof", "proto.field"), named.map { it.kind.id }.toSet())
    }
}
