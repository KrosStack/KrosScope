package io.github.krosstack.kscope.service.settings

/**
 * A group of analyzers that `kscope.toml` can switch off. [key] is the name in the `analyzers` table. The Android
 * resources group covers the resources, the manifest, the `R`, view binding and `BuildConfig` stubs and their linkers;
 * AIDL and the Compose resources are groups of their own. The web bundles group covers the TypeScript and JavaScript
 * sources, the npm packages, the links from the files of a bundle to the sources they were built from, and the links
 * from the paths that Gradle tasks copy from to the package or module that builds the bundle in them.
 */
public enum class AnalyzerKind(public val key: String) {
    ANDROID_RESOURCES("android-resources"),
    AIDL("aidl"),
    SCHEMAS("schemas"),
    NATIVE_BINDINGS("native-bindings"),
    GIT_HISTORY("git-history"),
    COMPOSE_RESOURCES("compose-resources"),
    WEB_BUNDLES("web-bundles"),
}
