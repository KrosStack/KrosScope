package io.github.krosstack.kscope.cli

/** An input that cannot be used, such as a task file that is not UTF-8. It ends the command with exit code 1. */
internal class InputFailure(message: String, cause: Throwable? = null) : Exception(message, cause)
