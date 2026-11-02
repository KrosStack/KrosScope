package io.github.krosstack.kscope.service.analysis

import io.github.krosstack.kscope.service.KrosScope
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Three modules that do not depend on each other declare a Java type of the same name. The Kotlin code of the middle
 * one uses its own type, whether the scan analyses all modules or only that one.
 */
class SameJavaNameIntegrationTest {
    private val sample = SampleWorkspace("clash")
    private val binder = "beta/src/main/kotlin/clash/Binder.kt"

    @AfterTest
    fun cleanUp() {
        sample.close()
    }

    @Test
    fun `a module resolves its own Java type in a full scan`() {
        KrosScope.open(sample.root).use { service ->
            service.scan()
            val resolved = service.info().coverage.filter { it.state == "exact" && it.evidence == "compiler" }
            assertTrue(resolved.any { it.relation == "extends" }, "${service.info().coverage}")
        }
    }

    @Test
    fun `an incremental scan of one module matches a scan of all`() {
        KrosScope.open(sample.root).use { service ->
            service.scan()
            sample.write(
                binder,
                "package clash\n\nclass Binder {\n    val binder = object : Contract.Stub() {\n" +
                    "        override fun ping() {\n            println()\n        }\n    }\n}\n",
            )
            val verification = assertNotNull(service.scan(verify = true).verification)
            assertTrue(verification.matches, "${verification.differences}")
            assertEquals(0, verification.differingUnits)
        }
    }
}
