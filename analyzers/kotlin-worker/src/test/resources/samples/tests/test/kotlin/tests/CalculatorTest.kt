package tests

import org.junit.Test
import org.junit.jupiter.api.Nested

class CalculatorTest {
    @Test
    fun adds() {}

    @Test
    fun subtracts() {}

    fun helper() {}
}

class Calculator {
    fun add(a: Int, b: Int): Int = a + b
}

class NestedTests {
    @Nested
    inner class Deep {
        @org.junit.jupiter.api.Test
        fun works() {}
    }
}
