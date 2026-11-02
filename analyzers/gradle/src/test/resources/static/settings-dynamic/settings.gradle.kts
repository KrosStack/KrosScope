rootProject.name = "dynamic"

include(":fixed")
listOf("alpha", "beta").forEach { include(":modules:$it") }
