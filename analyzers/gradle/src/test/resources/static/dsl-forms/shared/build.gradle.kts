plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    jvm()
    iosArm64()
    iosSimulatorArm64()
    applyDefaultHierarchyTemplate()

    sourceSets {
        val nonWebMain by creating {
            dependsOn(commonMain.get())
        }
        configure(listOf(iosMain, jvmMain)) {
            get().dependsOn(nonWebMain)
        }
    }
}
