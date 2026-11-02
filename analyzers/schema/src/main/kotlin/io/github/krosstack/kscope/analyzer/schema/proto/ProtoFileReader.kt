package io.github.krosstack.kscope.analyzer.schema.proto

import com.squareup.wire.Syntax
import com.squareup.wire.schema.Field
import com.squareup.wire.schema.Location
import com.squareup.wire.schema.internal.parser.EnumElement
import com.squareup.wire.schema.internal.parser.FieldElement
import com.squareup.wire.schema.internal.parser.MessageElement
import com.squareup.wire.schema.internal.parser.ProtoFileElement
import com.squareup.wire.schema.internal.parser.ProtoParser
import com.squareup.wire.schema.internal.parser.RpcElement
import com.squareup.wire.schema.internal.parser.ServiceElement
import com.squareup.wire.schema.internal.parser.TypeElement
import io.github.krosstack.kscope.analyzer.schema.firstSentence

private const val JAVA_PACKAGE_OPTION = "java_package"

/** Parses proto files with the Wire schema parser and keeps what the analysis needs, with where the elements are. */
internal class ProtoFileReader {
    fun read(path: String, text: String): ProtoReading = try {
        val element = ProtoParser.parse(Location.get(path), text)
        ProtoReading.Parsed(fileOf(element, ProtoSpans(text)))
    } catch (failure: IllegalStateException) {
        ProtoReading.Failed(failure.message.orEmpty())
    } catch (failure: IllegalArgumentException) {
        ProtoReading.Failed(failure.message.orEmpty())
    }

    private fun fileOf(element: ProtoFileElement, spans: ProtoSpans) = ProtoFile(
        packageName = element.packageName.orEmpty(),
        javaPackage = element.options.firstOrNull { it.name == JAVA_PACKAGE_OPTION }?.value as? String,
        isProto3 = element.syntax == Syntax.PROTO_3,
        types = element.types.mapNotNull { typeOf(it, spans) },
        services = element.services.map { serviceOf(it, spans) },
    )

    private fun typeOf(element: TypeElement, spans: ProtoSpans): ProtoType? = when (element) {
        is MessageElement -> messageOf(element, spans)
        is EnumElement -> enumOf(element, spans)
        else -> null
    }

    private fun messageOf(element: MessageElement, spans: ProtoSpans): ProtoMessage {
        val own = element.fields.map { fieldOf(it, null, spans) }
        val inOneOfs = element.oneOfs.flatMap { oneOf -> oneOf.fields.map { fieldOf(it, oneOf.name, spans) } }
        val oneOfs = element.oneOfs.map { ProtoOneOf(it.name, placeOf(it.location, it.name, it.documentation, spans)) }
        return ProtoMessage(
            name = element.name,
            place = placeOf(element.location, element.name, element.documentation, spans),
            fields = (own + inOneOfs).sortedBy { it.tag },
            oneOfs = oneOfs,
            nested = element.nestedTypes.mapNotNull { typeOf(it, spans) },
        )
    }

    private fun enumOf(element: EnumElement, spans: ProtoSpans) = ProtoEnum(
        name = element.name,
        place = placeOf(element.location, element.name, element.documentation, spans),
        constants = element.constants.map {
            ProtoConstant(it.name, it.tag, spans.placeOf(it.location, it.name, firstSentence(it.documentation), true))
        },
    )

    private fun fieldOf(element: FieldElement, oneOf: String?, spans: ProtoSpans) = ProtoField(
        name = element.name,
        type = element.type,
        label = labelOf(element.label),
        tag = element.tag,
        oneOf = oneOf,
        place = spans.placeOf(element.location, element.name, firstSentence(element.documentation), true),
    )

    private fun labelOf(label: Field.Label?): ProtoLabel = when (label) {
        Field.Label.OPTIONAL -> ProtoLabel.OPTIONAL
        Field.Label.REQUIRED -> ProtoLabel.REQUIRED
        Field.Label.REPEATED -> ProtoLabel.REPEATED
        Field.Label.ONE_OF, null -> ProtoLabel.NONE
    }

    private fun serviceOf(element: ServiceElement, spans: ProtoSpans) = ProtoService(
        name = element.name,
        place = placeOf(element.location, element.name, element.documentation, spans),
        rpcs = element.rpcs.map { rpcOf(it, spans) },
    )

    private fun rpcOf(element: RpcElement, spans: ProtoSpans) = ProtoRpc(
        name = element.name,
        requestType = element.requestType,
        responseType = element.responseType,
        place = placeOf(element.location, element.name, element.documentation, spans),
    )

    private fun placeOf(location: Location, name: String, documentation: String, spans: ProtoSpans) =
        spans.placeOf(location, name, firstSentence(documentation), false)
}
