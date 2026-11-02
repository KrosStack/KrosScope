package io.github.krosstack.kscope.context.selection.generated

import io.github.krosstack.kscope.context.declaration
import io.github.krosstack.kscope.context.sourceArtifact
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.TextRange
import kotlin.random.Random

internal data class FileShape(val maxBlocks: Int, val maxMembers: Int)

internal fun fileShapeOf(profile: CaseProfile): FileShape =
    if (profile == CaseProfile.DENSE) FileShape(DENSE_BLOCKS, DENSE_MEMBERS) else FileShape(MAX_BLOCKS, MAX_MEMBERS)

internal class SourceFileGenerator(
    private val random: Random,
    loneSurrogates: Boolean,
    private val shape: FileShape = fileShapeOf(CaseProfile.ORDINARY),
) {
    private var surrogateOwed = loneSurrogates
    private val surrogatePercent = if (loneSurrogates) SURROGATE_PERCENT else 0

    fun generate(plan: FilePlan, tag: String): GeneratedFile {
        val body = FileBody()
        repeat(1 + random.nextInt(shape.maxBlocks)) { index ->
            addBlock(body, "${tag}b$index")
            addSeparator(body)
        }
        val text = body.lines.joinToString("\n", postfix = "\n")
        val artifact = sourceArtifact(plan.path, text).copy(
            ref = ArtifactRef(RepositoryId(plan.repository), RepoPath(plan.path)),
            sourceSet = plan.sourceSet,
        )
        return GeneratedFile(artifact, text, symbols(artifact, body.declarations), plan.kind)
    }

    private fun addBlock(body: FileBody, name: String) {
        when (random.nextInt(BLOCK_KINDS)) {
            0 -> oneLiner(body, name)
            1 -> function(body, name)
            else -> typeWithMembers(body, name)
        }
    }

    private fun oneLiner(body: FileBody, name: String) {
        val range = body.add("fun $name(): String = \"${random.pick(LITERALS)}\"")
        body.declarations.add(DeclarationSpec(name, range, signature("fun $name(): String"), null))
    }

    private fun function(body: FileBody, name: String) {
        val open = body.add("fun $name(x: Int): Int {")
        body.add("    return x + ${random.nextInt(LITERAL_BOUND)}")
        val close = body.add("}")
        body.declarations.add(DeclarationSpec(name, span(open, close), signature("fun $name(x: Int): Int"), null))
    }

    private fun typeWithMembers(body: FileBody, name: String) {
        val open = body.add("class $name {")
        val members = (0 until (1 + random.nextInt(shape.maxMembers))).map { index ->
            "${name}m$index" to body.add("    fun ${name}m$index(): Int = $index")
        }
        val close = body.add("}")
        body.declarations.add(DeclarationSpec(name, span(open, close), signature("class $name"), null))
        for ((member, line) in members) {
            val range = TextRange(line.startLine, MEMBER_COLUMN, line.endLine, line.endColumn)
            body.declarations.add(DeclarationSpec(member, range, signature("fun $member(): Int"), name))
        }
    }

    private fun addSeparator(body: FileBody) {
        when (random.nextInt(SEPARATORS)) {
            0 -> Unit

            1 -> body.add("")

            2 -> body.add("// note ${random.nextInt(LITERAL_BOUND)}")

            else -> {
                body.add("")
                body.add("// α🙂 note")
            }
        }
    }

    private fun signature(text: String): String = when {
        surrogateOwed -> text.also { surrogateOwed = false } + random.pick(SURROGATES)
        random.chance(surrogatePercent) -> text + random.pick(SURROGATES)
        else -> text
    }

    private fun symbols(artifact: Artifact, specs: List<DeclarationSpec>): List<Symbol> {
        val declared = specs.map {
            declaration(it.name, artifact, it.range, it.signature).copy(certainty = random.pick(CERTAINTIES))
        }
        val byName = declared.associateBy { it.name }
        return declared.zip(specs) { symbol, spec ->
            symbol.copy(container = spec.container?.let { byName.getValue(it).id })
        }
    }

    private companion object {
        const val BLOCK_KINDS = 3
        const val SEPARATORS = 4
        const val LITERAL_BOUND = 9
        const val MEMBER_COLUMN = 5
        const val SURROGATE_PERCENT = 40
        val LITERALS = listOf("value", "α🙂漢字", "a\\nb", "```", "tick`s")
        val SURROGATES = listOf("\uD83D", "\uDE00")
        val CERTAINTIES = listOf(
            Certainty.EXACT,
            Certainty.EXACT,
            Certainty.EXACT,
            Certainty.EXACT,
            Certainty.PROBABLE,
            Certainty.HEURISTIC,
        )
    }
}

private const val MAX_BLOCKS = 4
private const val MAX_MEMBERS = 2
private const val DENSE_BLOCKS = 8
private const val DENSE_MEMBERS = 4

private class DeclarationSpec(val name: String, val range: TextRange, val signature: String, val container: String?)

private class FileBody {
    val lines = mutableListOf<String>()
    val declarations = mutableListOf<DeclarationSpec>()

    fun add(line: String): TextRange {
        lines.add(line)
        return TextRange(lines.size, 1, lines.size, line.length + 1)
    }
}

private fun span(first: TextRange, last: TextRange): TextRange =
    TextRange(first.startLine, first.startColumn, last.endLine, last.endColumn)
