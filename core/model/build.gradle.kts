import io.github.krosstack.kscope.conventions.VerifyPackageSize

plugins {
    id("kscope.kotlin-library")
    `java-test-fixtures`
}

dependencies {
    api(libs.kotlinx.serialization.core)
    api(libs.kotlinx.serialization.json)
}

tasks.named<VerifyPackageSize>("verifyPackageSize") {
    directoryLimits.put("src/main/kotlin/io/github/krosstack/kscope/model", 25)
}
