package dev.zeteny.ticketrail.config

import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProfileLoaderTest {

    private val vaultRoot = "/vault".toPath()
    private val fs = FakeFileSystem().apply { createDirectories(vaultRoot / "Templates") }

    private val parameters = """
        spec_version: 5
        timezone: Europe/Amsterdam
        queues: [home, work]
        daily_note_path: Journal/{date}.md
        daily_note_template: Templates/Daily.md
        task_note_folder: Tasks
        task_note_template: Templates/Task.md
        state_file: Templates/Ticket_State.md
    """.trimIndent()

    private fun writeNote(path: String, parameters: String) {
        fs.write(vaultRoot / path) { writeUtf8("# Profile\n\n```ticket-profile\n$parameters\n```\n") }
    }

    private fun loadFailure(config: ServiceConfig): ProfileException {
        val result = loadProfile(config, fs)
        assertTrue(result.isFailure)
        return assertIs<ProfileException>(result.exceptionOrNull())
    }

    @Test
    fun loadsAProfileNoteInAnExcludedPath() {
        writeNote("Templates/Ticket_Profile.md", parameters + "\nexcluded_paths: [Templates/]")
        val config = ServiceConfig.inVault("/vault", "Templates/Ticket_Profile.md").getOrThrow()

        assertEquals(setOf("home", "work"), loadProfile(config, fs).getOrThrow().queues)
    }

    @Test
    fun profileNoteThatWouldBeIndexedIsRefused() {
        writeNote("Templates/Ticket_Profile.md", parameters)
        val config = ServiceConfig.inVault("/vault", "Templates/Ticket_Profile.md").getOrThrow()

        assertEquals(PROFILE_PATH, loadFailure(config).field)
    }

    @Test
    fun missingProfileNoteIsRefused() {
        val config = ServiceConfig.inVault("/vault", "Templates/Nope.md").getOrThrow()
        assertEquals(PROFILE_PATH, loadFailure(config).field)
    }

    @Test
    fun inlineProfileIsParsed() {
        val config = ServiceConfigParser.parse(
            "vault_root: /vault\nprofile:\n" + parameters.prependIndent("  ")
        ).getOrThrow()

        assertEquals(5, loadProfile(config, fs).getOrThrow().specVersion)
    }

    @Test
    fun invalidParameterInNoteIsNamed() {
        writeNote("Templates/Ticket_Profile.md", parameters.replace("timezone: Europe/Amsterdam", "timezone: Nowhere"))
        val config = ServiceConfig.inVault("/vault", "Templates/Ticket_Profile.md").getOrThrow()

        assertEquals(TIMEZONE, loadFailure(config).field)
    }
}
