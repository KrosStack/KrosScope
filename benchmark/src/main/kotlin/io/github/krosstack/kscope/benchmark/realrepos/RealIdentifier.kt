package io.github.krosstack.kscope.benchmark.realrepos

internal object RealIdentifier {
    private val PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._:-]*")

    fun isValid(id: String): Boolean = PATTERN.matches(id)
}
