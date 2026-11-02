package sample.tasktide.core.legacy;

import java.util.List;
import sample.tasktide.core.model.Task;

public interface Exporter {
    String export(List<Task> tasks);
}
