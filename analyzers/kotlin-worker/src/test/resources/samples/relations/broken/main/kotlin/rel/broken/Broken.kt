package rel.broken

class Target {
    fun ok(): Int = 1
}

class Damaged {
    fun fine(target: Target): Int = target.ok()

    fun broken(: Int {
        val unfinished = Target(
    }

    fun after(target: Target): Int = target.ok()
}

fun trailing(target: Target): Int = target.ok()
