package io.github.krosstack.kscope.cli.service.entry

import io.github.krosstack.kscope.cli.service.home.ServiceDirectory
import io.github.krosstack.kscope.cli.service.launch.ServiceLaunch
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

internal class SystemdUserEntry(directory: ServiceDirectory, configHome: Path, private val runner: CommandRunner) :
    SessionEntry {
    private val file = LaunchFile(directory.launchFile)
    private val unit: Path = configHome.resolve("systemd").resolve("user").resolve(UNIT)

    override fun install(launch: ServiceLaunch): Installation = file.installing(launch) { enable(launch) }

    override fun uninstall(): Removal = when {
        Files.exists(unit) -> disableAndDelete()

        file.exists() -> {
            file.delete()
            Removal.Removed
        }

        else -> Removal.NotInstalled
    }

    override fun state(): EntryState = when {
        !Files.exists(unit) -> EntryState.NotRegistered
        runner.run(IS_ENABLED).succeeded -> EntryState.Registered(registeredCommand())
        else -> EntryState.Disabled(UNIT)
    }

    private fun enable(launch: ServiceLaunch): Installation {
        val previous = if (Files.exists(unit)) Files.readAllBytes(unit) else null
        Files.createDirectories(unit.parent)
        Files.writeString(unit, SystemdUnit.text(file.commandWords(launch)))
        val failure = failureOf(listOf(RELOAD, ENABLE))
        if (failure != null) restore(previous)
        return if (failure == null) Installation.Installed else Installation.Refused("$NO_MANAGER\n$failure")
    }

    private fun restore(previous: ByteArray?) {
        if (previous == null) Files.deleteIfExists(unit) else Files.write(unit, previous)
    }

    private fun disableAndDelete(): Removal {
        val refusal = failureOf(listOf(DISABLE))
        if (refusal != null) return Removal.Refused(refusal)
        Files.delete(unit)
        file.delete()
        return failureOf(listOf(RELOAD))?.let { Removal.Refused(it) } ?: Removal.Removed
    }

    private fun failureOf(commands: List<List<String>>): String? = commands.firstNotNullOfOrNull { command ->
        runner.run(command).takeIf { !it.succeeded }?.failureOf(command)
    }

    private fun registeredCommand(): RegisteredCommand? = try {
        SystemdUnit.registeredCommand(Files.readString(unit))
    } catch (_: IOException) {
        null
    }

    private companion object {
        const val UNIT = "kscope.service"
        const val NO_MANAGER = "This session has no systemd user manager; start the service with " +
            "`kscope service start`."
        val RELOAD = listOf("systemctl", "--user", "daemon-reload")
        val ENABLE = listOf("systemctl", "--user", "enable", UNIT)
        val DISABLE = listOf("systemctl", "--user", "disable", UNIT)
        val IS_ENABLED = listOf("systemctl", "--user", "is-enabled", UNIT)
    }
}
