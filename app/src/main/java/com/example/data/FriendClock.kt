package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "friend_clocks")
data class FriendClock(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val friendName: String,
    val timezoneId: String,  // e.g. "Asia/Tokyo"
    val cityLabel: String,   // e.g. "Tokyo"
    val showInWidget: Boolean = true // Controls visibility in compact in-app & Home Screen widgets
)
