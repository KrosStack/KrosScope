plugins {
    id("kscope.kotlin-library")
}

dependencies {
    api(project(":core:model"))
    api(project(":core:spi"))
    implementation(project(":core:store"))

    testImplementation(testFixtures(project(":core:model")))
}

tasks.withType<Test>().configureEach {
    inputs.files(fileTree(rootProject.layout.projectDirectory.dir("fixtures/reference-workspace")) { exclude("**/build/**", "**/.gradle/**", "**/.kscope/**", "**/.kotlin/**") })
        .withPropertyName("referenceWorkspace").withPathSensitivity(PathSensitivity.RELATIVE)
}
