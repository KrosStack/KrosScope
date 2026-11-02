package sample.tasktide.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import sample.tasktide.core.model.Task
import sample.tasktide.core.model.TaskId
import sample.tasktide.ui.shared.TaskCard

@Composable
fun DesktopApp(module: DesktopModule) {
    val scope = rememberCoroutineScope()
    val tasks by module.repository.observeAll().collectAsState(initial = emptyList())
    var summary by remember { mutableStateOf("") }

    LaunchedEffect(tasks) {
        summary = module.repository.openCountsByPriority().entries
            .joinToString { "${it.value} ${it.key.name.lowercase()}" }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ActionLabel("Add task") {
                scope.launch { module.repository.upsert(Task(TaskId.generate(), "Task ${tasks.size + 1}")) }
            }
            ActionLabel("Sync") { scope.launch { module.syncEngine.sync() } }
            BasicText(summary)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(tasks, key = { it.id.value }) { task ->
                TaskCard(task, onClick = { scope.launch { module.repository.upsert(task.toggled()) } })
            }
        }
    }
}

@Composable
private fun ActionLabel(text: String, onClick: () -> Unit) {
    BasicText(text, modifier = Modifier.clickable(onClick = onClick))
}
