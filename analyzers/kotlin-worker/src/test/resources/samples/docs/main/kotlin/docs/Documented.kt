package docs

/**
 * Loads the cached tasks. Falls back to the network when the cache is cold.
 *
 * @param id the task identifier
 * @return the task, or null
 */
class TaskLoader(
    /** The repository the tasks come from. It may be replaced. */
    val repository: String,
    val limit: Int,
) {
    /** Returns the [TaskLoader] summary as `text`; more detail follows. */
    fun summary(): String = ""

    /**
     * No full stop here
     *
     * Second paragraph is ignored.
     */
    fun noPunctuation() {}

    /** @return only a tag, so there is no description */
    fun tagOnly(): Int = 0

    // A line comment is not documentation.
    fun plain() {}

    /** Question mark ends it? Not this part. */
    val flag: Boolean = true
}

/** Summary on a single line. */
typealias Alias = String

/**
 * <p>Handles markup and {@code inline} tags. See {@link Other#method}.
 */
object Markup
