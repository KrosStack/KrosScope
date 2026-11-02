package io.github.krosstack.kscope.cli.service.entry

import java.util.concurrent.CountDownLatch

internal object BlockingMain {
    @JvmStatic
    fun main(arguments: Array<String>) {
        check(arguments.isEmpty()) { "no arguments are expected" }
        CountDownLatch(1).await()
    }
}
