package io.github.krosstack.kscope.engine.structure

import io.github.krosstack.kscope.engine.TemporaryTree
import java.nio.file.Files
import java.nio.file.Path

class HeadFixture {
    val base: Path = Files.createTempDirectory("kscope-head-commit").toRealPath()
    val checkout: Path = base.resolve("checkout")

    fun create() {
        Files.createDirectories(checkout.resolve("lib"))
        Files.writeString(checkout.resolve("lib/A.kt"), "class A\n")
        TestGit.output(checkout, "init", "-q")
        TestGit.output(checkout, "add", "-A")
        TestGit.output(checkout, "commit", "-q", "-m", "first")
    }

    fun delete() {
        TemporaryTree.deleteEventually(base)
    }

    fun head(): String = TestGit.output(checkout, "rev-parse", "HEAD")

    fun branch(): String = TestGit.output(checkout, "symbolic-ref", "HEAD")
}
