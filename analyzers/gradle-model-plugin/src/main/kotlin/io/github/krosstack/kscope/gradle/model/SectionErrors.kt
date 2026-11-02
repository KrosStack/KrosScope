package io.github.krosstack.kscope.gradle.model

import java.util.TreeMap

/** Collects the failures of model sections so that one broken section never fails the whole model. */
internal class SectionErrors {
    private val messages = TreeMap<String, MutableList<String>>()

    /**
     * Any exception, and any linkage error caused by a renamed plugin API, is recorded and becomes a null result:
     * the models of third-party plugins change without notice and must never fail the fetch.
     */
    @Suppress("TooGenericExceptionCaught")
    fun <T> guard(section: String, detail: String? = null, body: () -> T): T? = try {
        body()
    } catch (failure: Exception) {
        record(section, detail, failure)
        null
    } catch (failure: LinkageError) {
        record(section, detail, failure)
        null
    }

    fun asMap(): Map<String, List<String>> = messages.mapValues { it.value.toList() }

    private fun record(section: String, detail: String?, failure: Throwable) {
        val prefix = detail?.let { "$it: " }.orEmpty()
        messages.getOrPut(section) { mutableListOf() } += "$prefix${failure.javaClass.name}: ${failure.message}"
    }
}
