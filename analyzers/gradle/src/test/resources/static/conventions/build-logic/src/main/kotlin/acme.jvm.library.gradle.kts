plugins {
    id("org.jetbrains.kotlin.jvm")
    `java-library`
}

val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    add("testImplementation", catalog.findLibrary("junit").get())
    testImplementation(kotlin("test"))
}
