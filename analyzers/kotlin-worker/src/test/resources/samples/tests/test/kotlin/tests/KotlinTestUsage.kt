package tests

import kotlin.test.Test as KotlinTest
import org.junit.jupiter.api.*

class StarImportTest {
    @Test
    fun viaStarImport() {}
}

class AliasedTest {
    @KotlinTest
    fun viaAlias() {}
}

class OtherTest {
    @Test(timeout = 5000)
    fun withArguments() {}
}

class NotATest {
    @Ignore
    fun skipped() {}
}
