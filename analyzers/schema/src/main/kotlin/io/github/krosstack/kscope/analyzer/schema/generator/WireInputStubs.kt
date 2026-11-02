package io.github.krosstack.kscope.analyzer.schema.generator

import io.github.krosstack.kscope.analyzer.schema.generator.specs.SpecTarget
import io.github.krosstack.kscope.analyzer.schema.proto.ProtoIdentities
import io.github.krosstack.kscope.analyzer.schema.proto.ProtoReadings
import io.github.krosstack.kscope.analyzer.schema.proto.ProtoTypeIndex
import io.github.krosstack.kscope.analyzer.schema.proto.WireKotlinPlan

private const val GENERATOR = "wire"

/**
 * Makes the Kotlin classes that Wire generates in a module from a proto file that the module reads from a source path,
 * wherever the file is. The classes are declared in the home of the module and come from the proto symbols of the
 * file, whose ids name the module that holds the file: the two identities are not the same.
 */
internal class WireInputStubs(private val readings: ProtoReadings, private val types: ProtoTypeIndex) {
    fun of(home: StubHome, target: SpecTarget): GeneratorOutcome {
        val file = readings.of(target.artifact) ?: return GeneratorOutcome.EMPTY
        val source = ProtoIdentities(target.placement.identity, file.packageName)
        val stub = WireKotlinPlan(file, source, types).stub()
        return home.stubbed(stub, GENERATOR, "wire-input/${target.virtualName}.kt")
    }
}
