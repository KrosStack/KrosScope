package rel.collisions;

public final class Boxes {
    public static final class First {
        public static final class Item {
        }
    }

    public static final class Second {
        public static final class Item {
        }
    }

    public static String take(int value) {
        return "primitive";
    }

    public static String take(Integer value) {
        return "boxed";
    }

    public static String join(First.Item value) {
        return "first";
    }

    public static String join(Second.Item value) {
        return "second";
    }

    public static String callsPrimitive() {
        return take(1);
    }

    public static String callsBoxed() {
        return take(Integer.valueOf(1));
    }

    public static String callsJoin() {
        return join(new First.Item());
    }
}
