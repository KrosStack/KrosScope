package sample.tasktide.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import sample.tasktide.core.legacy.ExportRegistry
import sample.tasktide.core.legacy.LegacyTaskFormatter
import sample.tasktide.core.model.Task
import sample.tasktide.core.model.TaskId
import sample.tasktide.core.model.TaskStore
import sample.tasktide.core.model.toIsoDate

class TaskListViewModel(
    private val store: TaskStore,
    private val formatter: LegacyTaskFormatter = LegacyTaskFormatter(),
) : ViewModel() {
    val state: StateFlow<TaskListUiState> = store.observeAll()
        .map { tasks -> TaskListUiState(items = tasks.map { toItem(it) }, loading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), TaskListUiState())

    fun toggleDone(id: TaskId) {
        viewModelScope.launch {
            store.find(id)?.let { store.upsert(it.toggled()) }
        }
    }

    fun clearCompleted() {
        viewModelScope.launch {
            store.observeAll().first().filter { it.done }.forEach { store.delete(it.id) }
        }
    }

    suspend fun exportCsv(): String? = ExportRegistry.find("csv")?.export(store.observeAll().first())

    private fun toItem(task: Task) = TaskItem(
        id = task.id,
        title = task.title,
        priority = task.priority,
        priorityLabel = formatter.priorityText(task),
        dueDate = task.dueAt?.toIsoDate(),
        done = task.done,
    )

    class Factory(private val store: TaskStore) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = TaskListViewModel(store) as T
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
