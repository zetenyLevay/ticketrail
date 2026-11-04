package dev.zeteny.ticketrail.config

import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlNull
import com.charleskorn.kaml.YamlScalar
import kotlinx.datetime.IllegalTimeZoneException
import kotlinx.datetime.TimeZone
import okio.Path
import okio.Path.Companion.toPath
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

internal const val SPEC_VERSION = "spec_version"
internal const val TIMEZONE = "timezone"
internal const val QUEUES = "queues"
internal const val DAILY_NOTE_PATH = "daily_note_path"
internal const val DAILY_NOTE_TEMPLATE = "daily_note_template"
internal const val TICKETS_HEADING = "tickets_heading"
internal const val TASK_NOTE_FOLDER = "task_note_folder"
internal const val TASK_NOTE_TEMPLATE = "task_note_template"
internal const val EXTRA_NOTE_TYPES = "extra_note_types"
internal const val STATE_FILE = "state_file"
internal const val EXCLUDED_PATHS = "excluded_paths"
internal const val CONFLICT_PATTERN = "conflict_pattern"
internal const val QUIET_PERIOD = "quiet_period"
internal const val DEBOUNCE = "debounce"
internal const val POLL_INTERVAL = "poll_interval"
internal const val FULL_SCAN_INTERVAL = "full_scan_interval"
internal const val WIP_WARN_PER_QUEUE = "wip_warn_per_queue"

private val PROFILE_KEYS = setOf(
    SPEC_VERSION, TIMEZONE, QUEUES, DAILY_NOTE_PATH, DAILY_NOTE_TEMPLATE, TICKETS_HEADING,
    TASK_NOTE_FOLDER, TASK_NOTE_TEMPLATE, EXTRA_NOTE_TYPES, STATE_FILE, EXCLUDED_PATHS,
    CONFLICT_PATTERN, QUIET_PERIOD, DEBOUNCE, POLL_INTERVAL, FULL_SCAN_INTERVAL, WIP_WARN_PER_QUEUE,
)

// Keys of a note type entry (spec 1.4).
private const val QUEUE = "queue"
private const val DUE_DATE = "due_date"
private const val FIELDS = "fields"
private val NOTE_TYPE_KEYS = setOf(QUEUE, DUE_DATE, FIELDS)

private val QUEUE_NAME = Regex("[a-z][a-z0-9_-]*")

/** Turns profile parameters into a [Profile], applying the defaults and checks of spec 1.2 and 1.4. */
object ProfileParser {

    fun parse(parameters: YamlMap): Result<Profile> = runCatching { parseOrThrow(parameters) }

    private fun parseOrThrow(map: YamlMap): Profile {
        val specVersion = map.optionalInt(SPEC_VERSION) ?: invalidProfile(SPEC_VERSION, "$SPEC_VERSION is required")

        // A newer spec may add parameters this service doesn't know (0.3), so only refuse
        // unknown keys for versions we implement, where they are most likely typos.
        if (specVersion <= Profile.SUPPORTED_SPEC_VERSION) {
            map.entries.keys.map { it.content }.firstOrNull { it !in PROFILE_KEYS }
                ?.let { invalidProfile(it, "unknown parameter '$it'") }
        }

        val queues = queuesOf(map)

        return Profile(
            specVersion = specVersion,
            timezone = timezoneOf(map.requireString(TIMEZONE)),
            queues = queues,
            dailyNotePath = dailyNotePathOf(map.requireString(DAILY_NOTE_PATH)),
            dailyNoteTemplate = vaultPathOf(DAILY_NOTE_TEMPLATE, map.requireString(DAILY_NOTE_TEMPLATE)),
            taskNoteFolder = vaultPathOf(TASK_NOTE_FOLDER, map.requireString(TASK_NOTE_FOLDER)),
            taskNoteTemplate = vaultPathOf(TASK_NOTE_TEMPLATE, map.requireString(TASK_NOTE_TEMPLATE)),
            stateFile = vaultPathOf(STATE_FILE, map.requireString(STATE_FILE)),
            ticketsHeading = map.optionalString(TICKETS_HEADING)?.let(::ticketsHeadingOf)
                ?: Profile.DEFAULT_TICKETS_HEADING,
            extraNoteTypes = extraNoteTypesOf(map, queues),
            excludedPaths = map.optionalStringList(EXCLUDED_PATHS)?.map { vaultPathOf(EXCLUDED_PATHS, it) }
                ?: emptyList(),
            conflictPattern = map.optionalString(CONFLICT_PATTERN)?.let(::conflictPatternOf)
                ?: Profile.DEFAULT_CONFLICT_PATTERN,
            quietPeriod = map.optionalDuration(QUIET_PERIOD, min = 0) { it.seconds } ?: Profile.DEFAULT_QUIET_PERIOD,
            debounce = map.optionalDuration(DEBOUNCE, min = 0) { it.milliseconds } ?: Profile.DEFAULT_DEBOUNCE,
            pollInterval = map.optionalDuration(POLL_INTERVAL, min = 1) { it.seconds } ?: Profile.DEFAULT_POLL_INTERVAL,
            fullScanInterval = map.optionalDuration(FULL_SCAN_INTERVAL, min = 1) { it.minutes }
                ?: Profile.DEFAULT_FULL_SCAN_INTERVAL,
            wipWarnPerQueue = map.optionalInt(WIP_WARN_PER_QUEUE, min = 1) ?: Profile.DEFAULT_WIP_WARN_PER_QUEUE,
        )
    }

