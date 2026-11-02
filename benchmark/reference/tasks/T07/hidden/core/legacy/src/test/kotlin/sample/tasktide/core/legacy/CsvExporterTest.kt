package sample.tasktide.core.legacy

import sample.tasktide.core.model.Priority
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CsvExporterTest {
    private val exporter = CsvExporter()

    @Test
    fun outputStartsWithHeader() {
        val csv = exporter.export(emptyList())

        assertEquals("title,priority,due,slug\n", csv)
    }

    @Test
    fun rowContainsFormattedColumns() {
        val task = TaskFixtures.task("Pay rent", Priority.HIGH, 86_400_000L)

        val lines = exporter.export(listOf(task)).lines()

        assertEquals("Pay rent,high,1970-01-02,pay-rent", lines[1])
    }

    @Test
    fun titlesWithCommasAreQuoted() {
        val task = TaskFixtures.task("Eggs, milk", Priority.LOW, 0L)

        assertTrue(exporter.export(listOf(task)).contains("\"Eggs, milk\",low"))
    }

    @Test
    fun registryResolvesKnownFormats() {
        assertNotNull(ExportRegistry.find("csv"))
        assertEquals(setOf("csv", "json", "overdue"), ExportRegistry.formats())
    }

    @Test
    fun taskWithoutDueDateHasEmptyDueColumn() {
        val task = TaskFixtures.task("Pay rent", Priority.HIGH)

        val lines = exporter.export(listOf(task)).lines()

        assertEquals("Pay rent,high,,pay-rent", lines[1])
    }

    @Test
    fun taskDueAtTheEpochStillShowsItsDate() {
        val task = TaskFixtures.task("Pay rent", Priority.HIGH, 0L)

        val lines = exporter.export(listOf(task)).lines()

        assertEquals("Pay rent,high,1970-01-01,pay-rent", lines[1])
    }

    @Test
    fun rowsWithAndWithoutDueDateKeepTheirOtherColumns() {
        val tasks = listOf(
            TaskFixtures.task("Eggs, milk", Priority.LOW),
            TaskFixtures.task("Call the Bank", Priority.URGENT, 1_700_000_000_000L),
        )

        val lines = exporter.export(tasks).lines()

        assertEquals("\"Eggs, milk\",low,,eggs-milk", lines[1])
        assertEquals("Call the Bank,urgent,2023-11-14,call-the-bank", lines[2])
    }

    @Test
    fun overdueExportIgnoresTasksWithoutDueDate() {
        val task = TaskFixtures.task("Pay rent", Priority.HIGH)

        assertEquals("", ExportRegistry.find("overdue")?.export(listOf(task)))
    }
}
