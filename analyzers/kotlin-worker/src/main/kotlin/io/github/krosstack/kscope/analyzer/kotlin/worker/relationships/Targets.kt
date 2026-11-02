package io.github.krosstack.kscope.analyzer.kotlin.worker.relationships

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.SymbolId

/** Whether a name is read, written or neither; only variables carry it into the relationship as a sub-kind. */
internal enum class Access(val subKind: String?) {
    READ("read"),
    WRITE("write"),
    NONE(null),
}

/**
 * What a reference points at once it is mapped to the symbol model. [subKind] names a special way of reaching the
 * target (`constructor` for an implicit constructor, `generated` for a compiler-made member). Declarations that have
 * no symbol of their own are mapped to the nearest one that has, or not at all.
 */
internal sealed interface Target {
    val node: NodeRef
    val isInterface: Boolean

    /** [isAccessorCall] marks a property access that the language carries out as a call of an accessor method. */
    data class Workspace(
        val id: SymbolId,
        val subKind: String? = null,
        override val isInterface: Boolean = false,
        val isAccessorCall: Boolean = false,
    ) : Target {
        override val node: NodeRef get() = NodeRef.OfSymbol(id)
    }

    data class Library(val id: SymbolId, override val isInterface: Boolean = false) : Target {
        override val node: NodeRef get() = NodeRef.OfSymbol(id)
    }

    /** The class that Kotlin makes for the top-level declarations of a file; the file stands for it. */
    data class FileFacade(val file: ArtifactRef) : Target {
        override val node: NodeRef get() = NodeRef.OfArtifact(file)
        override val isInterface: Boolean get() = false
    }
}

/** What the compiler answered for one reference; the symbols are whatever the language front end resolves to. */
internal sealed interface Attempt<out S> {
    /** The reference resolved; a reference to an overloaded name may resolve to several symbols. */
    data class Resolved<S>(val symbols: List<S>) : Attempt<S>

    /** The compiler could not resolve the reference and offers [candidates], possibly none. */
    data class Erroneous<S>(val candidates: List<S>) : Attempt<S>

    /** There is nothing to report: the compiler failed on the reference or it is not a reference at all. */
    data object Unavailable : Attempt<Nothing>
}

/**
 * The relationship a kind of reference produces. A supertype is an `EXTENDS` edge to a class and an `IMPLEMENTS` edge
 * to an interface, so the kind may depend on the target; [kindIfUnknown] covers a supertype that did not resolve.
 */
internal data class Shape(
    val kindForClass: RelationKind,
    val kindForInterface: RelationKind,
    val kindIfUnknown: RelationKind,
    val subKind: String? = null,
    val allowsLibrary: Boolean = false,
) {
    fun kindFor(target: Target?): RelationKind = when {
        target is Target.Workspace && target.isAccessorCall -> RelationKind.CALLS
        target == null -> kindIfUnknown
        target.isInterface -> kindForInterface
        else -> kindForClass
    }

    companion object {
        fun of(kind: RelationKind, subKind: String? = null, allowsLibrary: Boolean = false) =
            Shape(kind, kind, kind, subKind, allowsLibrary)

        val call = of(RelationKind.CALLS)
        val typeUse = of(RelationKind.TYPE_USES)
        val reference = of(RelationKind.REFERENCES)
        val override = of(RelationKind.OVERRIDES, allowsLibrary = true)
        val actualizes = of(RelationKind.ACTUALIZES)
        val imports = of(RelationKind.IMPORTS)

        fun supertype(declaringIsInterface: Boolean, hasConstructorCall: Boolean, anonymous: Boolean): Shape {
            val isExtending = declaringIsInterface || hasConstructorCall
            val unknown = if (isExtending) RelationKind.EXTENDS else RelationKind.IMPLEMENTS
            val forInterface = if (declaringIsInterface) RelationKind.EXTENDS else RelationKind.IMPLEMENTS
            return Shape(RelationKind.EXTENDS, forInterface, unknown, "anonymous".takeIf { anonymous }, true)
        }
    }
}
