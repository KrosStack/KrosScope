package io.github.krosstack.kscope.service.level

import io.github.krosstack.kscope.service.ScanLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LevelPolicyTest {
    private val untrusted = LevelPolicy(trusted = false)
    private val trusted = LevelPolicy(trusted = true)

    private fun LevelPolicy.run(
        explicit: ScanLevel? = null,
        file: ScanLevel? = null,
        index: ScanLevel? = null,
        rebuild: Boolean = false,
    ) = decide(explicit, file, index, rebuild)

    @Test
    fun `a scan with nothing asked for runs at resolved`() {
        assertEquals(LevelDecision.Run(ScanLevel.RESOLVED), untrusted.run())
        assertEquals(LevelDecision.Run(ScanLevel.RESOLVED), untrusted.run(index = ScanLevel.RESOLVED))
    }

    @Test
    fun `a level given on the command line is followed without trust`() {
        assertEquals(LevelDecision.Run(ScanLevel.EXACT), untrusted.run(explicit = ScanLevel.EXACT))
        assertEquals(LevelDecision.Run(ScanLevel.RESOLVED), untrusted.run(explicit = ScanLevel.RESOLVED))
    }

    @Test
    fun `a settings file cannot make a scan exact without stored trust`() {
        val decision = assertIs<LevelDecision.Run>(untrusted.run(file = ScanLevel.EXACT))

        assertEquals(ScanLevel.RESOLVED, decision.level)
        assertEquals(LevelPolicy.EXACT_NEEDS_TRUST, decision.notice)
        assertTrue(decision.notice.orEmpty().contains("kscope scan --level exact"))
    }

    @Test
    fun `a settings file makes a scan exact in a trusted workspace`() {
        assertEquals(LevelDecision.Run(ScanLevel.EXACT), trusted.run(file = ScanLevel.EXACT))
        assertEquals(
            LevelDecision.Run(ScanLevel.EXACT),
            trusted.run(file = ScanLevel.EXACT, index = ScanLevel.RESOLVED),
        )
    }

    @Test
    fun `a plain scan never goes shallower than the index`() {
        assertEquals(
            LevelDecision.Run(ScanLevel.EXACT),
            trusted.run(file = ScanLevel.RESOLVED, index = ScanLevel.EXACT),
        )
        assertEquals(LevelDecision.Run(ScanLevel.EXACT), trusted.run(index = ScanLevel.EXACT))
    }

    @Test
    fun `a plain scan keeps an exact index only where the build is trusted`() {
        val refused = assertIs<LevelDecision.Refuse>(untrusted.run(index = ScanLevel.EXACT))

        assertTrue(refused.message.contains("kscope trust"), refused.message)
        assertTrue(refused.message.contains("kscope scan --level exact"), refused.message)
        assertTrue(refused.message.contains("kscope scan --level resolved --rebuild"), refused.message)
        assertIs<LevelDecision.Refuse>(untrusted.run(file = ScanLevel.EXACT, index = ScanLevel.EXACT))
        assertIs<LevelDecision.Refuse>(untrusted.run(index = ScanLevel.EXACT, rebuild = true))
    }

    @Test
    fun `an explicit shallower level needs a rebuild`() {
        val refused = assertIs<LevelDecision.Refuse>(
            trusted.run(explicit = ScanLevel.RESOLVED, index = ScanLevel.EXACT),
        )

        assertTrue(refused.message.contains("kscope scan --level resolved --rebuild"), refused.message)
        assertEquals(
            LevelDecision.Run(ScanLevel.RESOLVED),
            untrusted.run(explicit = ScanLevel.RESOLVED, index = ScanLevel.EXACT, rebuild = true),
        )
    }

    @Test
    fun `an explicit level that is not shallower than the index needs no rebuild`() {
        val same = untrusted.run(explicit = ScanLevel.EXACT, index = ScanLevel.EXACT)
        val deeper = untrusted.run(explicit = ScanLevel.EXACT, index = ScanLevel.RESOLVED)

        assertEquals(LevelDecision.Run(ScanLevel.EXACT), same)
        assertEquals(LevelDecision.Run(ScanLevel.EXACT), deeper)
        assertNull((deeper as LevelDecision.Run).notice)
    }
}
