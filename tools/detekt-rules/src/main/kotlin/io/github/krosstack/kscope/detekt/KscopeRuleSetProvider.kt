package io.github.krosstack.kscope.detekt

import dev.detekt.api.RuleSet
import dev.detekt.api.RuleSetId
import dev.detekt.api.RuleSetProvider

public class KscopeRuleSetProvider : RuleSetProvider {
    override val ruleSetId: RuleSetId = RuleSetId("kscope")

    override fun instance(): RuleSet = RuleSet(ruleSetId, listOf(::SingleBehavingType))
}
