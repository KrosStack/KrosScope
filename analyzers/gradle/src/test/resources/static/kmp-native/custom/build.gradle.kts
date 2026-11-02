plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    jvm()
    iosArm64()
    iosX64()

    applyHierarchyTemplate(KotlinHierarchyTemplate.default) {
        group("ios")
    }
}
