package io.github.krosstack.kscope.analyzer.kotlin.linking

import io.github.krosstack.kscope.model.Platform
import io.github.krosstack.kscope.model.SourceSetRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class TestSubjectAcrossModulesTest : TestSubjectBase() {
    @Test
    fun `a test module is bound to the class of a dependency compiled for its platform and to no other`() {
        draft.target(":tests", "jvm", Platform.JVM)
        draft.target(":lib", "jvm", Platform.JVM)
        draft.target(":lib", "ios", Platform.NATIVE)
        val tests = draft.sourceSet(":tests", "test", SetShape(SourceSetRole.UNIT_TEST, targets = listOf("jvm")))
        val common = draft.sourceSet(":lib", "commonMain", SetShape(targets = listOf("jvm", "ios")))
        val ios = draft.sourceSet(":lib", "iosMain", SetShape(targets = listOf("ios")))
        draft.dependency(":tests", ":lib")
        val greeter = draft.type(common, "p.lib.Greeter")
        draft.type(ios, "p.lib.Pinger")
        val greeterTest = draft.type(tests, "p.tests.GreeterTest", testFlag)
        val pingerTest = draft.type(tests, "p.tests.PingerTest", testFlag)

        assertEquals(greeter.node, targetOf(greeterTest))
        assertNull(targetOf(pingerTest))
    }
}
