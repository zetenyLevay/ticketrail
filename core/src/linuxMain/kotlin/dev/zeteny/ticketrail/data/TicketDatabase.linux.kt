package dev.zeteny.ticketrail.data

import androidx.room3.Room
import androidx.room3.RoomDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.posix.getenv

// Follows the XDG spec: $XDG_DATA_HOME/ticketrail/tickets.db, falling back to ~/.local/share.
@OptIn(ExperimentalForeignApi::class)
fun getDatabaseBuilder(): RoomDatabase.Builder<TicketDatabase> {
    val dataHome = getenv("XDG_DATA_HOME")?.toKString()?.takeIf { it.isNotBlank() }
        ?: "${getenv("HOME")?.toKString()}/.local/share"
    val dbDir = "$dataHome/ticketrail".toPath()
    FileSystem.SYSTEM.createDirectories(dbDir)
    return Room.databaseBuilder<TicketDatabase>(
        name = (dbDir / "tickets.db").toString(),
    )
}
