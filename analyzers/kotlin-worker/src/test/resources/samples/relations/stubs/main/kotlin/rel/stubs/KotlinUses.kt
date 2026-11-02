package rel.stubs

class KotlinUses {
    fun resource(): Int = R.string.app_name

    fun debug(): Boolean = BuildConfig.DEBUG

    fun remote(binder: Any): IFoo = IFoo.Stub.asInterface(binder)

    fun generated(): Int = Generated.value
}
