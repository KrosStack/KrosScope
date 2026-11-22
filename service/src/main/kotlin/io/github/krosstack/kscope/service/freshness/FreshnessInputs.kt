package io.github.krosstack.kscope.service.freshness

public class FreshnessInputs internal constructor(
    private val checkouts: List<Checkout>,
    private val scan: ScanTimes?,
    private val limitMillis: Long,
) {
    public fun check(): IndexFreshness = FreshnessCheck(limitMillis).of(checkouts, scan)
}
