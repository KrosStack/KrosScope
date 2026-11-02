package rel.inheritapp

import rel.inherit.BaseRepository
import rel.inherit.Named
import rel.inherit.Repository

class UserRepository : BaseRepository<String>() {
    override fun load(id: Int): String = "user$id"

    override fun describe(): String = "users"
}

class Cached(private val delegate: Repository<String>) : Repository<String> by delegate

class Person(override val name: String) : Named(name)

fun anonymous(): Repository<Int> = object : Repository<Int> {
    override fun load(id: Int): Int = id

    override fun count(): Int = 1
}
