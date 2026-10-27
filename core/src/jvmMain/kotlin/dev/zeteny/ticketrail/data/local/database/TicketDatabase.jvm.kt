package dev.zeteny.ticketrail.data.local.database

import androidx.room3.Room
import androidx.room3.RoomDatabase
import java.io.File

fun getDatabaseBuilder(): RoomDatabase.Builder<TicketDatabase> {
    val dbFile = File(System.getProperty("java.io.tmpdir"), "tickets.db")
    return Room.databaseBuilder<TicketDatabase>(
        name = dbFile.absolutePath,
    )
}
