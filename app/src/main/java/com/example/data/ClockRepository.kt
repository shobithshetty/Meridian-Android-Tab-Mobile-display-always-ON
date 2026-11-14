package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ClockRepository(private val clockDao: ClockDao) {

    val allFriendClocks: Flow<List<FriendClock>> = clockDao.getAllFriendClocks()

    val settings: Flow<ClockSettings> = clockDao.getSettingsFlow().map { saved ->
        saved ?: ClockSettings()
    }

    suspend fun getSettingsDirect(): ClockSettings {
        return clockDao.getSettingsDirect() ?: ClockSettings()
    }

    suspend fun getFriendClocksDirect(): List<FriendClock> {
        return clockDao.getFriendClocksDirect()
    }

    suspend fun saveSettings(clockSettings: ClockSettings) {
        clockDao.saveSettings(clockSettings)
    }

    suspend fun insertFriend(friendClock: FriendClock) {
        clockDao.insertFriendClock(friendClock)
    }

    suspend fun updateFriend(friendClock: FriendClock) {
        clockDao.updateFriendClock(friendClock)
    }

    suspend fun deleteFriend(friendClock: FriendClock) {
        clockDao.deleteFriendClock(friendClock)
    }

    suspend fun deleteFriendById(id: Int) {
        clockDao.deleteFriendClockById(id)
    }

    suspend fun deleteAllFriends() {
        clockDao.deleteAllFriendClocks()
    }

    val allAlarms: Flow<List<AlarmItem>> = clockDao.getAllAlarms()

    suspend fun insertAlarm(alarm: AlarmItem) {
        clockDao.insertAlarm(alarm)
    }

    suspend fun deleteAlarm(alarm: AlarmItem) {
        clockDao.deleteAlarm(alarm)
    }

    suspend fun updateAlarm(alarm: AlarmItem) {
        clockDao.updateAlarm(alarm)
    }

    /**
     * Seeds default settings and starter world cities strictly once on initial install
     * (when settings record does not yet exist), never overwriting or deleting user-added cities.
     */
    suspend fun checkAndPrepopulate() {
        val currentSettings = clockDao.getSettingsDirect()
        if (currentSettings == null) {
            clockDao.saveSettings(ClockSettings())
        }
        val existingClocks = clockDao.getFriendClocksDirect()
        val standardLocations = listOf(
            FriendClock(
                friendName = "Toronto",
                timezoneId = "America/Toronto",
                cityLabel = "Toronto, Canada",
                showInWidget = true
            ),
            FriendClock(
                friendName = "Bangalore",
                timezoneId = "Asia/Kolkata",
                cityLabel = "Bangalore, India",
                showInWidget = true
            ),
            FriendClock(
                friendName = "Germany",
                timezoneId = "Europe/Berlin",
                cityLabel = "Berlin, Germany",
                showInWidget = true
            ),
            FriendClock(
                friendName = "Qatar",
                timezoneId = "Asia/Qatar",
                cityLabel = "Doha, Qatar",
                showInWidget = true
            ),
            FriendClock(
                friendName = "Kuwait",
                timezoneId = "Asia/Kuwait",
                cityLabel = "Kuwait City, Kuwait",
                showInWidget = true
            ),
            FriendClock(
                friendName = "Saudi Arabia",
                timezoneId = "Asia/Riyadh",
                cityLabel = "Riyadh, Saudi Arabia",
                showInWidget = true
            ),
            FriendClock(
                friendName = "London",
                timezoneId = "Europe/London",
                cityLabel = "London, UK",
                showInWidget = true
            ),
            FriendClock(
                friendName = "New York",
                timezoneId = "America/New_York",
                cityLabel = "New York, USA",
                showInWidget = true
            ),
            FriendClock(
                friendName = "Tokyo",
                timezoneId = "Asia/Tokyo",
                cityLabel = "Tokyo, Japan",
                showInWidget = true
            )
        )
        if (existingClocks.isEmpty()) {
            for (location in standardLocations) {
                clockDao.insertFriendClock(location)
            }
        } else {
            // Ensure newly requested cities and countries are added if not already present in user list
            val requestedLocations = standardLocations.take(6)
            for (location in requestedLocations) {
                val exists = existingClocks.any {
                    it.friendName.equals(location.friendName, ignoreCase = true) ||
                    it.cityLabel.contains(location.friendName, ignoreCase = true)
                }
                if (!exists) {
                    clockDao.insertFriendClock(location)
                }
            }
        }
    }
}
