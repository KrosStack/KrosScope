plugins {
    id("tasktide.jvm.library")
    alias(libs.plugins.wire)
}

dependencies {
    api(libs.wire.runtime)
}

wire {
    kotlin {}
}
