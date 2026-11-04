package dev.zeteny.ticketrail.config

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import okio.Path
import okio.Path.Companion.toPath
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** The profile parameters of spec 1.2. Every path is relative to the vault root. */
data class Profile(
    val specVersion: Int,
    val timezone: TimeZone,
    val queues: Set<String>,
    val dailyNotePath: String,          // pattern containing {date}, e.g. "Journal/{date}.md"
    val dailyNoteTemplate: Path,
    val taskNoteFolder: Path,
    val taskNoteTemplate: Path,
    val stateFile: Path,
    val ticketsHeading: String = DEFAULT_TICKETS_HEADING,
    val extraNoteTypes: Map<String, NoteType> = emptyMap(),
    val excludedPaths: List<Path> = emptyList(),
    val conflictPattern: String = DEFAULT_CONFLICT_PATTERN,
    val quietPeriod: Duration = DEFAULT_QUIET_PERIOD,
    val debounce: Duration = DEFAULT_DEBOUNCE,
    val pollInterval: Duration = DEFAULT_POLL_INTERVAL,
    val fullScanInterval: Duration = DEFAULT_FULL_SCAN_INTERVAL,
    val wipWarnPerQueue: Int = DEFAULT_WIP_WARN_PER_QUEUE,
) {
    /** Spec 0.3: a profile targeting a newer spec is still read and indexed, but every write is refused. */
    val supportsWrites: Boolean get() = specVersion <= SUPPORTED_SPEC_VERSION

    /** Spec 5.2: the daily note for [date]. */
    fun dailyNoteFor(date: LocalDate): Path = dailyNotePath.replace(DATE_PLACEHOLDER, date.toString()).toPath()

    /**
     * Spec 3.4: true if [path] is never indexed, because it lies under a folder in [excludedPaths]
     * or has a segment starting with `.`. Conflict copies ([conflictPattern]) are handled separately.
     */
    fun isExcluded(path: Path): Boolean =
        path.segments.any { it.startsWith(".") } ||
            excludedPaths.any { prefix -> path.segments.take(prefix.segments.size) == prefix.segments }

    companion object {
        /** The highest spec version this service implements. */
        const val SUPPORTED_SPEC_VERSION = 5

        const val DATE_PLACEHOLDER = "{date}"
        const val DEFAULT_TICKETS_HEADING = "Tickets"
        const val DEFAULT_CONFLICT_PATTERN = "*.sync-conflict-*"
        val DEFAULT_QUIET_PERIOD = 10.seconds
        val DEFAULT_DEBOUNCE = 500.milliseconds
        val DEFAULT_POLL_INTERVAL = 30.seconds
        val DEFAULT_FULL_SCAN_INTERVAL = 10.minutes
        const val DEFAULT_WIP_WARN_PER_QUEUE = 1
    }
}

/** A note type entry of `extra_note_types` (spec 1.4). */
data class NoteType(
    val queue: String? = null,
    val dueDate: DueDateAccess = DueDateAccess.READ_ONLY,
    val fields: List<String> = emptyList(),
)

enum class DueDateAccess(val yaml: String) {
    WRITABLE("writable"),
    READ_ONLY("read_only"),
}

/** The built-in ticket note type (spec 4.1). */
const val TASK_NOTE_TYPE = "task"

/** The core fields of every ticket note (spec 4.2). */
val CORE_NOTE_FIELDS = setOf("type", "ticket_id", "status", "priority", "queue", "do_date", "due_date")

class ProfileException(val field: String?, message: String) : Exception(message)

internal fun invalidProfile(field: String?, message: String): Nothing =
    throw ProfileException(field, "profile: $message")
