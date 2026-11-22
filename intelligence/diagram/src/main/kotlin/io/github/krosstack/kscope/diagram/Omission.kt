package io.github.krosstack.kscope.diagram

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 * [notExpanded] counts the symbols the walk reached but did not read the relationships of before it stopped.
 * [membersNotRead] counts the members of the walked types whose relationships the read budget left unread.
 * [callsNotDrawn] counts the resolved calls to a target that is no callable, by the kind of the target, in key order.
 * It covers the calls of the callables whose calls the walk followed.
 *
 * The next six fields count relationships the walk read and does not draw, each under exactly one reason. With the
 * links cut by the budget in [linksByState] and the links drawn they add up to the relationships read, of the kinds the
 * diagram shows, each counted once.
 * [notInType] is a type-level relationship whose other end is in no type: a top-level function, a file facade, a schema
 * message, or a class the index does not hold.
 * [notCallable] is a call-level relationship whose caller or target is no callable.
 * [withoutSymbol] is a relationship whose other end is not a symbol or has no symbol in the index.
 * [withinType] is a type-level relationship between two members of the same type.
 * [beyondDepth] is a relationship read from a symbol the walk did not follow: one at the last level, or a caller that
 * the walk follows upstream only.
 * [stopped] is a relationship the node cap or the read budget kept out.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
public data class Omission(
    public val nodes: Int,
    public val linksByState: Map<DrawnState, Int>,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val notExpanded: Int = 0,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val membersNotRead: Int = 0,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val callsNotDrawn: Map<String, Int> = emptyMap(),
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val notInType: Int = 0,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val notCallable: Int = 0,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val withoutSymbol: Int = 0,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val withinType: Int = 0,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val beyondDepth: Int = 0,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val stopped: Int = 0,
)
