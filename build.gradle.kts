plugins {
    base
}

tasks.check {
    dependsOn(gradle.includedBuild("build-logic").task(":check"))
}
