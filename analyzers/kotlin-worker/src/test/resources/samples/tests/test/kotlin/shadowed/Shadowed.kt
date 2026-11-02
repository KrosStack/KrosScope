package shadowed

annotation class Test

class Shadowed {
    @Test
    fun notATest() {}
}
