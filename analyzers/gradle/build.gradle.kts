plugins {
    id("kscope.kotlin-library")
}

val modelPluginJar = configurations.register("modelPluginJar") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

dependencies {
    api(project(":core:model"))
    api(project(":core:spi"))
    implementation(libs.gradle.tooling.api)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.groovy)
    implementation(libs.tomlj)
    runtimeOnly(libs.slf4j.nop)
    add(modelPluginJar.name, project(":analyzers:gradle-model-plugin"))

    testImplementation(testFixtures(project(":core:model")))
}

tasks.processResources {
    from(modelPluginJar) {
        into("kscope/gradle")
        rename { "gradle-model-plugin.jar" }
    }
}

val integration = providers.gradleProperty("kscope.integration").orNull == "true"

tasks.withType<Test>().configureEach {
    inputs.files(fileTree(rootProject.layout.projectDirectory.dir("fixtures/reference-workspace")) { exclude("**/build/**", "**/.gradle/**", "**/.kscope/**", "**/.kotlin/**") })
        .withPropertyName("referenceWorkspace").withPathSensitivity(PathSensitivity.RELATIVE)
}

tasks.test {
    useJUnitPlatform {
        if (!integration) excludeTags("gradle-integration")
    }
    systemProperty("kscope.fixture", rootProject.layout.projectDirectory.dir("fixtures/reference-workspace").asFile.path)
    systemProperty("kscope.integration.index", layout.buildDirectory.dir("integration-index").get().asFile.path)
}
