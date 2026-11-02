package io.github.krosstack.kscope.analyzer.schema

import io.github.krosstack.kscope.analyzer.schema.sqldelight.sql.Bind
import io.github.krosstack.kscope.analyzer.schema.sqldelight.sql.BindUse
import io.github.krosstack.kscope.analyzer.schema.sqldelight.sql.SqlBinds
import kotlin.test.Test
import kotlin.test.assertEquals

class SqlBindsTest {
    private fun Bind.described(): String {
        val context = context?.let { "${it.use} ${it.qualifier.orEmpty()}.${it.column.orEmpty()}" }
        return "$key $context"
    }

    @Test
    fun `positional and named arguments are told apart and a name used twice is one argument`() {
        val binds = SqlBinds.of("SELECT * FROM t WHERE a = ? AND b = :name AND c = :name AND d = ?")
        assertEquals(listOf("?1", "name", "?2"), binds.map { it.key })
        assertEquals(listOf(null, "name", null), binds.map { it.name })
    }

    @Test
    fun `numbered arguments keep their numbers`() {
        assertEquals(listOf("?2", "?1"), SqlBinds.of("SELECT ?2, ?1").map { it.key })
    }

    @Test
    fun `casts, strings and comments are not arguments`() {
        val binds = SqlBinds.of("SELECT a::text, ':x', '?' FROM t -- :y ?\n WHERE b = :z /* ? */")
        assertEquals(listOf("z"), binds.map { it.key })
    }

    @Test
    fun `the column an argument is compared with is its context`() {
        val sql = "SELECT * FROM t JOIN u WHERE t.a = ? AND b LIKE ? AND c IN ? AND d >= ? AND e IS NOT ?"
        val described = SqlBinds.of(sql).map { it.described() }
        assertEquals(
            listOf(
                "?1 VALUE t.a",
                "?2 VALUE .b",
                "?3 COLLECTION .c",
                "?4 VALUE .d",
                "?5 VALUE .e",
            ),
            described,
        )
    }

    @Test
    fun `between takes the column for both of its arguments and limits are counts`() {
        val binds = SqlBinds.of("SELECT * FROM t WHERE x BETWEEN ? AND ? LIMIT ? OFFSET :skip")
        assertEquals(listOf(BindUse.VALUE, BindUse.VALUE, BindUse.COUNT, BindUse.COUNT), binds.map { it.context?.use })
        assertEquals(listOf("x", "x"), binds.take(2).map { it.context?.column })
    }

    @Test
    fun `a whole row is an argument after values, and an argument inside an expression has no column`() {
        assertEquals(BindUse.ROW, SqlBinds.of("INSERT INTO t VALUES ?").single().context?.use)
        assertEquals(null, SqlBinds.of("SELECT * FROM t WHERE lower(a) = ?").single().context)
    }

    @Test
    fun `an anonymous argument takes the slot after the largest number written before it`() {
        assertEquals(listOf("?1", "?2"), SqlBinds.of("SELECT * FROM t WHERE id = ?1 AND name = ?").map { it.key })
        assertEquals(listOf("?5", "?6", "?7"), SqlBinds.of("SELECT ?5, ?, ?").map { it.key })
    }
}
