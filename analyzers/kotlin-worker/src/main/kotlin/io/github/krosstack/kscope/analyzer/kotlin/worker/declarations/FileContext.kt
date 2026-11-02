package io.github.krosstack.kscope.analyzer.kotlin.worker.declarations

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import io.github.krosstack.kscope.analyzer.kotlin.worker.plan.FileOwner
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.SourceLocation
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolDescriptor
import io.github.krosstack.kscope.model.SymbolFlag
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolKind
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.model.Visibility

/** A declaration read from syntax, before it is placed in a file and a source set. Offsets are character offsets. */
internal data class SymbolDraft(
    val kind: SymbolKind,
    val name: String,
    val qualifiedName: String,
    val descriptor: SymbolDescriptor,
    val container: SymbolId?,
    val signature: String,
    val visibility: Visibility,
    val flags: Set<SymbolFlag>,
    val declaration: IntRange,
    val nameRange: IntRange?,
    val documentation: String?,
    val attributes: Map<String, String>,
)

internal fun typeKind(id: String) = SymbolKind(id, SymbolCategory.TYPE)

internal fun callableKind(id: String) = SymbolKind(id, SymbolCategory.CALLABLE)

internal fun variableKind(id: String) = SymbolKind(id, SymbolCategory.VARIABLE)

internal fun IntRange.toOffsets(): Pair<Int, Int> = first to last + 1

internal fun PsiElement.offsets(): IntRange = textRange.startOffset until textRange.endOffset

/** The range of a declaration without its leading documentation comment. */
internal fun declarationOffsets(element: PsiElement): IntRange {
    val start = generateSequence(element.firstChild) { it.nextSibling }
        .firstOrNull { it !is PsiComment && it !is PsiWhiteSpace }?.textRange?.startOffset
    return (start ?: element.textRange.startOffset) until element.textRange.endOffset
}

/** Everything needed to turn drafts of one file into symbols of its source set. */
internal class FileContext(val owner: FileOwner, val file: PsiFile) {
    private val lines = LineIndex(file.text)

    fun idOf(descriptor: SymbolDescriptor): SymbolId = owner.idOf(descriptor)

    fun location(offsets: IntRange): SourceLocation? {
        val artifact = owner.artifact ?: return null
        val (start, end) = offsets.toOffsets()
        return SourceLocation(artifact, lines.range(start, end))
    }

    fun startOfFile(): SourceLocation? = owner.artifact?.let { SourceLocation(it, TextRange(1, 1, 1, 1)) }

    fun symbolOf(draft: SymbolDraft): Symbol = Symbol(
        id = idOf(draft.descriptor),
        kind = draft.kind,
        name = draft.name,
        qualifiedName = draft.qualifiedName,
        signature = draft.signature,
        container = draft.container,
        visibility = draft.visibility,
        flags = draft.flags,
        origin = SymbolOrigin.Source,
        declaration = location(draft.declaration),
        nameLocation = draft.nameRange?.let(::location),
        documentation = draft.documentation,
        scope = Scope(sourceSet = owner.sourceSet),
        evidenceClass = EvidenceClass.STRUCTURAL,
        certainty = Certainty.EXACT,
        attributes = draft.attributes,
    )
}
