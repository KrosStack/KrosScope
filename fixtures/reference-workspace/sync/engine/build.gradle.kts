plugins {
    id("tasktide.jvm.library")
}

dependencies {
    api(project(":core:model"))
    api(project(":data:db"))
    api(project(":sync:protocol"))
}
