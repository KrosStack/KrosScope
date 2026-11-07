package io.github.krosstack.kscope.diagram

import kotlinx.serialization.Serializable

@Serializable
public enum class DrawnState { EXACT, PROBABLE, HEURISTIC, UNRESOLVED, NOT_ANALYSED }
