package io.github.krosstack.kscope.cli.text.format

internal enum class Align { LEFT, RIGHT }

/** What a table may do to the cells of a column that does not fit the terminal: nothing, or shorten a path. */
internal enum class Fit { WHOLE, PATH }

/** A column of a [Table]; [header] is shown above the rows when any column has one. */
internal class Column(val header: String = "", val align: Align = Align.LEFT, val fit: Fit = Fit.WHOLE)

/**
 * Rows of cells padded to the widest cell of each column, with two spaces between the columns. Given a terminal
 * [width] it shortens the cells of [Fit.PATH] columns in the middle until the rows fit, never below
 * [MINIMUM_PATH_WIDTH]; without a width nothing is cut. The header is emphasised.
 */
internal class Table(private val columns: List<Column>) {
    private val rows = mutableListOf<List<String>>()

    fun row(vararg cells: String): Table = apply {
        require(cells.size == columns.size) { "A row needs ${columns.size} cells" }
        rows += cells.toList()
    }

    fun render(indent: String = "  ", width: Int? = null, emphasis: Emphasis = Emphasis.NONE): List<String> {
        val withHeader = columns.any { it.header.isNotEmpty() }
        val body = if (width == null) rows else fitted(rows, width - indent.length)
        val all = if (withHeader) listOf(columns.map { it.header }) + body else body
        val widths = columns.indices.map { index -> all.maxOfOrNull { it[index].length } ?: 0 }
        val lines = all.map { cells -> indent + line(cells, widths) }
        return if (withHeader) listOf(emphasis.header(lines.first())) + lines.drop(1) else lines
    }

    private fun fitted(cells: List<List<String>>, room: Int): List<List<String>> {
        val widths = columns.indices.map { index -> maxOf(columns[index].header.length, cells.widest(index)) }
        var overflow = widths.sum() + GAP * (columns.size - 1) - room
        val limits = widths.toMutableList()
        for (index in columns.indices.filter { columns[it].fit == Fit.PATH }.sortedByDescending { widths[it] }) {
            if (overflow <= 0) break
            val limit = maxOf(widths[index] - overflow, MINIMUM_PATH_WIDTH).coerceAtMost(widths[index])
            overflow -= widths[index] - limit
            limits[index] = limit
        }
        return cells.map { row ->
            row.mapIndexed { index, cell ->
                if (columns[index].fit == Fit.PATH) PathShortening.shorten(cell, limits[index]) else cell
            }
        }
    }

    private fun List<List<String>>.widest(index: Int): Int = maxOfOrNull { it[index].length } ?: 0

    private fun line(cells: List<String>, widths: List<Int>): String =
        columns.indices.joinToString("  ") { index -> pad(cells[index], widths[index], columns[index].align) }.trimEnd()

    private fun pad(cell: String, width: Int, align: Align): String =
        if (align == Align.RIGHT) cell.padStart(width) else cell.padEnd(width)

    companion object {
        const val MINIMUM_PATH_WIDTH = 24
        private const val GAP = 2
    }
}
