package rel.commonjava

import org.junit.jupiter.api.TestInfo

class Reporter(private val info: TestInfo) {
    fun title(): String = info.displayName

    fun tags(): Set<String> = info.tags
}

fun describe(reporter: Reporter): String = reporter.title() + reporter.tags().size
