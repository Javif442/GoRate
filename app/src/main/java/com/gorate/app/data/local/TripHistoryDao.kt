package com.gorate.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TripHistoryDao {
    @Insert
    suspend fun insertTrip(trip: TripHistoryEntity)

    @Query("SELECT * FROM trip_history ORDER BY timestamp DESC")
    fun getAllTrips(): Flow<List<TripHistoryEntity>>

    @Query("DELETE FROM trip_history")
    suspend fun clearHistory()

    @Query("UPDATE trip_history SET note = :newNote WHERE id = :tripId")
    suspend fun updateTripNote(tripId: Long, newNote: String)

    @Query("DELETE FROM trip_history WHERE id = :tripId")
    suspend fun deleteTrip(tripId: Long)

    @Query("DELETE FROM trip_history WHERE id IN (:tripIds)")
    suspend fun deleteTrips(tripIds: List<Long>)

    @Query("DELETE FROM trip_history WHERE price < 0.5 OR price > 500000.0 OR distanceKm < 0.05")
    suspend fun purgeInvalidTrips()
}
