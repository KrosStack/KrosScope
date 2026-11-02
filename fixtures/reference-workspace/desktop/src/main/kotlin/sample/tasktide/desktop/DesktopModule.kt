package sample.tasktide.desktop

import sample.tasktide.core.model.UuidGenerator
import sample.tasktide.data.db.DriverFactory
import sample.tasktide.data.db.TaskRepository
import sample.tasktide.data.db.createTaskDatabase
import sample.tasktide.sync.engine.FakeSyncTransport
import sample.tasktide.sync.engine.SyncEngine
import java.io.File

class DesktopModule(databaseFile: File = defaultDatabaseFile()) {
    val repository = TaskRepository(createTaskDatabase(DriverFactory(databaseFile)))

    val syncEngine = SyncEngine(repository, FakeSyncTransport(), deviceId = "desktop-${UuidGenerator().next()}")
}

private fun defaultDatabaseFile(): File {
    val directory = File(System.getProperty("user.home"), ".tasktide").apply { mkdirs() }
    return File(directory, "tasks.db")
}
