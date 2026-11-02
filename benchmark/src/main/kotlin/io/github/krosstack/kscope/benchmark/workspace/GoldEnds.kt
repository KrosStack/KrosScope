package io.github.krosstack.kscope.benchmark.workspace

import io.github.krosstack.kscope.benchmark.gold.GoldAddress

/** Reads a gold address as an end. Every address of this gold names a file, which starts with its repository. */
internal object GoldEnds {
    fun inFile(address: GoldAddress): GoldEnd =
        GoldEnd.InFile(requireFile(address), (address.lines + listOfNotNull(address.line)).toSet())

    fun inModule(address: GoldAddress): GoldEnd =
        GoldEnd.InModule(requireFile(address).substringBefore('/'), requireNotNull(address.module) { address })

    private fun requireFile(address: GoldAddress): String = requireNotNull(address.file) { address }
}
