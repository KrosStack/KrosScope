package sample.tasktide.core.legacy;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public final class DateUtils {
    private static final DateTimeFormatter ISO_DAY = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC);

    private DateUtils() {
    }

    public static String formatIso(long epochMillis) {
        return ISO_DAY.format(Instant.ofEpochMilli(epochMillis));
    }

    public static boolean isOverdue(long dueAt, long now) {
        return dueAt < now;
    }
}
