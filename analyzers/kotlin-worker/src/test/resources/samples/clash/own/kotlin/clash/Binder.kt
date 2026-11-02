package clash

class Binder {
    val binder = object : Contract.Stub() {
        override fun ping() = Unit
    }
}
