package io.github.krosstack.kscope.benchmark.workspace

import io.github.krosstack.kscope.benchmark.gold.GoldAddress

/**
 * A relationship class of the gold: how both of its addresses read as ends, and the producer of the links it is scored
 * on.
 */
internal class LinkClass(val name: String, val producer: LinkProducer, val end: (GoldAddress) -> GoldEnd)
