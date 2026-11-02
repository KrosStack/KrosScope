package io.github.krosstack.kscope.context.estimate

import io.github.krosstack.kscope.context.ContextFixture
import io.github.krosstack.kscope.model.context.ContextInput

internal class EstimateCase(val name: String, val fixture: ContextFixture, val input: ContextInput)

internal fun estimateCases(): List<EstimateCase> =
    TextEdgeCases.all() + FragmentEdgeCases.all() + PackageEdgeCases.all()

internal fun estimateCase(name: String): EstimateCase = estimateCases().first { it.name == name }
