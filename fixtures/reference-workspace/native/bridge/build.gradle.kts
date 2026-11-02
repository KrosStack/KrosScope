plugins {
    id("tasktide.android.library")
}

android {
    namespace = "sample.tasktide.nativebridge"
}

dependencies {
    api(project(":core:model"))
}
