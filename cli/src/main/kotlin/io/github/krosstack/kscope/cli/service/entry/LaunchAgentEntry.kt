package io.github.krosstack.kscope.cli.service.entry

import io.github.krosstack.kscope.cli.service.home.ServiceDirectory
import io.github.krosstack.kscope.cli.service.launch.ServiceLaunch
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

internal class LaunchAgentEntry(directory: ServiceDirectory, home: Path) : SessionEntry {
    private val file = LaunchFile(directory.launchFile)
    private val agents: Path = home.resolve("Library").resolve("LaunchAgents")
    private val plist: Path = agents.resolve("${LaunchAgentPlist.LABEL}.plist")

    override fun install(launch: ServiceLaunch): Installation = file.installing(launch) {
        val staged = agents.resolve("${LaunchAgentPlist.LABEL}.plist.tmp")
        Files.createDirectories(agents)
        try {
            Files.writeString(staged, LaunchAgentPlist.text(file.commandWords(launch)))
            Files.move(staged, plist, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(staged)
        }
        Installation.Installed
    }

    override fun uninstall(): Removal {
        val installed = Files.exists(plist) || file.exists()
        Files.deleteIfExists(plist)
        file.delete()
        return if (installed) Removal.Removed else Removal.NotInstalled
    }

    override fun state(): EntryState =
        if (Files.exists(plist)) EntryState.Registered(registeredCommand()) else EntryState.NotRegistered

    private fun registeredCommand(): RegisteredCommand? = try {
        LaunchAgentPlist.registeredCommand(Files.readString(plist))
    } catch (_: IOException) {
        null
    }
}
