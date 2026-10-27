package dev.zeteny.ticketrail.data

import androidx.room3.ColumnTypeConverters
import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

@Database(entities = [TicketEntity::class], version = 1)
@ConstructedBy(TicketDatabaseConstructor::class)
@ColumnTypeConverters(Converters::class)
abstract class TicketDatabase : RoomDatabase(){
    abstract fun getTicketDao(): TicketDao
}

@Suppress("KotlinNoActualForExpect")
expect object TicketDatabaseConstructor : RoomDatabaseConstructor<TicketDatabase> {
    override fun initialize(): TicketDatabase
}

fun getRoomDatabase(
    builder: RoomDatabase.Builder<TicketDatabase>
): TicketDatabase {
    return builder
        .addMigrations()
        .fallbackToDestructiveMigrationOnDowngrade(true)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}

fun getTicketDao(ticketDatabase: TicketDatabase) = ticketDatabase.getTicketDao()