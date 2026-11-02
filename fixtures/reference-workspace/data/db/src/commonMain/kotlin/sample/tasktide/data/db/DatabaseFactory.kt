package sample.tasktide.data.db

fun createTaskDatabase(factory: DriverFactory): TaskDatabase = TaskDatabase(factory.createDriver())
