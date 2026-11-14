package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ClockDao {
    // ---- Friend / World City Clocks ----
    @Query("SELECT * FROM friend_clocks ORDER BY id ASC")
    fun getAllFriendClocks(): Flow<List<FriendClock>>

    @Query("SELECT * FROM friend_clocks ORDER BY id ASC")
    suspend fun getFriendClocksDirect(): List<FriendClock>

    @Query("SELECT COUNT(*) FROM friend_clocks")
    suspend fun getFriendCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFriendClock(friendClock: FriendClock)

    @Update
    suspend fun updateFriendClock(friendClock: FriendClock)

    @Delete
    suspend fun deleteFriendClock(friendClock: FriendClock)

    @Query("DELETE FROM friend_clocks WHERE id = :id")
    suspend fun deleteFriendClockById(id: Int)

    @Query("DELETE FROM friend_clocks")
    suspend fun deleteAllFriendClocks()

    // ---- Clock / Chime Settings ----
    @Query("SELECT * FROM clock_settings WHERE id = 1 LIMIT 1")
    fun getSettingsFlow(): Flow<ClockSettings?>

    @Query("SELECT * FROM clock_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettingsDirect(): ClockSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(clockSettings: ClockSettings)

    // ---- Alarms ----
    @Query("SELECT * FROM alarms ORDER BY hour ASC, minute ASC")
    fun getAllAlarms(): Flow<List<AlarmItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlarm(alarm: AlarmItem)

    @Delete
    suspend fun deleteAlarm(alarm: AlarmItem)

    @Update
    suspend fun updateAlarm(alarm: AlarmItem)
}
