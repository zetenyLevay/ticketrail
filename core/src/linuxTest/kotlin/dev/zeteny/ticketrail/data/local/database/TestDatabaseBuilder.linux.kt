package dev.zeteny.ticketrail.data.local.database

import androidx.room3.Room
import androidx.room3.RoomDatabase
import dev.zeteny.ticketrail.data.TicketDatabase

actual fun inMemoryDatabaseBuilder(): RoomDatabase.Builder<TicketDatabase> =
    Room.inMemoryDatabaseBuilder<TicketDatabase>()
