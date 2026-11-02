package sample.tasktide.desktop

import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import sample.tasktide.core.model.PlatformInfo

fun main() = application {
    val module = remember { DesktopModule() }
    Window(onCloseRequest = ::exitApplication, title = "TaskTide (${PlatformInfo.name})") {
        DesktopApp(module)
    }
}
