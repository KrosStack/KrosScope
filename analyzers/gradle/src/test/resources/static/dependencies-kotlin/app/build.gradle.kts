plugins {
    `java-library`
    alias(libs.plugins.ksp)
}

dependencies {
    implementation(project(":core:model"))
    api(projects.core.modelApi)
    implementation(libs.kotlinx.coroutines.core)
    implementation(platform(libs.androidx.compose.bom))
    implementation("org.slf4j:slf4j-api:2.0.17")
    runtimeOnly("org.slf4j:slf4j-simple")
    testImplementation(kotlin("test"))
    add("compileOnly", "javax.inject:javax.inject:1")
    "annotationProcessor"("com.google.dagger:dagger-compiler:2.51")
    implementation(files("libs/local.jar"))
    implementation(compose.desktop.currentOs)
    implementation(project(":missing"))
    testImplementation(libs.bundles.testing)
    ksp(libs.room.compiler)
}
