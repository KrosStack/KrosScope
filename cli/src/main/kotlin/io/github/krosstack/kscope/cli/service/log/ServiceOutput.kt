package io.github.krosstack.kscope.cli.service.log

internal interface ServiceOutput {
    fun begin()

    fun line(text: String)
}
