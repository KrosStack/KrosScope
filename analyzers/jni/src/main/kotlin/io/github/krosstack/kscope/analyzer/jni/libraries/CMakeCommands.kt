package io.github.krosstack.kscope.analyzer.jni.libraries

private val invocationStart = Regex("[A-Za-z_][A-Za-z0-9_]*\\s*\\(")

/** An argument of a CMake command: its text without quotes, and where it lies in the file. */
internal class CMakeArgument(val value: String, val start: Int, val end: Int)

/** A call of a CMake command: where its name starts, where its closing parenthesis ends, and its arguments. */
internal class CMakeCall(val name: String, val start: Int, val end: Int, val arguments: List<CMakeArgument>)

/**
 * Reads the command calls of CMake text, which stand one after another, so that a call inside a quoted argument of
 * another is not one.
 */
internal class CMakeCommands(text: String) {
    private val code = CMakeComments.blank(text)

    fun calls(command: String): List<CMakeCall> = invocations().filter { it.name.equals(command, ignoreCase = true) }

    private fun invocations(): List<CMakeCall> {
        val calls = mutableListOf<CMakeCall>()
        var index = 0
        while (index < code.length) {
            val match = invocationStart.matchAt(code, index)
            if (match == null) {
                index++
            } else {
                val call = callOf(index, match.value.substringBefore('(').trimEnd(), match.range.last + 1)
                calls += call
                index = call.end
            }
        }
        return calls
    }

    private fun callOf(start: Int, name: String, afterOpening: Int): CMakeCall {
        val arguments = mutableListOf<CMakeArgument>()
        var depth = 0
        var index = afterOpening
        while (index < code.length && !(code[index] == ')' && depth == 0)) {
            when {
                code[index].isWhitespace() -> index++

                code[index] == '(' || code[index] == ')' -> {
                    depth += if (code[index] == '(') 1 else -1
                    index++
                }

                else -> {
                    val argument = argumentAt(index)
                    arguments += argument
                    index = argument.end
                }
            }
        }
        return CMakeCall(name, start, minOf(index + 1, code.length), arguments)
    }

    private fun argumentAt(start: Int): CMakeArgument {
        val bracketOpener = CMakeComments.bracketOpenerLength(code, start)
        return when {
            bracketOpener > 0 -> bracketArgument(start, bracketOpener)
            code[start] == '"' -> quotedArgument(start)
            else -> plainArgument(start)
        }
    }

    private fun bracketArgument(start: Int, openerLength: Int): CMakeArgument {
        val end = CMakeComments.afterBracket(code, start, openerLength)
        val body = code.substring(start + openerLength, end).removeSuffix(CMakeComments.bracketCloser(openerLength))
        return CMakeArgument(body, start, end)
    }

    private fun plainArgument(start: Int): CMakeArgument {
        var end = start
        while (end < code.length && !isArgumentEnd(code[end])) end += if (code[end] == '\\') 2 else 1
        end = minOf(end, code.length)
        return CMakeArgument(code.substring(start, end), start, end)
    }

    private fun quotedArgument(start: Int): CMakeArgument {
        var close = start + 1
        while (close < code.length && code[close] != '"') close += if (code[close] == '\\') 2 else 1
        close = minOf(close, code.length)
        return CMakeArgument(code.substring(start + 1, close), start, minOf(close + 1, code.length))
    }

    private fun isArgumentEnd(character: Char) =
        character.isWhitespace() || character == '(' || character == ')' || character == '"'
}
