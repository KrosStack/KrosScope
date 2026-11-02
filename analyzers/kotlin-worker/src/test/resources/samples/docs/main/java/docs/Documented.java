package docs;

/**
 * Describes a task. Details come later.
 */
public class Documented {
    /** The identifier. */
    public final int id = 0;

    /**
     * Finds the task by {@code id}.
     *
     * @param id the identifier
     * @return the task
     * @deprecated use {@link #lookup(int)}
     */
    public String find(int id) {
        return "";
    }

    public String lookup(int id) {
        return "";
    }
}
