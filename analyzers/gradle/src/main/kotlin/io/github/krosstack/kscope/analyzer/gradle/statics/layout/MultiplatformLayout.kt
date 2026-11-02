package io.github.krosstack.kscope.analyzer.gradle.statics.layout

import io.github.krosstack.kscope.model.Platform

private const val COMMON_TEST = "commonTest"
private val androidTests = listOf("androidUnitTest", "androidInstrumentedTest", "androidHostTest", "androidDeviceTest")

/**
 * The layout of a Kotlin Multiplatform project: the targets that are declared, the main source sets of the default
 * hierarchy template for them, the test source sets that have a directory, and the source sets that scripts declare
 * or connect with `dependsOn`. A declared source set compiles for the targets of the source sets that depend on it.
 */
internal class MultiplatformLayout(private val directoryExists: (String) -> Boolean) {
    fun plan(targets: List<TargetSpec>, blocks: List<SourceSetBlock>, variants: List<VariantDraft>): Layout {
        val nodes = DefaultHierarchy.plan(targets)
        val android = targets.firstOrNull { it.platform == Platform.ANDROID }?.name
        val mains = nodes.map { node ->
            val membership = if (node.name == "${android}Main") variants.map { it.name } else emptyList()
            SourceSetDraft(node.name, node.parents, node.targets, membership, declared = false)
        }
        val tests = testsWithDirectoryAndTheirAncestors(candidateTests(nodes, android))
        val declared = declaredSets(blocks, (mains + tests).map { it.name }.toSet())
        val edges = blocks.groupBy { it.name }.mapValues { (_, same) -> same.flatMap(SourceSetBlocks::dependsOn) }
        val sourceSets = (mains + tests + declared).map { draft ->
            SourceSetDraft(
                draft.name,
                (draft.dependsOn + edges[draft.name].orEmpty()).distinct(),
                draft.targets,
                draft.variants,
                draft.declared,
            )
        }
        return Layout(targets.map { TargetDraft(it.name, it.platform) }, variants, withDerivedTargets(sourceSets))
    }

    private fun withDerivedTargets(drafts: List<SourceSetDraft>): List<SourceSetDraft> {
        val byName = drafts.associateBy { it.name }
        return drafts.map { draft ->
            if (draft.declared && draft.targets.isEmpty()) {
                val dependents = drafts.filter { dependsOnTransitively(it, draft.name, byName) }
                val targets = dependents.flatMap { it.targets }.distinct()
                SourceSetDraft(draft.name, draft.dependsOn, targets, draft.variants, draft.declared)
            } else {
                draft
            }
        }
    }

    private fun dependsOnTransitively(
        from: SourceSetDraft,
        name: String,
        byName: Map<String, SourceSetDraft>,
    ): Boolean {
        val seen = mutableSetOf(from.name)
        val pending = ArrayDeque(from.dependsOn)
        while (pending.isNotEmpty()) {
            val next = pending.removeFirst()
            if (next == name) return true
            if (seen.add(next)) pending += byName[next]?.dependsOn.orEmpty()
        }
        return false
    }

    private fun candidateTests(nodes: List<HierarchyNode>, android: String?): List<SourceSetDraft> {
        val derived = nodes.map { node ->
            val parents = node.parents.map(::testOf)
            SourceSetDraft(testOf(node.name), parents, node.targets, emptyList(), declared = false)
        }
        val forAndroid = android?.let { name ->
            androidTests.map { SourceSetDraft(it, listOf(COMMON_TEST), listOf(name), emptyList(), declared = false) }
        }.orEmpty()
        return derived.filter { it.name != "${android}Test" } + forAndroid
    }

    private fun testsWithDirectoryAndTheirAncestors(candidates: List<SourceSetDraft>): List<SourceSetDraft> {
        val byName = candidates.associateBy { it.name }
        val emitted = linkedSetOf<String>()
        val pending = ArrayDeque(candidates.filter { directoryExists(it.name) }.map { it.name })
        while (pending.isNotEmpty()) {
            val name = pending.removeFirst()
            if (emitted.add(name)) pending += byName[name]?.dependsOn.orEmpty()
        }
        return candidates.filter { it.name in emitted }
    }

    private fun declaredSets(blocks: List<SourceSetBlock>, known: Set<String>): List<SourceSetDraft> =
        blocks.filter { it.creates && it.name !in known }.map { it.name }.distinct()
            .map { SourceSetDraft(it, emptyList(), emptyList(), emptyList(), declared = true) }

    private fun testOf(mainName: String): String =
        if (mainName.endsWith("Main")) mainName.removeSuffix("Main") + "Test" else mainName
}
