package io.github.krosstack.kscope.cli.service.entry

import java.nio.file.Files
import java.nio.file.Path

internal class RegistrationFile(val path: Path) {
    fun <T> importing(commandLine: String, register: () -> T): T {
        try {
            Files.write(path, RegistryValue.registrationFile(commandLine))
            return register()
        } finally {
            delete()
        }
    }

    fun delete() {
        Files.deleteIfExists(path)
    }
}
