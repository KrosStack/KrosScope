package io.github.krosstack.kscope.analyzer.schema.proto

import io.github.krosstack.kscope.analyzer.schema.IdentityFactory
import io.github.krosstack.kscope.analyzer.schema.namespaces
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolDescriptor
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolKind

internal object ProtoKinds {
    const val PACKAGE_ATTRIBUTE = "proto.package"

    val file = SymbolKind("proto.file", SymbolCategory.SCHEMA)
    val message = SymbolKind("proto.message", SymbolCategory.SCHEMA)
    val field = SymbolKind("proto.field", SymbolCategory.SCHEMA)
    val enumeration = SymbolKind("proto.enum", SymbolCategory.SCHEMA)
    val enumConstant = SymbolKind("proto.enum-constant", SymbolCategory.SCHEMA)
    val service = SymbolKind("proto.service", SymbolCategory.SCHEMA)
    val rpc = SymbolKind("proto.rpc", SymbolCategory.SCHEMA)
    val oneOf = SymbolKind("proto.oneof", SymbolCategory.SCHEMA)
}

/**
 * The ids of the symbols of a proto file: `proto/<package>/<Type>#<member>.` in the module and source set of the file.
 * A nested type adds a type segment. The Wire synthesizer uses the same ids for the sources of truth it points at.
 */
internal class ProtoIdentities(private val identity: IdentityFactory, private val packageName: String) {
    fun typeId(path: List<String>): SymbolId = identity.idOf(typeDescriptor(path))

    fun memberId(path: List<String>, member: String): SymbolId = identity.idOf(typeDescriptor(path).term(member))

    fun qualifiedName(path: List<String>, member: String? = null): String =
        (listOf(packageName).filter { it.isNotEmpty() } + path + listOfNotNull(member)).joinToString(".")

    private fun typeDescriptor(path: List<String>): SymbolDescriptor =
        path.fold(SymbolDescriptor().namespace(NAMESPACE).namespaces(packageName)) { descriptor, name ->
            descriptor.type(name)
        }

    private companion object {
        const val NAMESPACE = "proto"
    }
}