    private fun timezoneOf(value: String): TimeZone =
        try {
            TimeZone.of(value)
        } catch (e: IllegalTimeZoneException) {
            invalidProfile(TIMEZONE, "$TIMEZONE must be an IANA zone name like 'Europe/Amsterdam', got '$value'")
        }

    private fun queuesOf(map: YamlMap): Set<String> {
        val names = map.optionalStringList(QUEUES) ?: invalidProfile(QUEUES, "$QUEUES is required")
        if (names.isEmpty()) invalidProfile(QUEUES, "$QUEUES must list at least one queue")
        names.firstOrNull { !QUEUE_NAME.matches(it) }
            ?.let { invalidProfile(QUEUES, "queue '$it' must match ${QUEUE_NAME.pattern}") }
        names.groupBy { it }.entries.firstOrNull { it.value.size > 1 }
            ?.let { invalidProfile(QUEUES, "queue '${it.key}' is listed more than once") }
        return names.toSet()
    }

    private fun dailyNotePathOf(value: String): String {
        val pattern = value.trim()
        if (Profile.DATE_PLACEHOLDER !in pattern) {
            invalidProfile(DAILY_NOTE_PATH, "$DAILY_NOTE_PATH must contain ${Profile.DATE_PLACEHOLDER}, got '$value'")
        }
        // Check the path rules on a real date, so the pattern can't produce a path outside the vault.
        vaultPathOf(DAILY_NOTE_PATH, pattern.replace(Profile.DATE_PLACEHOLDER, "2000-01-01"))
        return pattern
    }

    private fun ticketsHeadingOf(value: String): String {
        val heading = value.trim()
        if (heading.isEmpty()) invalidProfile(TICKETS_HEADING, "$TICKETS_HEADING must not be empty")
        if (heading.startsWith("#")) {
            invalidProfile(TICKETS_HEADING, "$TICKETS_HEADING is the heading text without '#', got '$value'")
        }
        return heading
    }

    private fun conflictPatternOf(value: String): String {
        val pattern = value.trim()
        if (pattern.isEmpty()) invalidProfile(CONFLICT_PATTERN, "$CONFLICT_PATTERN must not be empty")
        if ('/' in pattern) invalidProfile(CONFLICT_PATTERN, "$CONFLICT_PATTERN matches file names, so it must not contain '/'")
        return pattern
    }

