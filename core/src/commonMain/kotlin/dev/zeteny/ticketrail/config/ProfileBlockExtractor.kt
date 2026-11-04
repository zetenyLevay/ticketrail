package dev.zeteny.ticketrail.config

/**
 * Finds the YAML inside the single fenced code block with info string `ticket-profile` (spec 1.3).
 * Fences follow CommonMark: three or more backticks or tildes, indented at most three spaces,
 * closed by a fence of the same character that is at least as long.
 */
object ProfileBlockExtractor {

    const val INFO_STRING = "ticket-profile"

    private val OPENING_FENCE = Regex("""^( {0,3})(`{3,}|~{3,})(.*)$""")

    fun extract(markdown: String): Result<String> = runCatching { extractOrThrow(markdown) }

    private fun extractOrThrow(markdown: String): String {
        val lines = markdown.split("\n").map { it.removeSuffix("\r") }
        val blocks = mutableListOf<String>()
        var i = 0
        while (i < lines.size) {
            val open = OPENING_FENCE.matchEntire(lines[i])
            if (open == null) {
                i++
                continue
            }
            val indent = open.groupValues[1].length
            val fence = open.groupValues[2]
            val info = open.groupValues[3].trim()
            if (fence[0] == '`' && '`' in info) {   // not a fence in CommonMark
                i++
                continue
            }

            val closing = Regex("""^ {0,3}${Regex.escape(fence[0].toString())}{${fence.length},}\s*$""")
            val end = (i + 1 until lines.size).firstOrNull { closing.matches(lines[it]) }
            if (info == INFO_STRING) {
                if (end == null) invalidProfile(PROFILE_PATH, "the $INFO_STRING block is never closed")
                blocks += lines.subList(i + 1, end).joinToString("\n") { it.dropIndent(indent) }
            }
            // An unclosed fence runs to the end of the document, so nothing after it is a block.
            i = (end ?: lines.size) + 1
        }

        return when (blocks.size) {
            1 -> blocks.single()
            0 -> invalidProfile(PROFILE_PATH, "the profile note has no $INFO_STRING block")
            else -> invalidProfile(PROFILE_PATH, "the profile note has ${blocks.size} $INFO_STRING blocks, expected exactly one")
        }
    }

    private fun String.dropIndent(indent: Int): String =
        drop(minOf(indent, takeWhile { it == ' ' }.length))
}
