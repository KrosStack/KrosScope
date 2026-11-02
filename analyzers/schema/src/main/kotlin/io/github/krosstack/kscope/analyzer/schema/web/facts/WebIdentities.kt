package io.github.krosstack.kscope.analyzer.schema.web.facts

import io.github.krosstack.kscope.analyzer.schema.IdentityFactory
import io.github.krosstack.kscope.analyzer.schema.web.WebKinds
import io.github.krosstack.kscope.model.SymbolDescriptor
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolKind

/**
 * The ids of the symbols of web bundles, in the module and source set that an [IdentityFactory] names: the namespace of
 * the kind, such as `typescript/` or `bundle/`, then the file, and for a package its name. A file has one id whichever
 * producer unit reads it, so that a link from another file can name it.
 */
internal object WebIdentities {
    fun fileId(identity: IdentityFactory, kind: SymbolKind, path: String): SymbolId = identity.idOf(base(kind, path))

    fun packageId(identity: IdentityFactory, path: String, name: String): SymbolId =
        identity.idOf(base(WebKinds.npmPackage, path).term(name))

    private fun base(kind: SymbolKind, path: String): SymbolDescriptor =
        SymbolDescriptor().namespace(kind.id.substringBefore('.')).meta(path)
}
