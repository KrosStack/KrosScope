plugins {
    `kotlin-dsl`
}

gradlePlugin {
    plugins {
        register("androidLibrary") {
            id = libs.plugins.acme.android.library.asProvider().get().pluginId
            implementationClass = "AndroidLibraryPlugin"
        }
        register("androidLibraryCompose") {
            id = libs.plugins.acme.android.library.compose.get().pluginId
            implementationClass = "AndroidLibraryComposePlugin"
        }
        register("androidApp") {
            id = "acme.android.app"
            implementationClass = "AppPlugin"
        }
        create("feature") {
            id = "acme.feature"
            implementationClass = "com.acme.logic.FeaturePlugin"
        }
        register("lint") {
            id = "acme.lint"
            implementationClass = "LintPlugin"
        }
        register("kmpLibrary") {
            id = "acme.kmp.library"
            implementationClass = "KmpLibraryPlugin"
        }
        register("flavors") {
            id = "acme.flavors"
            implementationClass = "FlavorsPlugin"
        }
        register("dynamic") {
            id = "acme.dynamic"
            implementationClass = "DynamicPlugin"
        }
        register("aggregate") {
            id = "acme.aggregate"
            implementationClass = "AggregatePlugin"
        }
        register("broken") {
            id = "acme.broken"
            implementationClass = "MissingPlugin"
        }
    }
}
