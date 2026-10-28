package dev.zeteny.ticketrail.config

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlException
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlNull
import com.charleskorn.kaml.YamlScalar

object ServiceConfigParser {

    fun parse(yaml: String): Result<ServiceConfig> = runCatching { parseOrThrow(yaml) }

    private fun parseOrThrow(yaml: String): ServiceConfig {
        val root = try {
            Yaml.default.parseToYamlNode(yaml)
        } catch (e: YamlException) {
            fail(null, "not valid YAML (${e.message})")
        }
        val map = root as? YamlMap ?: fail(null, "must be a map of keys, like '$VAULT_ROOT: /path'")

        val vaultRoot = vaultRootOf(map.requireString(VAULT_ROOT))

        val hasProfile = map.getKey(PROFILE) != null
        val hasProfilePath = map.getKey(PROFILE_PATH) != null
        val profile = when {
            hasProfile && hasProfilePath ->
                fail(null, "set exactly one of $PROFILE or $PROFILE_PATH, not both")
            hasProfilePath ->
                ProfileSource.InVault(profilePathOf(map.requireString(PROFILE_PATH)))
            hasProfile -> {
                val parameters = map.get<YamlNode>(PROFILE) as? YamlMap
                    ?: fail(PROFILE, "$PROFILE must be a map of profile parameters")
                ProfileSource.Inline(parameters)
            }
            else ->
                fail(null, "set one of $PROFILE or $PROFILE_PATH")
        }

        return ServiceConfig(vaultRoot, profile)
    }

    private fun YamlMap.requireString(key: String): String =
        when (val node = get<YamlNode>(key)) {
            null, is YamlNull -> fail(key, "$key is required")
            is YamlScalar -> node.content
            else -> fail(key, "$key must be a single value, not a list or map")
        }
}