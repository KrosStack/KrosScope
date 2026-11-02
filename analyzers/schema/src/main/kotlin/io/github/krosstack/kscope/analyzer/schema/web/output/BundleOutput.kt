package io.github.krosstack.kscope.analyzer.schema.web.output

import io.github.krosstack.kscope.analyzer.schema.web.Bundler

/**
 * The directory, a repository path, that [bundler] writes the bundle of a package to, and whether the config of the
 * bundler says so or the default of the bundler applies.
 */
internal data class BundleOutput(val bundler: Bundler, val directory: String, val isDeclared: Boolean)
