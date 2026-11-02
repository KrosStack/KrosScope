package mixed.legacy;

import java.util.List;
import java.util.Map;

/**
 * Shouts strings in Java. The second sentence is not part of the summary.
 *
 * @deprecated use the Kotlin service instead
 */
@Deprecated
public class JavaSupport extends Base implements Shouter<String> {
    public static final int LIMIT = 10;
    protected final int first = 1, second = 2;
    private String name;

    public JavaSupport() {
    }

    JavaSupport(String name, int... extra) {
        this.name = name;
    }

    /** Shouts the text. */
    @Override
    public String shout(String text) {
        return text.toUpperCase();
    }

    public String shout(int times) {
        return "x".repeat(times);
    }

    public int count(List<String> items, Map<String, List<Integer>> index, String[] names, int[][] grid) {
        return items.size();
    }

    public static native int fast();

    protected abstract static class Inner {
        abstract void run();
    }

    public enum Mode {
        FAST("f"), SLOW("s") {
            @Override
            String code() {
                return "slow";
            }
        };

        private final String code;

        Mode(String code) {
            this.code = code;
        }

        String code() {
            return code;
        }
    }

    public interface Listener {
        void onEvent(String event);

        default void onDone() {
        }
    }

    public @interface Tag {
        String value();

        int priority() default 1;
    }

    public record Pair(int left, String right) {
    }
}

abstract class Base {
}

interface Shouter<T> {
    T shout(T value);
}
