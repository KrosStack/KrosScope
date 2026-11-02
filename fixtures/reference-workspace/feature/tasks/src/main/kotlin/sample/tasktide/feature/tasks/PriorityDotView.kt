package sample.tasktide.feature.tasks

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import sample.tasktide.core.model.Priority

class PriorityDotView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    var priority: Priority = Priority.NORMAL
        set(value) {
            field = value
            applyColor()
            invalidate()
        }

    init {
        applyColor()
    }

    override fun onDraw(canvas: Canvas) {
        val radius = minOf(width, height) / 2f
        canvas.drawCircle(width / 2f, height / 2f, radius, paint)
    }

    private fun applyColor() {
        paint.color = ContextCompat.getColor(context, colorRes(priority))
    }

    private fun colorRes(priority: Priority): Int = when (priority) {
        Priority.LOW -> R.color.priority_low
        Priority.NORMAL -> R.color.priority_normal
        Priority.HIGH -> R.color.priority_high
        Priority.URGENT -> R.color.priority_urgent
    }
}
