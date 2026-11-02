package io.github.krosstack.kscope.analyzer.schema.proto

import io.github.krosstack.kscope.model.SymbolId

/**
 * A message or enum of the workspace as the Kotlin generator names it. [firstConstant] is the default of an enum and
 * [symbol] is the proto symbol of the type, when the file it is in belongs to a module.
 */
internal class ProtoTypeRef(
    val kotlinName: String,
    val isEnum: Boolean,
    val firstConstant: String?,
    val symbol: SymbolId?,
)

/** A parsed proto file with the ids its symbols have, if it is in a module. */
internal class IndexedProto(val file: ProtoFile, val ids: ProtoIdentities?)

/** The messages and enums of all proto files of the workspace by their proto name, for resolving field types. */
internal class ProtoTypeIndex(files: List<IndexedProto>) {
    private val types: Map<String, ProtoTypeRef> = buildMap {
        files.forEach { indexed ->
            val file = indexed.file
            file.types.forEach { putAll(entriesOf(it, emptyList(), file, indexed.ids)) }
        }
    }

    /**
     * Finds the type that [name] stands for in [scope], the package segments followed by the names of the enclosing
     * messages: the innermost scope that has a type of that name wins, as in the proto language.
     */
    fun resolve(name: String, scope: List<String>): ProtoTypeRef? {
        if (name.startsWith('.')) return types[name.removePrefix(".")]
        return (scope.size downTo 0).firstNotNullOfOrNull { size ->
            types[(scope.take(size) + name).joinToString(".")]
        }
    }

    private fun entriesOf(
        type: ProtoType,
        outer: List<String>,
        file: ProtoFile,
        ids: ProtoIdentities?,
    ): Map<String, ProtoTypeRef> {
        val path = outer + type.name
        val protoName = qualify(file.packageName, path)
        val ref = refOf(type, qualify(file.kotlinPackage, path), ids?.typeId(path))
        val nested = (type as? ProtoMessage)?.nested.orEmpty().flatMap { entriesOf(it, path, file, ids).entries }
        return mapOf(protoName to ref) + nested.associate { it.key to it.value }
    }

    private fun refOf(type: ProtoType, kotlinName: String, symbol: SymbolId?): ProtoTypeRef = when (type) {
        is ProtoEnum -> ProtoTypeRef(kotlinName, true, type.constants.firstOrNull()?.name, symbol)
        is ProtoMessage -> ProtoTypeRef(kotlinName, false, null, symbol)
    }

    private fun qualify(prefix: String, path: List<String>) =
        (listOf(prefix).filter { it.isNotEmpty() } + path).joinToString(".")
}
