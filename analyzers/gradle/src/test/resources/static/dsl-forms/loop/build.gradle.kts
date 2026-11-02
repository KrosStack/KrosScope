plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    jvm()
    iosArm64()

    sourceSets {
        val nonWebMain by creating {
            dependsOn(commonMain.get())
        }
        listOf(iosMain, jvmMain).forEach { it.get().dependsOn(nonWebMain) }
    }
}
