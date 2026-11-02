package sample.tasktide.core.legacy;

import sample.tasktide.core.model.Priority;
import sample.tasktide.core.model.Task;

public class LegacyTaskFormatter {
    private static final String NO_DUE_DATE = "no due date";

    private final String separator;

    public LegacyTaskFormatter() {
        this(" | ");
    }

    public LegacyTaskFormatter(String separator) {
        this.separator = separator;
    }

    public String getSeparator() {
        return separator;
    }

    public String format(Task task) {
        return task.getTitle() + separator + priorityText(task) + separator + dueText(task);
    }

    public String priorityText(Task task) {
        Priority priority = task.getPriority();
        return priority == null ? PriorityLabels.UNKNOWN : PriorityLabels.INSTANCE.labelFor(priority);
    }

    public String slug(Task task) {
        return SlugsKt.slugify(task.getTitle());
    }

    private String dueText(Task task) {
        Long dueAt = task.getDueAt();
        return dueAt == null ? NO_DUE_DATE : DateUtils.formatIso(dueAt);
    }
}
