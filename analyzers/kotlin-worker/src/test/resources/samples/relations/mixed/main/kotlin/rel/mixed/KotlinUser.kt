package rel.mixed

class KotlinUser(private val support: JavaSupport) : JavaBase("kotlin"), JavaListener {
    override fun describe(): String = "kotlin"

    override fun listen(event: String) {
    }

    fun use(): Int {
        val created = JavaSupport("x")
        val plain = JavaPlain()
        val shouted = JavaSupport.shout("a") + JavaSupport.shout(2)
        val label = support.name
        support.name = "y"
        support.limit = support.limit + 1
        val max = JavaSupport.MAX
        support.onDone { println(it) }
        val callback = JavaCallback { println(it) }
        support.onDone(callback)
        val pair = JavaPair(1, "a")
        val left = pair.left()
        return plain.size() + max + shouted.length + label.length + created.hashCode() + left + pair.right().length
    }
}
