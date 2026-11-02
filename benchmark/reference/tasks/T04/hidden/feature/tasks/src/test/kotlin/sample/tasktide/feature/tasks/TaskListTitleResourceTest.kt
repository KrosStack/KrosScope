package sample.tasktide.feature.tasks

import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TaskListTitleResourceTest {
    private val moduleDirectory = File(".").canonicalFile
    private val repositoryDirectory = moduleDirectory.parentFile.parentFile
    private val defaultStrings = File(moduleDirectory, "src/main/res/values/strings.xml")
    private val germanStrings = File(moduleDirectory, "src/main/res/values-de/strings.xml")

    private val stringEntry = Regex("<string name=\"([^\"]+)\"[^>]*>([^<]*)</string>")

    private fun entries(file: File): Map<String, String> =
        stringEntry.findAll(file.readText()).associate { it.groupValues[1] to it.groupValues[2] }

    private fun newName(): String {
        val added = entries(defaultStrings).keys - ORIGINAL_NAMES
        return added.single()
    }

    private fun sourceFiles(): List<File> = repositoryDirectory.walkTopDown()
        .onEnter { it.name != "build" && it.name != ".gradle" && it.name != ".git" && it.name != ".kotlin" }
        .filter { it.isFile && it.extension in SOURCE_EXTENSIONS && "src" in it.path.split(File.separatorChar) }
        .toList()

    @Test
    fun theOldNameIsNoLongerDefined() {
        assertFalse(OLD_NAME in entries(defaultStrings))
        assertFalse(OLD_NAME in entries(germanStrings))
    }

    @Test
    fun exactlyOneNewNameSaysItIsTheListTitle() {
        val name = newName()

        assertTrue("list" in name.lowercase() && "title" in name.lowercase(), name)
        assertEquals(ORIGINAL_NAMES.size, entries(defaultStrings).size)
    }

    @Test
    fun englishAndGermanTextsAreKept() {
        val name = newName()

        assertEquals("Tasks", entries(defaultStrings)[name])
        assertEquals("Aufgaben", entries(germanStrings)[name])
    }

    @Test
    fun germanStillTranslatesTheSameStrings() {
        val expected = setOf(newName(), "tasks_empty_title", "action_mark_done")

        assertEquals(expected, entries(germanStrings).keys)
    }

    @Test
    fun nothingRefersToTheOldNameAnymore() {
        val reference = Regex("(?<![A-Za-z0-9_])" + OLD_NAME + "(?![A-Za-z0-9_])")

        val offenders = sourceFiles().filter { reference.containsMatchIn(it.readText()) }

        assertEquals(emptyList<String>(), offenders.map { it.name })
    }

    @Test
    fun theNavigationLabelAndTheScreenTitleUseTheNewName() {
        val name = newName()
        val navigation = File(moduleDirectory, "src/main/res/navigation/nav_tasks.xml").readText()
        val fragment = File(moduleDirectory, "src/main/kotlin/sample/tasktide/feature/tasks/TaskListFragment.kt")

        assertTrue("@string/$name" in navigation)
        assertTrue("R.string.$name" in fragment.readText())
    }

    @Test
    fun everyStringReferenceOfTheModuleIsDefined() {
        val defined = entries(defaultStrings).keys
        val references = Regex("(?:@string/|R\\.string\\.)([A-Za-z0-9_]+)")
        val used = sourceFiles().filter { it.path.startsWith(moduleDirectory.path) && "src${File.separator}main" in it.path }
            .flatMap { file -> references.findAll(file.readText()).map { it.groupValues[1] }.toList() }
            .toSet()

        assertEquals(emptySet<String>(), used - defined)
    }

    private companion object {
        val OLD_NAME = "tasks" + "_title"
        val SOURCE_EXTENSIONS = setOf("kt", "java", "xml", "kts")
        val ORIGINAL_NAMES = setOf(
            OLD_NAME,
            "task_detail_title",
            "tasks_empty_title",
            "tasks_empty_hint",
            "task_priority_format",
            "task_due_format",
            "action_mark_done",
            "action_delete_done",
            "action_export",
        )
    }
}
