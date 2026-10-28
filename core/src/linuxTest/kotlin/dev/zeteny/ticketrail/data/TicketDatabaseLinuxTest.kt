package dev.zeteny.ticketrail.data

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import platform.posix.getpid
import platform.posix.setenv
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Checks the real Linux builder, with XDG_DATA_HOME pointed at a temp dir so ~/.local/share is untouched. */
@OptIn(ExperimentalForeignApi::class)
class TicketDatabaseLinuxTest {
    private val dataHome = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "ticketrail-test-${getpid()}"

    @BeforeTest
    fun setUp() {
        setenv("XDG_DATA_HOME", dataHome.toString(), 1)
    }

    @AfterTest
    fun tearDown() {
        FileSystem.SYSTEM.deleteRecursively(dataHome)
    }

    @Test
    fun databaseIsCreatedUnderXdgDataHome() = runTest {
        val database = getRoomDatabase(getDatabaseBuilder())
        try {
            database.getTicketDao().insert(sampleTicket())
            assertEquals(1, database.getTicketDao().count())
        } finally {
            database.close()
        }

        assertTrue(FileSystem.SYSTEM.exists(dataHome / "ticketrail" / "tickets.db"))
    }
}
