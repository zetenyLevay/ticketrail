package dev.zeteny.ticketrail.data.local.database

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TicketDatabaseTest {
    private lateinit var database: TicketDatabase

    @BeforeTest
    fun setUp() {
        database = getRoomDatabase(inMemoryDatabaseBuilder())
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun newDatabaseIsEmpty() = runTest {
        assertEquals(0, database.getTicketDao().count())
    }

    @Test
    fun insertedTicketIsCounted() = runTest {
        database.getTicketDao().insert(sampleTicket())

        assertEquals(1, database.getTicketDao().count())
    }

    @Test
    fun ticketRoundTripsThroughConverters() = runTest {
        val ticket = sampleTicket()
        database.getTicketDao().insert(ticket)

        assertEquals(listOf(ticket), database.getTicketDao().getAllAsFlow().first())
    }
}
