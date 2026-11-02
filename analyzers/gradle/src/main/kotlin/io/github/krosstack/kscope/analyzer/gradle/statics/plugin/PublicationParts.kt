package io.github.krosstack.kscope.analyzer.gradle.statics.plugin

/**
 * One part of a coordinate as the build writes it: its [text] when it is a string literal and null when computed,
 * the [expression] as written, and whether it comes from a convention plugin and is therefore [heuristic].
 */
internal class Part(val text: String?, val expression: String, val heuristic: Boolean)

/** The parts that one publication sets itself; a part left null is taken from the module. */
internal class Declared(val groupId: Part?, val artifactId: Part?, val version: Part?)
