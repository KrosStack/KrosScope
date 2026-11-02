package sample.tasktide.core.legacy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DateUtilsTest {
    @Test
    public void formatsEpochAsIsoDate() {
        assertEquals("1970-01-01", DateUtils.formatIso(0L));
    }

    @Test
    public void formatsLaterDay() {
        assertEquals("2023-11-14", DateUtils.formatIso(1_700_000_000_000L));
    }

    @Test
    public void overdueWhenDueDateIsInThePast() {
        assertTrue(DateUtils.isOverdue(10L, 20L));
        assertFalse(DateUtils.isOverdue(20L, 10L));
    }
}
