package dev.zeteny.ticketrail

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform