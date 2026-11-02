package io.github.krosstack.kscope.cli.text

import io.github.krosstack.kscope.cli.text.format.DetailLevel
import io.github.krosstack.kscope.cli.text.format.Emphasis
import io.github.krosstack.kscope.cli.text.format.Readings
import io.github.krosstack.kscope.cli.text.format.TextContext
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.retrieval.AnalysisResult
import io.github.krosstack.kscope.retrieval.AnalyzedQuery
import io.github.krosstack.kscope.retrieval.ChangeScope
import io.github.krosstack.kscope.retrieval.FileRole
import io.github.krosstack.kscope.retrieval.RankedFile
import io.github.krosstack.kscope.retrieval.SignalContribution
import io.github.krosstack.kscope.retrieval.SignalKind
import io.github.krosstack.kscope.service.ArtifactKind
import java.nio.file.Path

/** A task analysis shaped like one of the reference workspace: a few strong files, a tail, a document and images. */
internal object ResultFixtures {
    private val repository = RepositoryId("sample")
    private const val SETTINGS = "data/settings/src/main/kotlin/sample/tasktide/data/settings"
    private const val TASKS = "feature/tasks/src/main/kotlin/sample/tasktide/feature/tasks"
    private const val SETTINGS_TEST = "data/settings/src/test/kotlin/sample/tasktide/data/settings"
    private const val TASKS_TEST = "feature/tasks/src/test/kotlin/sample/tasktide/feature/tasks"
    const val DOCUMENT = "README.md"
    const val IMAGE = "app/src/main/res/mipmap/ic_launcher.png"
    const val SCREENSHOT = "feature/tasks/src/test/screenshots/TaskList.png"

    fun file(path: String, score: Double, role: FileRole = FileRole.READ): RankedFile = RankedFile(
        ArtifactRef(repository, RepoPath(path)),
        score,
        role,
        listOf(
            SignalContribution(SignalKind.LEXICAL_SYMBOL, score * 0.6),
            SignalContribution(SignalKind.GRAPH, score * 0.4),
        ),
    )

    private val strong = listOf(
        file("$SETTINGS/UserPreference.kt", 0.129, FileRole.EDIT),
        file("$SETTINGS_TEST/RoomPreferenceRepositoryTest.kt", 0.123, FileRole.EDIT),
        file("$SETTINGS/PreferenceDao.kt", 0.101),
        file("$SETTINGS/RoomPreferenceRepository.kt", 0.094),
        file("$SETTINGS/PreferenceRepository.kt", 0.092),
        file("$SETTINGS/SettingsDatabase.kt", 0.09),
        file("$SETTINGS/PreferenceKeys.kt", 0.088),
        file("$TASKS/TaskListViewModel.kt", 0.087),
        file("$TASKS/TaskAdapter.kt", 0.085),
        file("$TASKS/TaskListFragment.kt", 0.083),
        file("$TASKS/TaskDetailActivity.kt", 0.081),
        file("$TASKS/TaskListUiState.kt", 0.072),
        file("$TASKS/PriorityDotView.kt", 0.07),
    )

    private val extras = listOf(
        file(DOCUMENT, 0.053),
        file(IMAGE, 0.06),
        file(SCREENSHOT, 0.05),
    )

    private val tail = (1..20).map {
        file("core/model/src/commonMain/kotlin/sample/tasktide/core/model/Generated$it.kt", 0.03 - it * 0.001)
    }

    val files: List<RankedFile> = (strong + extras + tail).sortedByDescending { it.score }

    val kinds: Map<ArtifactRef, ArtifactKind> = mapOf(
        ArtifactRef(repository, RepoPath(DOCUMENT)) to ArtifactKind.DOCUMENT,
        ArtifactRef(repository, RepoPath(IMAGE)) to ArtifactKind.BINARY,
        ArtifactRef(repository, RepoPath(SCREENSHOT)) to ArtifactKind.BINARY,
    )

    private val workspace: Path = Path.of("workspace").toAbsolutePath()
    private val escape = 27.toChar()

    fun captured(detail: DetailLevel = DetailLevel.STANDARD, sections: Set<String>? = null): TextContext =
        TextContext(workspace, Emphasis.NONE, width = null, detail = detail, sections = sections)

    fun terminal(width: Int, emphasised: Boolean = true): TextContext =
        TextContext(workspace, Emphasis(emphasised), width = width)

    fun render(context: TextContext, reading: String? = Readings.TASK): String =
        AnalyzeText.render(analysis(), context, kinds, reading)

    fun visible(text: String): String = text.replace(escape.toString(), "<ESC>")

    fun analysis(files: List<RankedFile> = this.files): AnalysisResult = AnalysisResult(
        query = AnalyzedQuery(
            text = "Users want to be reminded about a task before it is due",
            terms = listOf("users", "want", "reminded", "task", "due"),
            exactMentions = emptyList(),
            expansions = emptyList(),
            hints = emptyList(),
        ),
        files = files,
        symbols = emptyList(),
        modules = emptyList(),
        tests = listOf(
            file("$SETTINGS_TEST/RoomPreferenceRepositoryTest.kt", 4.0),
            file("$TASKS_TEST/TaskListViewModelTest.kt", 4.0),
        ),
        boundaries = emptyList(),
        scope = ChangeScope(
            modules = listOf(ModuleId("sample//:data:settings"), ModuleId("sample//:feature:tasks")),
            sourceSets = listOf(SourceSetId("sample//:data:settings@main")),
            platforms = listOf("android"),
            variants = emptyList(),
            publicSymbols = emptyList(),
            crossModule = true,
        ),
        risks = emptyList(),
        unresolved = emptyList(),
        evidence = emptyMap(),
        confidence = "limited",
        contextEstimates = mapOf(
            ContextLevel.MINIMAL to TokenCount(11_212),
            ContextLevel.RECOMMENDED to TokenCount(30_572),
        ),
    )
}
