package io.github.krosstack.kscope.analyzer.kotlin.linking

import io.github.krosstack.kscope.spi.BoundaryLinker

/**
 * The linkers that read the Kotlin and Java facts and add what the compiler could not state: `expect`/`actual`
 * pairs of source sets it did not bind, alternatives between build variants, the subject of a test and imports that
 * name a unique workspace declaration, in the repository of the reference or in one that it depends on by coordinates.
 */
public object KotlinLinkers {
    public fun linkers(): List<BoundaryLinker> = listOf(
        ExpectActualConventionLinker(),
        VariantAlternativeLinker(),
        TestSubjectLinker(),
        QualifiedNameLinker(),
    )
}
