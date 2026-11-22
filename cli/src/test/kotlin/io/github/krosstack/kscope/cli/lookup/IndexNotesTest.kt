package io.github.krosstack.kscope.cli.lookup

import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.service.lookup.RepositoryNarrowing
import io.github.krosstack.kscope.service.lookup.SearchOrigin
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class IndexNotesTest {
    private val root = Path.of("work", "shop").toAbsolutePath()
    private val index = slashed(root.resolve(".kscope"))
    private val where = slashed(root)
    private val shop = RepositoryNarrowing(RepositoryId("r1"), "shop", atRoot = true)
    private val application = RepositoryNarrowing(RepositoryId("r2"), "application", atRoot = false)
    private val library = RepositoryNarrowing(RepositoryId("r3"), "library", atRoot = false)
    private val limited = "Using the index of a parent folder: $index. Paths are relative to $where. " +
        "Answer limited to application/; pass --workspace $where for the whole workspace."

    @Test
    fun `a repository at the root is limited under the name that info lists`() {
        assertEquals(
            "Using the index of a parent folder: $index. Paths are relative to $where. " +
                "Answer limited to the repository shop at the workspace root; " +
                "pass --workspace $where for the whole workspace.",
            IndexNotes.parent(root, SearchOrigin(shop)),
        )
    }

    @Test
    fun `a search that stopped at its cap names the repository and the way to search everywhere`() {
        assertEquals(
            "$limited The search in application/ stopped at its limit of candidates and may be incomplete; " +
                "pass --workspace $where to search everywhere.",
            IndexNotes.parent(root, SearchOrigin.limited(application, incomplete = true)),
        )
    }

    @Test
    fun `an identity outside the narrowing is named with its repository`() {
        assertEquals(
            "$limited \"sync.TaskSync\" lies in library/, outside that limit.",
            IndexNotes.parent(root, SearchOrigin(application, outside = library), "sync.TaskSync"),
        )
    }

    @Test
    fun `a widened search says which repository had nothing`() {
        assertEquals(
            "Using the index of a parent folder: $index. The answer covers the whole workspace $where, " +
                "and paths are relative to it. Nothing in the repository shop at the workspace root matches " +
                "\"clock\", so the search was widened to the whole workspace.",
            IndexNotes.parent(root, SearchOrigin(widenedFrom = shop), "clock"),
        )
    }

    @Test
    fun `an answer without an origin says that it covers the whole workspace`() {
        assertEquals(
            "Using the index of a parent folder: $index. The answer covers the whole workspace $where, " +
                "and paths are relative to it.",
            IndexNotes.parent(root),
        )
    }
}
