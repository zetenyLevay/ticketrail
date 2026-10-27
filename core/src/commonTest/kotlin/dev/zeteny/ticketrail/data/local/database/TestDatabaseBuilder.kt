package dev.zeteny.ticketrail.data.local.database

import androidx.room3.RoomDatabase
import dev.zeteny.ticketrail.data.TicketDatabase

/** In-memory builder, so shared tests never touch the disk. Each platform test source set provides the actual. */
expect fun inMemoryDatabaseBuilder(): RoomDatabase.Builder<TicketDatabase>
