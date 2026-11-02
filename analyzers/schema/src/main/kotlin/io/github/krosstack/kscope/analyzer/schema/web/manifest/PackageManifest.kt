package io.github.krosstack.kscope.analyzer.schema.web.manifest

import io.github.krosstack.kscope.analyzer.schema.document.DocScalar
import io.github.krosstack.kscope.analyzer.schema.web.Bundler

/**
 * What a `package.json` says that matters here: the [name] with where it is written, the [version], and the bundler
 * with a default directory, if any, that its dependencies or scripts name.
 */
internal class PackageManifest(val name: DocScalar?, val version: String?, val namedBundler: Bundler?)
