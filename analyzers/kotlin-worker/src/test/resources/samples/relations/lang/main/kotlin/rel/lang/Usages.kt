package rel.lang

@Marker(Child::class, level = 2)
class User(private val names: Names) {
    val child = Child()
    val plain = Plain()
    var counter: Int = 0

    fun run(point: Point, box: Box<Int>): Int {
        val (a, b) = point
        val copy = point.copy(x = 1)
        val values = Color.values()
        val red = Color.RED
        Registry.register()
        val limit = Registry.LIMIT
        Registry.count = 3
        counter++
        counter += 2
        point.y = 5
        val read = point.x + point.y
        val length = "abc".shoutLength
        "abc".shout()
        val reference = ::helper
        val member = Child::hello
        val anonymous = object : Shape {
            override val area: Int = 1

            override fun scale(factor: Int): Shape = this
        }
        val total = over(1) + over("x").length
        val named = Point(x = 1, y = 2)
        val first = box[0]
        val sum = box + box
        for (item in box) {
            println(item)
        }
        val cached by lazy { 1 }
        val pair = 1 pair 2
        val local = names.size + total
        val derived = object : Base(1) {}
        return read + local
    }

    fun parameterOnly(factor: Int): Int {
        val doubled = factor * 2
        fun inner(extra: Int) = extra + doubled
        return inner(factor)
    }

    fun companionQualifiers(): Int {
        val start = Counter.START
        val made = Counter.make()
        val explicit = Counter.Companion.START
        return start + made.hashCode() + explicit
    }
}
