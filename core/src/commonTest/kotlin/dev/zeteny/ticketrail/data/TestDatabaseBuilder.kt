package dev.zeteny.ticketrail.data

import androidx.room3.RoomDatabase

/** In-memory builder, so shared tests never touch the disk. Each platform test source set provides the actual. */
expect fun inMemoryDatabaseBuilder(): RoomDatabase.Builder<TicketDatabase>
