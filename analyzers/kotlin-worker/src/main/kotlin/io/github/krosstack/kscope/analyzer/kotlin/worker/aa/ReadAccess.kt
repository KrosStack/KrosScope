package io.github.krosstack.kscope.analyzer.kotlin.worker.aa

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable

internal fun <T> readAccess(block: () -> T): T = ApplicationManager.getApplication().runReadAction(Computable(block))
