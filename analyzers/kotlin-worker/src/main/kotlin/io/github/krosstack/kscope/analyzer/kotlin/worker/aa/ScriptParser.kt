package io.github.krosstack.kscope.analyzer.kotlin.worker.aa

import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import org.jetbrains.kotlin.analysis.api.standalone.buildStandaloneAnalysisAPISession
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory

/** Parses Kotlin text without resolving anything; the environment is created on first use. */
internal class ScriptParser : AutoCloseable {
    private val disposable: Disposable = Disposer.newDisposable("kscope-script-parser")
    private val project: Project by lazy(::parsingProject)
    private val factory: KtPsiFactory by lazy { KtPsiFactory(project, markGenerated = false) }

    fun parse(fileName: String, text: String): KtFile =
        factory.createFile(fileName, StringUtil.convertLineSeparators(text))

    fun parseJava(fileName: String, text: String): PsiFile = PsiFileFactory.getInstance(project)
        .createFileFromText(fileName, JavaLanguage.INSTANCE, StringUtil.convertLineSeparators(text))

    override fun close() {
        Disposer.dispose(disposable)
    }

    private fun parsingProject(): Project = buildStandaloneAnalysisAPISession(disposable, false) {
        buildKtModuleProvider { platform = JvmPlatforms.defaultJvmPlatform }
    }.project
}
