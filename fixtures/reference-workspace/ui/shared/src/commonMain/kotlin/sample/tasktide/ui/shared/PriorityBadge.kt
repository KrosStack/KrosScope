package sample.tasktide.ui.shared

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import sample.tasktide.core.model.Priority
import sample.tasktide.ui.shared.resources.Res
import sample.tasktide.ui.shared.resources.ic_flag
import sample.tasktide.ui.shared.resources.priority_high
import sample.tasktide.ui.shared.resources.priority_low
import sample.tasktide.ui.shared.resources.priority_normal
import sample.tasktide.ui.shared.resources.priority_urgent

@Composable
fun PriorityBadge(priority: Priority, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(priorityColor(priority))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painter = painterResource(Res.drawable.ic_flag), contentDescription = null, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        BasicText(
            text = stringResource(priorityLabel(priority)),
            style = TextStyle(color = Color.White, fontSize = 12.sp),
        )
    }
}

private fun priorityLabel(priority: Priority): StringResource = when (priority) {
    Priority.LOW -> Res.string.priority_low
    Priority.NORMAL -> Res.string.priority_normal
    Priority.HIGH -> Res.string.priority_high
    Priority.URGENT -> Res.string.priority_urgent
}

internal fun priorityColor(priority: Priority): Color = when (priority) {
    Priority.LOW -> Color(0xFF7CB342)
    Priority.NORMAL -> Color(0xFF1E88E5)
    Priority.HIGH -> Color(0xFFFB8C00)
    Priority.URGENT -> Color(0xFFE53935)
}
