package dev.zeteny.ticketrail.config

import com.charleskorn.kaml.YamlMap
import okio.Path
import okio.Path.Companion.toPath
import org.koin.core.logger.MESSAGE




data class ServiceConfig(
    val vaultRoot: Path,
    val profile: ProfileSource
) {
    companion object {
        fun inVault(vaultRoot: String, profilePath: String): Result<ServiceConfig> = runCatching{
            ServiceConfig(
                vaultRoot = vaultRootOf(vaultRoot),
                profile = ProfileSource.InVault(profilePathOf(profilePath)),
            )
        }
    }
}

sealed interface ProfileSource {
    data class InVault(val path: Path) : ProfileSource
    data class Inline(val yaml: YamlMap) : ProfileSource
}

interface ServiceConfigStore {
    suspend fun load(): ServiceConfig?
    suspend fun save(config: ServiceConfig)
}

internal val VAULT_ROOT = "vault_root"
internal val PROFILE = "profile"
internal val PROFILE_PATH = "profile_path"

internal fun vaultRootOf(value: String): Path {
    val path = value.trim().ifEmpty { fail(VAULT_ROOT, "$VAULT_ROOT must not be empty") }.toPath()
    if (!path.isAbsolute) fail(VAULT_ROOT, "$VAULT_ROOT must be an absolute path, got '$value'")
    return path
}

internal fun profilePathOf(value: String): Path {
    val path = value.trim().ifEmpty { fail(PROFILE_PATH, "$PROFILE_PATH must not be empty") }.toPath()
    if (path.isAbsolute) fail(PROFILE_PATH, "$PROFILE_PATH must be relative to $VAULT_ROOT, got '$value'")
    if (".." in path.segments) fail(PROFILE_PATH, "$PROFILE_PATH must stay inside the vault, got '$value'")
    return path
}

internal fun fail(field: String?, message: String): Nothing =
    throw ServiceConfigException(field, "service config: $message")

class ServiceConfigException(val field: String?, message: String): Exception(message)