package io.github.krosstack.kscope.cli.command

import com.github.ajalt.clikt.testing.test
import io.github.krosstack.kscope.cli.inWorkspace
import io.github.krosstack.kscope.cli.runKscope
import io.github.krosstack.kscope.service.trust.TrustStore
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrustCommandTest {
    private fun <T> withStore(block: (TrustStore, Path) -> T): T {
        val home = Files.createTempDirectory("kscope-trust-command")
        try {
            return block(TrustStore(home.resolve(TrustStore.FILE_NAME)), home.resolve(TrustStore.FILE_NAME))
        } finally {
            home.toFile().deleteRecursively()
        }
    }

    @Test
    fun `trust records the workspace and says so`() = inWorkspace { root ->
        withStore { store, _ ->
            val result = TrustCommand { store }.test(listOf("--workspace", root.toString()))

            assertEquals(0, result.statusCode)
            assertTrue(result.stdout.startsWith("Trusting "), result.stdout)
            assertTrue(store.isTrusted(root))
        }
    }

    @Test
    fun `trust twice says that the workspace is trusted already`() = inWorkspace { root ->
        withStore { store, _ ->
            TrustCommand { store }.test(listOf("--workspace", root.toString()))

            val again = TrustCommand { store }.test(listOf("--workspace", root.toString()))

            assertEquals(0, again.statusCode)
            assertTrue(again.stdout.contains("is trusted already."), again.stdout)
        }
    }

    @Test
    fun `revoke removes the trust and a second revoke says it was not trusted`() = inWorkspace { root ->
        withStore { store, _ ->
            store.grant(root)

            val revoked = TrustCommand { store }.test(listOf("--revoke", "--workspace", root.toString()))
            val again = TrustCommand { store }.test(listOf("--revoke", "--workspace", root.toString()))

            assertEquals(0, revoked.statusCode)
            assertTrue(revoked.stdout.startsWith("No longer trusting "), revoked.stdout)
            assertFalse(store.isTrusted(root))
            assertEquals(0, again.statusCode)
            assertTrue(again.stdout.contains("was not trusted."), again.stdout)
        }
    }

    @Test
    fun `a trust file that cannot be read is reported and left as it is`() = inWorkspace { root ->
        withStore { store, file ->
            Files.writeString(file, "trusted-workspaces = [\n")

            val result = TrustCommand { store }.test(listOf("--workspace", root.toString()))

            assertEquals(1, result.statusCode)
            assertTrue(result.stderr.startsWith("error: "), result.stderr)
            assertEquals("trusted-workspaces = [\n", Files.readString(file))
        }
    }

    @Test
    fun `trust needs an existing workspace directory`() {
        val result = runKscope("trust", "--workspace", "no/such/directory-for-kscope")

        assertEquals(2, result.statusCode)
    }

    @Test
    fun `trust is offered by the command line and listed in its help`() {
        val help = runKscope("--help")

        assertTrue(help.stdout.contains("trust"), help.stdout)
    }
}
