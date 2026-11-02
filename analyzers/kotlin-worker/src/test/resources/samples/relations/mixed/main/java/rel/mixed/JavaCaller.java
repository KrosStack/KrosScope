package rel.mixed;

import java.util.List;

public class JavaCaller implements Handler {
    @Override
    public int handle(String input) {
        return input.length();
    }

    int call(List<Service> services) {
        Service service = new Service("x", 1);
        Service other = new Service();
        Implicit implicit = new Implicit();
        String label = service.getLabel();
        service.setCounter(2);
        Service created = Service.create();
        Service viaCompanion = Service.Companion.instance();
        int limit = Service.LIMIT;
        int registered = Registry.INSTANCE.register("a");
        Mode mode = Mode.FAST;
        int twice = ApiKt.twice(2);
        int extra = ApiKt.getExtra("x");
        service.run("task");
        service.run(3);
        JavaPair pair = new JavaPair(1, "a");
        Runnable anonymous = new Runnable() {
            public void run() {
                service.run(4);
            }
        };
        return limit + registered + twice + extra + services.size() + pair.hashCode();
    }
}
