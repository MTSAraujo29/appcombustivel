package com.fuelcompare.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.fuelcompare.core.database.dao.DetailsSnapshotDao
import com.fuelcompare.core.database.dao.NearbySnapshotDao
import com.fuelcompare.core.database.entity.DetailsSnapshotEntity
import com.fuelcompare.core.database.entity.NearbySnapshotEntity

@Database(
    entities = [NearbySnapshotEntity::class, DetailsSnapshotEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class FuelCompareDatabase : RoomDatabase() {
    abstract fun nearbySnapshotDao(): NearbySnapshotDao
    abstract fun detailsSnapshotDao(): DetailsSnapshotDao

    companion object {
        const val NAME = "fuelcompare.db"
    }
}
