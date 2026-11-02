package io.github.krosstack.kscope.cli.service.entry

import io.github.krosstack.kscope.cli.service.home.ServiceDirectory
import java.nio.file.Path

internal val SYSTEMCTL_RELOAD = listOf("systemctl", "--user", "daemon-reload")

internal val SYSTEMCTL_ENABLE = listOf("systemctl", "--user", "enable", "kscope.service")

internal val SYSTEMCTL_DISABLE = listOf("systemctl", "--user", "disable", "kscope.service")

internal val SYSTEMCTL_IS_ENABLED = listOf("systemctl", "--user", "is-enabled", "kscope.service")

internal const val NO_USER_MANAGER =
    "This session has no systemd user manager; start the service with `kscope service start`."

internal fun unitFile(configHome: Path): Path = configHome.resolve("systemd").resolve("user").resolve("kscope.service")

internal fun systemdEntry(directory: ServiceDirectory, configHome: Path, runner: CommandRunner) =
    SystemdUserEntry(directory, configHome, runner)
