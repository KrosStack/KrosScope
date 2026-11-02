package rel.plugins

import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
class Ticket(val id: Int) {
    companion object {
        fun fromId(id: Int): Ticket = Ticket(id)

        fun codec() = serializer()
    }
}

@Parcelize
class Pass(val code: String)

@kotlinx.serialization.Serializable
object Settings

class Plain(val value: Int)
