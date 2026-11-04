package dev.zeteny.ticketrail.config

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlException
import com.charleskorn.kaml.YamlMap
import okio.FileSystem
import okio.IOException
import okio.Path

fun loadProfile(config: ServiceConfig, fs: FileSystem): Result<Profile> = runCatching {
    when (val source = config.profile) {
        is ProfileSource.Inline -> ProfileParser.parse(source.yaml).getOrThrow()
        is ProfileSource.InVault -> loadFromNote(config.vaultRoot, source.path, fs)
    }
}

private fun loadFromNote(vaultRoot: Path, profilePath: Path, fs: FileSystem): Profile {
    val markdown = try {
        fs.read(vaultRoot / profilePath) { readUtf8() }
    } catch (e: IOException) {
        invalidProfile(PROFILE_PATH, "can't read profile note '$profilePath' (${e.message})")
    }

    val yamlText = ProfileBlockExtractor.extract(markdown).getOrThrow()
    val parameters = try {
        Yaml.default.parseToYamlNode(yamlText)
    } catch (e: YamlException) {
        invalidProfile(null, "the ${ProfileBlockExtractor.INFO_STRING} block is not valid YAML (${e.message})")
    } as? YamlMap ?: invalidProfile(null, "the ${ProfileBlockExtractor.INFO_STRING} block must be a map of parameters")

    val profile = ProfileParser.parse(parameters).getOrThrow()

    // Spec 1.3: this check can only run now, because excluded_paths comes from the profile itself.
    if (!profile.isExcluded(profilePath)) {
        invalidProfile(PROFILE_PATH, "profile note '$profilePath' must be in a path excluded from indexing, e.g. listed in $EXCLUDED_PATHS")
    }
    return profile
}
