package io.github.krosstack.kscope.engine

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IndexTrustTest {
    @Test
    fun `trust is read from the persisted build model provider without running it`() {
        val provider = FakeTrustedProvider()
        val registry = coreRegistry(providers = CoreCapabilities.buildModelProviders() + provider)
        TestWorkspace("gradle-multi").use { workspace ->
            workspace.engine(registry, trusted = true).use { it.update() }
            workspace.engine(registry).use { engine ->
                assertTrue(engine.indexRequiresTrust())
                engine.update()
                assertFalse(engine.indexRequiresTrust())
            }
            kotlin.test.assertEquals(1, provider.calls)
        }
    }
}
