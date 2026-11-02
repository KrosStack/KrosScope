package io.github.krosstack.kscope.analyzer.schema.room

import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.SymbolQuery

private const val ENTITY = "Entity"
private const val DAO = "Dao"
private const val DATABASE = "Database"
private val classKinds = listOf("kotlin.class", "kotlin.interface", "java.class", "java.interface")
private val memberKinds = setOf("kotlin.property", "java.field")

/** The room annotation names that a symbol carries, as the declaration analyzers record them in its attributes. */
internal fun Symbol.hasRoomAnnotation(simpleName: String): Boolean = attributes["annotations"].orEmpty().split(' ')
    .any { it == "androidx.room.$simpleName" || it == simpleName }

/** The classes of the graph that carry the annotations of Room, and the properties of the entities. */
internal class RoomClasses(private val graph: GraphReader) {
    private val classes: List<Symbol> =
        classKinds.flatMap { graph.symbols(SymbolQuery(kindId = it, limit = Int.MAX_VALUE)) }

    val entities: List<Symbol> = classes.filter { it.hasRoomAnnotation(ENTITY) }
    val daos: List<Symbol> = classes.filter { it.hasRoomAnnotation(DAO) }
    val databases: List<Symbol> = classes.filter { it.hasRoomAnnotation(DATABASE) }

    val isEmpty: Boolean get() = entities.isEmpty() && daos.isEmpty() && databases.isEmpty()

    /** The table of an entity: the `tableName` of its annotation, else the name of the class. */
    fun tableOf(entity: Symbol): String = entity.attributes["annotation.Entity.tableName"] ?: entity.name

    fun hasExplicitTable(entity: Symbol): Boolean = "annotation.Entity.tableName" in entity.attributes

    fun membersOf(owner: Symbol): List<Symbol> = graph.symbols(SymbolQuery(container = owner.id, limit = Int.MAX_VALUE))

    fun propertiesOf(entity: Symbol): List<Symbol> = membersOf(entity).filter { it.kind.id in memberKinds }
}
