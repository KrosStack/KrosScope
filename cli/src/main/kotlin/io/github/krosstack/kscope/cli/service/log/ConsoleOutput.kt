package io.github.krosstack.kscope.cli.service.log

internal class ConsoleOutput(private val write: (String) -> Unit) : ServiceOutput {
    override fun begin() = Unit

    @Synchronized
    override fun line(text: String) = write(text)
}
