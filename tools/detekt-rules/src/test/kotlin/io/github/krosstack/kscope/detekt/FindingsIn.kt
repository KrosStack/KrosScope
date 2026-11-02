package io.github.krosstack.kscope.detekt

import dev.detekt.api.Config
import dev.detekt.api.Finding
import dev.detekt.test.lint

internal fun findingsIn(code: String): List<Finding> = SingleBehavingType(Config.empty).lint(code.trimIndent())
