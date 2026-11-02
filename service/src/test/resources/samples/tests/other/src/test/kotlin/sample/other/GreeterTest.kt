package sample.other

import org.junit.Test

class GreeterTest {
    @Test
    fun usesTheGreeterOfTheCore() {
        sample.tests.Greeter().shout("x")
    }
}
