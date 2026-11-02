package sample.tasktide.feature.tasks

import sample.tasktide.core.model.TaskStore

interface TaskStoreProvider {
    val taskStore: TaskStore
}
