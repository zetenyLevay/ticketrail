package dev.zeteny.ticketrail.data.local.database

import android.content.Context
import androidx.room3.Room
import androidx.room3.RoomDatabase
import dev.zeteny.ticketrail.data.TicketDatabase

fun getDatabaseBuilder(context: Context): RoomDatabase.Builder<TicketDatabase> {
    val appContext = context.applicationContext
    val dbFile = appContext.getDatabasePath("tickets.db")
    return Room.databaseBuilder<TicketDatabase>(
        context = appContext,
        name = dbFile.absolutePath,
    )
}
