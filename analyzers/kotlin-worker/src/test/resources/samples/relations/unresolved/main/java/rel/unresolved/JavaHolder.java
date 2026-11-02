package rel.unresolved;

import missing.pkg.JavaImported;

public class JavaHolder {
    Absent absent = new Absent();

    int call() {
        return notDefined(1) + Holder.class.hashCode();
    }

    int overloaded() {
        return pick(null);
    }

    int pick(String text) {
        return 1;
    }

    int pick(Integer number) {
        return 2;
    }
}
