package io.github.krosstack.kscope.analyzer.schema.web.facts

import io.github.krosstack.kscope.analyzer.schema.web.WebArtifactTypes
import io.github.krosstack.kscope.analyzer.schema.web.map.SourceMapComments
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.spi.AnalysisUnit
import io.github.krosstack.kscope.spi.ProduceRequest

/**
 * The facts of one module. Hand-written files give the symbols of the packages and of the scripts. The files that a
 * build left among its outputs, which carry the label of an output root, give the symbols of the bundle files and the
 * links to the sources they were built from, and a bundle with an inline source map gives a note. Facts come in the
 * order of the paths of the files, packages before scripts before bundles. A file that no module contains leaves no
 * fact.
 */
internal class UnitFacts(private val unit: AnalysisUnit, request: ProduceRequest) {
    private val view = RepositoryView(request, unit)
    private val packages = PackageFacts(view)
    private val scripts = SourceFileFacts(view)
    private val bundles = BundleFacts(view)

    fun all(): List<Fact> {
        val members = unit.artifacts.filter { view.placementOf(it) != null }.sortedBy { it.ref.path.value }
        val handWritten = members.filter { it.generated == null }
        val outputs = members.filter(view::isOutput)
        val packageFacts = handWritten.filter { it.type == WebArtifactTypes.npmManifest }.flatMap(packages::of)
        val scriptFacts = handWritten.filter { it.type in WebArtifactTypes.scripts }.flatMap(scripts::of)
        val bundleFacts = outputs.filter { it.type == WebArtifactTypes.sourceMap }.flatMap(bundles::of)
        val inlineNotes = outputs.filter { it.type == WebArtifactTypes.javascript }.mapNotNull(::inlineMapNote)
        return (packageFacts + scriptFacts + bundleFacts + inlineNotes).distinct()
    }

    private fun inlineMapNote(script: Artifact): Fact? =
        WebNotes.inlineSourceMap(script).takeIf { view.textOf(script)?.let(SourceMapComments::isInline) == true }
}
