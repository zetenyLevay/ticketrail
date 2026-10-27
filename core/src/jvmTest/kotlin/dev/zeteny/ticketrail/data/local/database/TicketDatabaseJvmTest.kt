package dev.zeteny.ticketrail.data.local.database

import dev.zeteny.ticketrail.data.getRoomDatabase
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class TicketDatabaseJvmTest {
    private val dbFile = File(System.getProperty("java.io.tmpdir"), "tickets.db")

    @BeforeTest
    @AfterTest
    fun deleteDatabaseFiles() {
        listOf("", "-wal", "-shm").forEach { File(dbFile.path + it).delete() }
    }

    @Test
    fun databaseIsCreatedOnDisk() = runTest {
        val database = getRoomDatabase(getDatabaseBuilder())
        try {
            database.getTicketDao().insert(sampleTicket())
            assertEquals(1, database.getTicketDao().count())
        } finally {
            database.close()
        }

        assertTrue(dbFile.exists())
    }
}
