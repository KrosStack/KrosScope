package mixed.core

actual fun platformName(): String = JavaSupport.describe("jvm")
