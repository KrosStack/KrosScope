package sample.tasktide.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class UuidGeneratorTest {
    @Test
    fun generatedIdsAreUnique() {
        val generator = UuidGenerator()

        assertNotEquals(generator.next(), generator.next())
    }

    @Test
    fun generatedTaskIdWrapsGeneratorOutput() {
        val id = TaskId.generate()

        assertEquals(36, id.value.length)
    }

    @Test
    fun desktopPlatformIsNotMobile() {
        assertEquals("desktop", PlatformInfo.name)
    }
}
