package sample.tasktide.sync.engine

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class ClientVersionWiringTest {
    private val repositoryDirectory = File(".").canonicalFile.parentFile.parentFile
    private val engineConstruction = Regex("SyncEngine\\(.*?clientVersion\\s*=", RegexOption.DOT_MATCHES_ALL)

    private fun constructsEngineWithVersion(path: String): Boolean =
        engineConstruction.containsMatchIn(File(repositoryDirectory, path).readText())

    @Test
    fun theAndroidAppReportsItsVersion() {
        assertTrue(constructsEngineWithVersion("app/src/main/kotlin/sample/tasktide/app/TaskTideApp.kt"))
    }

    @Test
    fun theDesktopAppReportsItsVersion() {
        assertTrue(constructsEngineWithVersion("desktop/src/main/kotlin/sample/tasktide/desktop/DesktopModule.kt"))
    }
}
