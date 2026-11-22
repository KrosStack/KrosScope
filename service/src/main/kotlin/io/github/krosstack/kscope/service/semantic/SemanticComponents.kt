package io.github.krosstack.kscope.service.semantic

import io.github.krosstack.kscope.retrieval.SemanticState
import io.github.krosstack.kscope.service.components.ComponentDirectory
import io.github.krosstack.kscope.service.components.ComponentListing
import io.github.krosstack.kscope.service.components.ComponentManifest
import io.github.krosstack.kscope.service.components.ComponentPlatform
import io.github.krosstack.kscope.service.components.ComponentState
import io.github.krosstack.kscope.service.components.listing
import io.github.krosstack.kscope.service.semantic.query.QueryDeadline
import io.github.krosstack.kscope.service.settings.WorkspaceSettings
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Finds the installed worker and model the manifest names and starts the worker under a machine-wide budget. */
internal open class SemanticComponents(
    private val directory: ComponentDirectory,
    private val budget: OptionalProcessBudget,
    private val componentFlags: () -> Map<String, Boolean>,
    private val manifest: ComponentManifest = ComponentManifest.shipped(),
    private val startup: Duration = 120.seconds,
) {
    sealed interface Opening {
        class Ready(
            val provider: LazyWorkerProvider,
            val fingerprint: () -> String = { provider.identity.toString() },
        ) : Opening {
            fun lost(): String? = provider.lost

            fun close(deadline: QueryDeadline? = null) = provider.close(deadline)
        }

        data class Unavailable(val state: SemanticState, val reason: String) : Opening
    }

    open fun unavailable(): Opening.Unavailable? = problem(listings())

    open fun open(): Opening {
        val listings = listings()
        problem(listings)?.let { return it }
        return try {
            val model = checkNotNull(listings.getValue(MODEL).installed).directory
            val layout = ComponentLayout(
                checkNotNull(listings.getValue(WORKER).installed).directory,
                model.resolve("model").takeIf { Files.exists(it, NOFOLLOW_LINKS) } ?: model,
            )
            Opening.Ready(
                LazyWorkerProvider(layout.identity, WorkerEmbeddingProvider.ID) { deadline, diagnostics ->
                    start(layout, deadline, diagnostics)
                },
                ::fingerprint,
            )
        } catch (failure: IOException) {
            Opening.Unavailable(SemanticState.WORKER_UNAVAILABLE, failure.message.orEmpty())
        } catch (failure: IllegalArgumentException) {
            Opening.Unavailable(SemanticState.WORKER_UNAVAILABLE, failure.message.orEmpty())
        } catch (failure: IllegalStateException) {
            Opening.Unavailable(SemanticState.WORKER_UNAVAILABLE, failure.message.orEmpty())
        }
    }

    private fun fingerprint(): String {
        val identity = listings().values.sortedBy { it.entry.name }.joinToString("\n") { listing ->
            val installed = listing.installed
            val directory = installed?.directory
            val stamp = directory?.let { Files.readAttributes(it, BasicFileAttributes::class.java, NOFOLLOW_LINKS) }
            val descriptor = directory?.resolve("model/model.json")?.takeIf { Files.isRegularFile(it, NOFOLLOW_LINKS) }
                ?: directory?.resolve("model.json")?.takeIf { Files.isRegularFile(it, NOFOLLOW_LINKS) }
            "${listing.entry}:${listing.state}:$installed:${stamp?.fileKey()}:${stamp?.creationTime()}:" +
                descriptor?.let(Files::readString).orEmpty()
        }
        return MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).toHexString()
    }

    private fun start(
        layout: ComponentLayout,
        deadline: QueryDeadline?,
        diagnostics: ((String) -> Unit)?,
    ): LazyWorkerProvider.Start {
        deadline?.requireRemaining()
        val admitted = if (deadline == null) budget.reserve() else budget.tryReserve()
        val reservation = when (val granted = admitted) {
            is OptionalProcessBudget.Reservation.Refused -> {
                if (deadline == null) throw IOException(granted.reason)
                return LazyWorkerProvider.Start.Refused(
                    WorkerEmbeddingProvider.QueryResult.Refused(
                        WorkerEmbeddingProvider.Refusal.MEMORY_BUSY,
                        granted.reason,
                    ),
                )
            }

            is OptionalProcessBudget.Reservation.Granted -> granted.reservation
        }
        var started = false
        try {
            deadline?.requireRemaining()
            val deadlines = WorkerDeadlines(startup = startup, absoluteQuery = deadline)
            val provider = WorkerEmbeddingProvider(
                layout.identity,
                layout.command(),
                deadlines,
                diagnostics = diagnostics ?: System.err::println,
            )
            started = true
            return LazyWorkerProvider.Started(provider, reservation::lost, provider::embedQuery) { closingDeadline ->
                closeBoth(provider, reservation, closingDeadline)
            }
        } finally {
            if (!started) reservation.close()
        }
    }

    private fun closeBoth(
        provider: WorkerEmbeddingProvider,
        reservation: ProcessReservation,
        deadline: QueryDeadline?,
    ) {
        try {
            provider.close(deadline)
        } finally {
            reservation.close()
        }
    }

    private fun listings(): Map<String, ComponentListing> {
        val platform = ComponentPlatform.current(System.getProperty("os.name"), System.getProperty("os.arch"))
        val settings = WorkspaceSettings(components = componentFlags())
        return manifest.listing(directory, platform, settings).filter { it.entry.name in NAMES }
            .associateBy { it.entry.name }
    }

    private fun problem(listings: Map<String, ComponentListing>): Opening.Unavailable? {
        val states = NAMES.map { listings[it]?.state ?: ComponentState.NOT_INSTALLED }
        return when {
            ComponentState.DISABLED in states -> Opening.Unavailable(SemanticState.DISABLED, "Disabled by workspace")

            states.all { it == ComponentState.INSTALLED } -> null

            states.any { it == ComponentState.SELF_TEST_FAILED || it == ComponentState.CORRUPT } -> Opening.Unavailable(
                SemanticState.WORKER_UNAVAILABLE,
                states.first { it != ComponentState.INSTALLED }.text,
            )

            else -> Opening.Unavailable(SemanticState.NOT_INSTALLED, "Semantic components are not installed")
        }
    }

    companion object {
        private const val WORKER = "semantic"
        private const val MODEL = "semantic-model"
        private val NAMES = listOf(WORKER, MODEL)

        fun reservations(root: Path): Path = root.resolve(".reservations")

        fun installed(
            env: (String) -> String?,
            allowanceMebibytes: Long?,
            flags: () -> Map<String, Boolean>,
        ): SemanticComponents {
            val directory = ComponentDirectory.resolve(System::getProperty, env)
            val budget = reservations(directory.root).let { reservations ->
                if (allowanceMebibytes == null) {
                    OptionalProcessBudget(reservations)
                } else {
                    OptionalProcessBudget(reservations, allowanceMebibytes = { allowanceMebibytes })
                }
            }
            return SemanticComponents(directory, budget, flags)
        }
    }
}
