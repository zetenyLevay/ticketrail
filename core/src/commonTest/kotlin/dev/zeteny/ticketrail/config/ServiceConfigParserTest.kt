package dev.zeteny.ticketrail.config

import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ServiceConfigParserTest {

    private fun parseFailure(yaml: String): ServiceConfigException {
        val result = ServiceConfigParser.parse(yaml)
        assertTrue(result.isFailure, "expected a failure for:\n$yaml")
        return assertIs<ServiceConfigException>(result.exceptionOrNull())
    }

    @Test
    fun profilePathGivesInVaultSource() {
        val config = ServiceConfigParser.parse(
            """
            vault_root: /home/me/Vault
            profile_path: Templates/Ticket_Profile.md
            """.trimIndent()
        ).getOrThrow()

        assertEquals("/home/me/Vault".toPath(), config.vaultRoot)
        assertEquals(ProfileSource.InVault("Templates/Ticket_Profile.md".toPath()), config.profile)
    }

    @Test
    fun inlineProfileGivesInlineSource() {
        val config = ServiceConfigParser.parse(
            """
            vault_root: /home/me/Vault
            profile:
              spec_version: 4
              queues: [home, work, study]
            """.trimIndent()
        ).getOrThrow()

        val inline = assertIs<ProfileSource.Inline>(config.profile)
        assertEquals("4", inline.yaml.getScalar("spec_version")?.content)
    }

    @Test
    fun missingVaultRootIsRefused() {
        val e = parseFailure("profile_path: Templates/Ticket_Profile.md")
        assertEquals(VAULT_ROOT, e.field)
    }

    @Test
    fun emptyVaultRootIsRefused() {
        val e = parseFailure("vault_root:\nprofile_path: Templates/Ticket_Profile.md")
        assertEquals(VAULT_ROOT, e.field)
    }

    @Test
    fun relativeVaultRootIsRefused() {
        val e = parseFailure("vault_root: Vault\nprofile_path: Templates/Ticket_Profile.md")
        assertEquals(VAULT_ROOT, e.field)
    }


    @Test
    fun neitherProfileNorPathIsRefused() {
        val e = parseFailure("vault_root: /home/me/Vault")
        assertTrue("one of profile or profile_path" in e.message!!)
    }

    @Test
    fun bothProfileAndPathAreRefused() {
        val e = parseFailure(
            """
            vault_root: /home/me/Vault
            profile_path: Templates/Ticket_Profile.md
            profile:
              spec_version: 4
            """.trimIndent()
        )
        assertTrue("not both" in e.message!!)
    }

    @Test
    fun absoluteProfilePathIsRefused() {
        val e = parseFailure("vault_root: /home/me/Vault\nprofile_path: /home/me/Vault/Profile.md")
        assertEquals(PROFILE_PATH, e.field)
    }

    @Test
    fun profilePathLeavingTheVaultIsRefused() {
        val e = parseFailure("vault_root: /home/me/Vault\nprofile_path: ../Profile.md")
        assertEquals(PROFILE_PATH, e.field)
    }

    @Test
    fun scalarProfileIsRefused() {
        val e = parseFailure("vault_root: /home/me/Vault\nprofile: Templates/Ticket_Profile.md")
        assertEquals(PROFILE, e.field)
    }


    @Test
    fun invalidYamlIsRefused() {
        val e = parseFailure("vault_root: [unclosed")
        assertEquals(null, e.field)
    }

    @Test
    fun nonMapDocumentIsRefused() {
        val e = parseFailure("- just\n- a list")
        assertEquals(null, e.field)
    }
}