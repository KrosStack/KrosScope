package rel.stubs;

public class JavaUses {
    int resource() {
        return R.string.app_name;
    }

    boolean debug() {
        return BuildConfig.DEBUG;
    }

    IFoo remote(Object binder) {
        return IFoo.Stub.asInterface(binder);
    }

    int generated() {
        return Generated.INSTANCE.getValue();
    }
}
