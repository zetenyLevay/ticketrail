package dev.zeteny.ticketrail.config

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlMap
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ProfileParserTest {

    private val required = """
        spec_version: 5
        timezone: Europe/Amsterdam
        queues: [home, work, study]
        daily_note_path: Journal/{date}.md
        daily_note_template: Templates/Daily.md
        task_note_folder: Tasks
        task_note_template: Templates/Task.md
        state_file: Templates/Ticket_State.md
    """.trimIndent()

    private fun parse(yaml: String) =
        ProfileParser.parse(Yaml.default.parseToYamlNode(yaml) as YamlMap)

    private fun parseFailure(yaml: String): ProfileException {
        val result = parse(yaml)
        assertTrue(result.isFailure, "expected a failure for:\n$yaml")
        return assertIs<ProfileException>(result.exceptionOrNull())
    }

    /** [required] with the line for [key] replaced by [line], or removed when [line] is null. */
    private fun requiredWith(key: String, line: String?) =
        required.lines().mapNotNull { if (it.startsWith("$key:")) line else it }.joinToString("\n")

    @Test
    fun appendixExampleParses() {
        val profile = parse(
            required + "\n" + """
            tickets_heading: Tickets
            extra_note_types:
              assignment:
                queue: study
                due_date: read_only
                fields: [course, weight]
            excluded_paths: [Templates/, Archive/]
            """.trimIndent()
        ).getOrThrow()

        assertEquals(5, profile.specVersion)
        assertEquals(TimeZone.of("Europe/Amsterdam"), profile.timezone)
        assertEquals(setOf("home", "work", "study"), profile.queues)
        assertEquals("Journal/{date}.md", profile.dailyNotePath)
        assertEquals("Tasks".toPath(), profile.taskNoteFolder)
        assertEquals(
            mapOf("assignment" to NoteType("study", DueDateAccess.READ_ONLY, listOf("course", "weight"))),
            profile.extraNoteTypes,
        )
        assertEquals(listOf("Templates".toPath(), "Archive".toPath()), profile.excludedPaths)
    }

    @Test
    fun optionalParametersGetTheirDefaults() {
        val profile = parse(required).getOrThrow()

        assertEquals("Tickets", profile.ticketsHeading)
        assertEquals(emptyMap(), profile.extraNoteTypes)
        assertEquals(emptyList(), profile.excludedPaths)
        assertEquals("*.sync-conflict-*", profile.conflictPattern)
        assertEquals(10.seconds, profile.quietPeriod)
        assertEquals(500.milliseconds, profile.debounce)
        assertEquals(30.seconds, profile.pollInterval)
        assertEquals(10.minutes, profile.fullScanInterval)
        assertEquals(1, profile.wipWarnPerQueue)
    }

    @Test
    fun durationsUseTheirOwnUnits() {
        val profile = parse(required + "\nquiet_period: 5\ndebounce: 250\npoll_interval: 60\nfull_scan_interval: 2")
            .getOrThrow()

        assertEquals(5.seconds, profile.quietPeriod)
        assertEquals(250.milliseconds, profile.debounce)
        assertEquals(60.seconds, profile.pollInterval)
        assertEquals(2.minutes, profile.fullScanInterval)
    }

    @Test
    fun dailyNoteForFillsInTheDate() {
        val profile = parse(required).getOrThrow()
        assertEquals("Journal/2026-11-04.md".toPath(), profile.dailyNoteFor(LocalDate(2026, 11, 4)))
    }

    @Test
    fun isExcludedCoversExcludedPathsAndDotFolders() {
        val profile = parse(required + "\nexcluded_paths: [Templates/]").getOrThrow()

        assertTrue(profile.isExcluded("Templates/Ticket_Profile.md".toPath()))
        assertTrue(profile.isExcluded(".obsidian/app.json".toPath()))
        assertFalse(profile.isExcluded("TemplatesOld/Note.md".toPath()))
        assertFalse(profile.isExcluded("Journal/2026-11-04.md".toPath()))
    }

    @Test
    fun newerSpecVersionStillParsesButRefusesWrites() {
        val profile = parse(requiredWith(SPEC_VERSION, "spec_version: 99") + "\nsome_future_parameter: 1").getOrThrow()
        assertFalse(profile.supportsWrites)
    }

    @Test
    fun everyMissingRequiredParameterIsNamed() {
        for (key in listOf(SPEC_VERSION, TIMEZONE, QUEUES, DAILY_NOTE_PATH, DAILY_NOTE_TEMPLATE,
            TASK_NOTE_FOLDER, TASK_NOTE_TEMPLATE, STATE_FILE)) {
            assertEquals(key, parseFailure(requiredWith(key, null)).field, "missing $key")
        }
    }

    @Test
    fun unknownParameterIsRefused() {
        assertEquals("excluded_path", parseFailure(required + "\nexcluded_path: [Templates/]").field)
    }

    @Test
    fun nonIntegerSpecVersionIsRefused() {
        assertEquals(SPEC_VERSION, parseFailure(requiredWith(SPEC_VERSION, "spec_version: five")).field)
    }

    @Test
    fun unknownTimezoneIsRefused() {
        assertEquals(TIMEZONE, parseFailure(requiredWith(TIMEZONE, "timezone: Mars/Olympus")).field)
    }

    @Test
    fun badQueuesAreRefused() {
        for (line in listOf("queues: []", "queues: [Home]", "queues: [home, home]", "queues: home")) {
            assertEquals(QUEUES, parseFailure(requiredWith(QUEUES, line)).field, line)
        }
    }

    @Test
    fun dailyNotePathWithoutDateIsRefused() {
        assertEquals(DAILY_NOTE_PATH, parseFailure(requiredWith(DAILY_NOTE_PATH, "daily_note_path: Journal/today.md")).field)
    }

    @Test
    fun pathsOutsideTheVaultAreRefused() {
        assertEquals(STATE_FILE, parseFailure(requiredWith(STATE_FILE, "state_file: /etc/state.md")).field)
        assertEquals(TASK_NOTE_FOLDER, parseFailure(requiredWith(TASK_NOTE_FOLDER, "task_note_folder: ../Tasks")).field)
        assertEquals(EXCLUDED_PATHS, parseFailure(required + "\nexcluded_paths: [../Elsewhere]").field)
    }

    @Test
    fun wipWarnBelowOneIsRefused() {
        assertEquals(WIP_WARN_PER_QUEUE, parseFailure(required + "\nwip_warn_per_queue: 0").field)
    }

    @Test
    fun badNoteTypeEntriesAreRefused() {
        val entries = listOf(
            "task:\n    queue: work",
            "assignment:\n    queue: nowhere",
            "assignment:\n    due_date: sometimes",
            "assignment:\n    fields: [status]",
            "assignment:\n    colour: red",
        )
        for (entry in entries) {
            val e = parseFailure(required + "\nextra_note_types:\n  $entry")
            assertEquals(EXTRA_NOTE_TYPES, e.field, entry)
        }
    }

    @Test
    fun emptyNoteTypeEntryGetsDefaults() {
        val profile = parse(required + "\nextra_note_types:\n  meeting:").getOrThrow()
        assertEquals(mapOf("meeting" to NoteType()), profile.extraNoteTypes)
    }
}
