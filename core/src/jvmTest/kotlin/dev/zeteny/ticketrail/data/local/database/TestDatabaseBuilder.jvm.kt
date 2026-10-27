package dev.zeteny.ticketrail.data.local.database

import androidx.room3.Room
import androidx.room3.RoomDatabase

actual fun inMemoryDatabaseBuilder(): RoomDatabase.Builder<TicketDatabase> =
    Room.inMemoryDatabaseBuilder<TicketDatabase>()
