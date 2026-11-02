plugins {
    `kotlin-dsl`
}

gradlePlugin {
    plugins {
        register("feature") {
            id = "classconv.feature"
            implementationClass = "FeaturePlugin"
        }
    }
}
