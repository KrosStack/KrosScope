package io.github.krosstack.kscope.engine.build.archive

/** What the class list of a built JAR says about the module that is meant to have built it. */
internal enum class ContentState(val value: String?) {
    MATCHED("matched"),
    DIFFERS("differs"),
    ABSENT(null),
}
