package io.github.krosstack.kscope.service.semantic

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OptionalProcessBudgetTest {
    private var clock = System.currentTimeMillis()

    private fun budget(directory: Path, alive: (Long) -> Boolean = { true }) =
        OptionalProcessBudget(directory, { clock }, { ALLOWANCE }, alive)

    @Test
    fun `simultaneous reservations cannot overcommit`(@TempDir directory: Path) {
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(4)
        try {
            val results = List(4) {
                pool.submit<OptionalProcessBudget.Reservation> {
                    start.await()
                    budget(directory).reserve()
                }
            }
            start.countDown()
            val granted = results.map { it.get() }.filterIsInstance<OptionalProcessBudget.Reservation.Granted>()
            assertEquals(1, granted.size)
            granted.forEach { it.reservation.close() }
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `stale and closed reservations release allowance`(@TempDir directory: Path) {
        val first = assertIs<OptionalProcessBudget.Reservation.Granted>(budget(directory).reserve())
        assertIs<OptionalProcessBudget.Reservation.Refused>(budget(directory).reserve())
        first.reservation.close()
        val second = assertIs<OptionalProcessBudget.Reservation.Granted>(budget(directory).reserve())
        clock += 2 * ProcessReservation.STALE_MILLIS
        val third = assertIs<OptionalProcessBudget.Reservation.Granted>(budget(directory).reserve())
        second.reservation.close()
        third.reservation.close()
    }

    @Test
    fun `a dead owner releases its share`(@TempDir directory: Path) {
        val held = assertIs<OptionalProcessBudget.Reservation.Granted>(budget(directory).reserve())
        assertIs<OptionalProcessBudget.Reservation.Granted>(budget(directory) { false }.reserve())
        held.reservation.close()
    }

    private fun release(directory: Path) {
        Files.list(directory).use { files -> files.filter { it.toString().endsWith(".reservation") }.toList() }
            .forEach(Files::deleteIfExists)
    }

    @Test
    fun `a reservation file that vanishes while it is read counts as released`(@TempDir directory: Path) {
        val held = assertIs<OptionalProcessBudget.Reservation.Granted>(budget(directory).reserve())
        val vanishing = budget(directory) {
            release(directory)
            true
        }
        assertIs<OptionalProcessBudget.Reservation.Granted>(vanishing.reserve())
        held.reservation.close()
    }

    @Test
    fun `a renewal after another process released the share reports it lost`(@TempDir directory: Path) {
        val held = assertIs<OptionalProcessBudget.Reservation.Granted>(budget(directory).reserve())
        assertNull(held.reservation.lost())
        release(directory)
        held.reservation.renew()
        assertNotNull(held.reservation.lost())
        held.reservation.close()
    }

    private companion object {
        const val ALLOWANCE = 3000L
    }
}
