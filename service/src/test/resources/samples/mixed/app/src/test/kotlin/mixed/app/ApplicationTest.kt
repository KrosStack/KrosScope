package mixed.app

import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationTest {
    @Test
    fun `greets every name`() {
        assertEquals(2, Application().run(listOf("Ada", "Grace")).size)
    }
}
