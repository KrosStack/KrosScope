package io.github.krosstack.kscope.service.lookup

import io.github.krosstack.kscope.engine.inspection.hasAnyIndex
import io.github.krosstack.kscope.service.KrosScope

internal fun KrosScope.holdsAnyIndex(): Boolean = engine.hasAnyIndex()
