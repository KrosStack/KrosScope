package sample.tasktide.ui.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import sample.tasktide.core.model.Task
import sample.tasktide.core.model.toIsoDate
import sample.tasktide.ui.shared.resources.Res
import sample.tasktide.ui.shared.resources.task_done_label
import sample.tasktide.ui.shared.resources.task_due_label

@Composable
fun TaskCard(task: Task, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF4F4F6))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = task.title,
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None,
                ),
            )
            task.dueAt?.let { dueAt ->
                BasicText(
                    text = stringResource(Res.string.task_due_label, dueAt.toIsoDate()),
                    style = TextStyle(fontSize = 12.sp, color = Color.Gray),
                )
            }
            if (task.done) {
                BasicText(
                    text = stringResource(Res.string.task_done_label),
                    style = TextStyle(fontSize = 12.sp, color = Color.Gray),
                )
            }
        }
        PriorityBadge(task.priority)
    }
}
