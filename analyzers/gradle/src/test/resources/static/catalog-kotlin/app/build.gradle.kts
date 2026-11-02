plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ksp)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.bundles.testing)
    testImplementation(libs.junit)
    ksp(libs.room.compiler)
    implementation(libs.does.not.exist)
}
