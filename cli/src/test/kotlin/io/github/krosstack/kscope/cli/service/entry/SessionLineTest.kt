package io.github.krosstack.kscope.cli.service.entry

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionLineTest {
    @Test
    fun `an entry that is not registered says how to register it`() {
        assertEquals(
            "It does not start with your session; `kscope service install` registers it.",
            SessionLine.of(EntryState.NotRegistered),
        )
    }

    @Test
    fun `a registered entry whose files exist says that it starts with the session`() = inTemporaryDirectory { home ->
        val java = Files.createFile(home.resolve("java"))
        val file = Files.writeString(home.resolve("launch.args"), "# nothing\n")

        val line = SessionLine.of(EntryState.Registered(RegisteredCommand(java.toString(), file.toString())))

        assertEquals("It starts with your session.", line)
    }

    @Test
    fun `a registered entry that names a missing file says which one and how to repair it`() =
        inTemporaryDirectory { home ->
            val missing = home.resolve("gone").toString()

            val line = SessionLine.of(EntryState.Registered(RegisteredCommand(missing, missing)))

            assertEquals(
                "It is registered to start with your session, but the Java executable $missing does not exist; " +
                    "run `kscope service install` again.",
                line,
            )
        }

    @Test
    fun `a unit that exists without being enabled is reported as registered but not enabled`() {
        val line = SessionLine.of(EntryState.Disabled("kscope.service"))

        assertEquals(
            "It is registered to start with your session, but the unit kscope.service is not enabled; " +
                "run `kscope service install` again.",
            line,
        )
    }

    @Test
    fun `a registered entry with another command is not claimed to start the service`() {
        val line = SessionLine.of(EntryState.Registered(null))

        assertEquals(
            "It is registered to start with your session, but its command is not the one " +
                "`kscope service install` writes; run `kscope service install` again.",
            line,
        )
    }
}
