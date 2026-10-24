package com.fuelcompare.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "nearby_snapshots",
    indices = [Index(value = ["savedAtEpochMillis"])],
)
data class NearbySnapshotEntity(
    @PrimaryKey val cacheKey: String,
    val payload: String,
    val savedAtEpochMillis: Long,
)

@Entity(
    tableName = "details_snapshots",
    indices = [Index(value = ["savedAtEpochMillis"])],
)
data class DetailsSnapshotEntity(
    @PrimaryKey val stationId: String,
    val payload: String,
    val savedAtEpochMillis: Long,
)
