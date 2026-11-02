package io.github.krosstack.kscope.analyzer.schema.proto

import io.github.krosstack.kscope.analyzer.schema.Span

internal enum class ProtoLabel { NONE, OPTIONAL, REQUIRED, REPEATED }

/** A declared element: where it is, where its name is and what its documentation comment says. */
internal class ProtoPlace(val declaration: Span, val nameSpan: Span, val documentation: String?)

internal data class ProtoField(
    val name: String,
    val type: String,
    val label: ProtoLabel,
    val tag: Int,
    val oneOf: String?,
    val place: ProtoPlace,
) {
    val isMap: Boolean get() = type.startsWith("map<")
}

internal class ProtoOneOf(val name: String, val place: ProtoPlace)

internal class ProtoConstant(val name: String, val number: Int, val place: ProtoPlace)

internal sealed interface ProtoType {
    val name: String
    val place: ProtoPlace
}

internal class ProtoMessage(
    override val name: String,
    override val place: ProtoPlace,
    val fields: List<ProtoField>,
    val oneOfs: List<ProtoOneOf>,
    val nested: List<ProtoType>,
) : ProtoType

internal class ProtoEnum(
    override val name: String,
    override val place: ProtoPlace,
    val constants: List<ProtoConstant>,
) : ProtoType

internal class ProtoRpc(val name: String, val requestType: String, val responseType: String, val place: ProtoPlace)

internal class ProtoService(val name: String, val place: ProtoPlace, val rpcs: List<ProtoRpc>)

/** What a proto file declares. [javaPackage] is the `java_package` option, which Wire uses for Kotlin. */
internal class ProtoFile(
    val packageName: String,
    val javaPackage: String?,
    val isProto3: Boolean,
    val types: List<ProtoType>,
    val services: List<ProtoService>,
) {
    /** The package of the Kotlin classes that Wire makes: the Java package if the file sets one. */
    val kotlinPackage: String get() = javaPackage ?: packageName
}

internal sealed interface ProtoReading {
    class Parsed(val file: ProtoFile) : ProtoReading

    class Failed(val message: String) : ProtoReading
}
