package io.github.krosstack.kscope.benchmark

internal object ScopedSystemProperties {
    fun <T> with(values: Map<String, String?>, action: () -> T): T {
        val previous = values.keys.associateWith { System.getProperty(it) }
        try {
            values.forEach { (name, value) -> set(name, value) }
            return action()
        } finally {
            previous.forEach { (name, value) -> set(name, value) }
        }
    }

    private fun set(name: String, value: String?) {
        if (value == null) System.clearProperty(name) else System.setProperty(name, value)
    }
}
