package sample.tasktide.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import sample.tasktide.core.model.TaskStore
import sample.tasktide.ui.shared.TaskCard

@Composable
fun NextUpCard(store: TaskStore, modifier: Modifier = Modifier) {
    val tasks by remember(store) { store.observeAll() }.collectAsState(initial = emptyList())
    val next = tasks.firstOrNull { !it.done }
    if (next != null) TaskCard(next, modifier)
}
