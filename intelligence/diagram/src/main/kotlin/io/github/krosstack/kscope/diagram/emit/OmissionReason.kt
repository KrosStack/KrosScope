package io.github.krosstack.kscope.diagram.emit

import io.github.krosstack.kscope.diagram.Omission

internal enum class OmissionReason(val words: String, val count: (Omission) -> Int) {
    NOT_EXPANDED("symbols not expanded: the walk stopped before it read their links", Omission::notExpanded),
    MEMBERS_NOT_READ("members not read: the read limit left their links unread", Omission::membersNotRead),
    NOT_IN_TYPE(
        "links with an end in no type: a top-level function, a file facade or a class the index lacks",
        Omission::notInType,
    ),
    NOT_CALLABLE(
        "links with a caller or target that is no callable: the call level draws callables only",
        Omission::notCallable,
    ),
    WITHOUT_SYMBOL(
        "links with an end that has no symbol: an external library or an id the index lacks",
        Omission::withoutSymbol,
    ),
    WITHIN_TYPE("links between two members of one type: a type has no edge to itself", Omission::withinType),
    BEYOND_DEPTH(
        "links from a symbol the walk did not follow: the depth limit, or a caller followed upstream only",
        Omission::beyondDepth,
    ),
    STOPPED(
        "links to symbols not reached: the walk stopped at the node limit or the read limit",
        Omission::stopped,
    ),
}
