package rel.plugins

class Usage {
    fun ticketSerializer() = Ticket.serializer()

    fun companionSerializer() = Ticket.Companion.serializer()

    fun objectSerializer() = Settings.serializer()

    fun passCreator() = Pass.CREATOR

    fun plainSerializer() = Plain.serializer()

    fun plainCreator() = Plain.CREATOR

    fun passSerializer() = Pass.serializer()
}