    private fun extraNoteTypesOf(map: YamlMap, queues: Set<String>): Map<String, NoteType> {
        val types = when (val node = map.get<YamlNode>(EXTRA_NOTE_TYPES)) {
            null, is YamlNull -> return emptyMap()
            is YamlMap -> node
            else -> invalidProfile(EXTRA_NOTE_TYPES, "$EXTRA_NOTE_TYPES must be a map from type name to entry")
        }
        return types.entries.entries.associate { (key, entry) ->
            val name = key.content.trim()
            if (name.isEmpty()) invalidProfile(EXTRA_NOTE_TYPES, "a type name in $EXTRA_NOTE_TYPES is empty")
            if (name == TASK_NOTE_TYPE) invalidProfile(EXTRA_NOTE_TYPES, "'$TASK_NOTE_TYPE' is built in and can't be redeclared")
            name to noteTypeOf(name, entry, queues)
        }
    }

    private fun noteTypeOf(name: String, node: YamlNode, queues: Set<String>): NoteType {
        val entry = when (node) {
            is YamlNull -> return NoteType()
            is YamlMap -> node
            else -> invalidProfile(EXTRA_NOTE_TYPES, "the entry for '$name' must be a map")
        }
        fun bad(message: String): Nothing = invalidProfile(EXTRA_NOTE_TYPES, "type '$name': $message")

        entry.entries.keys.map { it.content }.firstOrNull { it !in NOTE_TYPE_KEYS }
            ?.let { bad("unknown key '$it', expected one of ${NOTE_TYPE_KEYS.joinToString()}") }

        val queue = entry.optionalString(QUEUE, field = EXTRA_NOTE_TYPES)?.trim()?.also {
            if (it !in queues) bad("$QUEUE '$it' is not in $QUEUES")
        }
        val dueDate = entry.optionalString(DUE_DATE, field = EXTRA_NOTE_TYPES)?.trim()?.let { value ->
            DueDateAccess.entries.firstOrNull { it.yaml == value }
                ?: bad("$DUE_DATE must be ${DueDateAccess.entries.joinToString(" or ") { it.yaml }}, got '$value'")
        } ?: DueDateAccess.READ_ONLY
        val fields = entry.optionalStringList(FIELDS, field = EXTRA_NOTE_TYPES)?.map { it.trim() } ?: emptyList()
        fields.firstOrNull { it.isEmpty() }?.let { bad("$FIELDS contains an empty name") }
        fields.firstOrNull { it in CORE_NOTE_FIELDS }?.let { bad("$FIELDS must not repeat the core field '$it'") }

        return NoteType(queue, dueDate, fields)
    }

    /** A vault-relative path: not empty, not absolute, and not leaving the vault. */
    private fun vaultPathOf(field: String, value: String): Path {
        val path = value.trim().ifEmpty { invalidProfile(field, "$field must not be empty") }.toPath()
        if (path.isAbsolute) invalidProfile(field, "$field must be relative to the vault root, got '$value'")
        if (".." in path.segments) invalidProfile(field, "$field must stay inside the vault, got '$value'")
        return path
    }

    private fun YamlMap.requireString(key: String): String =
        optionalString(key) ?: invalidProfile(key, "$key is required")

    private fun YamlMap.optionalString(key: String, field: String = key): String? =
        when (val node = get<YamlNode>(key)) {
            null, is YamlNull -> null
            is YamlScalar -> node.content
            else -> invalidProfile(field, "$key must be a single value, not a list or map")
        }

    private fun YamlMap.optionalStringList(key: String, field: String = key): List<String>? =
        when (val node = get<YamlNode>(key)) {
            null, is YamlNull -> null
            is YamlList -> node.items.map { item ->
                (item as? YamlScalar)?.content ?: invalidProfile(field, "$key must be a list of single values")
            }
            else -> invalidProfile(field, "$key must be a list, like '$key: [a, b]'")
        }

    private fun YamlMap.optionalInt(key: String, min: Int? = null): Int? {
        val value = optionalString(key) ?: return null
        val number = value.trim().toIntOrNull() ?: invalidProfile(key, "$key must be an integer, got '$value'")
        if (min != null && number < min) invalidProfile(key, "$key must be at least $min, got $number")
        return number
    }

    private fun YamlMap.optionalDuration(key: String, min: Int, unit: (Int) -> Duration): Duration? =
        optionalInt(key, min)?.let(unit)
}
