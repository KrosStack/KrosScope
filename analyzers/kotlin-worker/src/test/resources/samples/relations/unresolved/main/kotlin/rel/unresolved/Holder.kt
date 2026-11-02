package rel.unresolved

import missing.pkg.Imported

class Holder {
    val unknown: Missing? = null

    fun unknownCall(): Int = notDefined(1)

    fun ambiguous(): Int = choose(1, 2, 3)

    fun probable(): Int = single("text")

    fun qualified(): Int = java.util.ArrayList<String>().size

    fun unknownMember(holder: Holder): Int = holder.noSuchMember

    fun lambdaOfUnresolved(): Int = notDefined { it + 1 }

    fun onlyLibraryCandidates(): Any = take(1, 2, 3)
}

fun choose(value: Int): Int = value

fun choose(value: String): Int = value.length

fun single(value: Int): Int = value
