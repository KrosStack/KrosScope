plugins {
    id("tasktide.kmp.library")
    alias(libs.plugins.sqldelight)
}

kotlin {
    android {
        namespace = "sample.tasktide.data.db"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            api(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
        }
        named("desktopMain") {
            dependencies {
                implementation(libs.sqldelight.sqlite.driver)
            }
        }
    }
}

sqldelight {
    databases {
        create("TaskDatabase") {
            packageName.set("sample.tasktide.data.db")
        }
    }
}
