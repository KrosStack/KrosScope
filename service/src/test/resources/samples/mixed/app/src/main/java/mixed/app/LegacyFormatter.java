package mixed.app;

import mixed.core.PoliteGreeter;

public final class LegacyFormatter {
    private LegacyFormatter() {
    }

    public static String format(String line) {
        return new PoliteGreeter("Dear").greet(line.trim());
    }
}
