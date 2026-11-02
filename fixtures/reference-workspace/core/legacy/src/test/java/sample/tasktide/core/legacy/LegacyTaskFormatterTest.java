package sample.tasktide.core.legacy;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import sample.tasktide.core.model.Priority;
import sample.tasktide.core.model.Task;

public class LegacyTaskFormatterTest {
    private final LegacyTaskFormatter formatter = new LegacyTaskFormatter();

    @Test
    public void formatsTitlePriorityAndDueDate() {
        Task task = TaskFixtures.task("Pay rent", Priority.HIGH, 0L);

        assertEquals("Pay rent | high | 1970-01-01", formatter.format(task));
    }

    @Test
    public void missingDueDateIsLabelled() {
        Task task = TaskFixtures.task("Pay rent", Priority.URGENT);

        assertEquals("Pay rent | urgent | no due date", formatter.format(task));
    }

    @Test
    public void slugIsLowerCaseWithDashes() {
        Task task = TaskFixtures.task("Call the Bank!", Priority.NORMAL);

        assertEquals("call-the-bank", formatter.slug(task));
    }

    @Test
    public void customSeparatorIsUsed() {
        Task task = TaskFixtures.task("Pay rent", Priority.LOW);

        assertEquals("Pay rent;low;no due date", new LegacyTaskFormatter(";").format(task));
    }
}
