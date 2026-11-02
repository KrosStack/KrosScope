package io.github.krosstack.kscope.engine.pipeline

import io.github.krosstack.kscope.engine.build.BuildModelCodec
import io.github.krosstack.kscope.engine.build.archive.JvmLibraryResolution
import io.github.krosstack.kscope.engine.build.archive.folders.ArtifactFolders
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.ProducerRef

/**
 * Links the modules of a workspace of several repositories by the JAR and AAR files they depend on and build, and
 * stores the links. Every link to a module is added to the model that analysis sees, so that references into the
 * module that builds a JAR resolve; a workspace of one repository has no such links and no such unit. The folders that
 * the user registered, and the archives read from them, are inputs of the unit.
 */
internal class JvmLibraryStage(private val context: ScanContext, private val session: UpdateSession) {
    fun run(resolved: ResolvedBuildModel, artifacts: List<Artifact>): ResolvedBuildModel {
        if (context.repositories.size < 2) return resolved
        val folders = ArtifactFolders(context.artifactFolders)
        val links = JvmLibraryResolution(context.checkouts, resolved.model, artifacts, folders).resolve()
        WorkspaceFacts(session).store(producer, links.relationships.map { Fact.OfRelationship(it) }, links.inputs)
        val model = resolved.model.copy(dependencies = resolved.model.dependencies + links.resolved())
        return ResolvedBuildModel(BuildModelCodec.canonical(model), resolved.classpaths)
    }

    companion object {
        val producer = ProducerRef("kscope.engine.jvm-libraries", "1")
    }
}
