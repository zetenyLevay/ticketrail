package dev.zeteny.ticketrail.data

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TicketDao{
    @Insert
    suspend fun insert(item: TicketEntity)

    @Query("SELECT count(*) FROM tickets_db")
    suspend fun count(): Int

    @Query("SELECT * FROM tickets_db")
    fun getAllAsFlow(): Flow<List<TicketEntity>>
}