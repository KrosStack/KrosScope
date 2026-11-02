package io.github.krosstack.kscope.analyzer.kotlin.linking

private val testSuffixes = listOf("KtTest", "Tests", "Test", "Spec", "IT")

/** The name conventions that tie a test class to the class it tests: `FooTest`, `FooTests`, `FooSpec`, `FooIT`. */
internal object SubjectNames {
    /** The name of the class under test, or null when [testName] follows none of the conventions. */
    fun of(testName: String): String? {
        val suffix = testSuffixes.firstOrNull { testName.endsWith(it) && testName.length > it.length }
        return suffix?.let { testName.removeSuffix(it) }
    }
}
