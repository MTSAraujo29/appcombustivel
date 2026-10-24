package com.fuelcompare.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fuelcompare.core.database.entity.DetailsSnapshotEntity
import com.fuelcompare.core.database.entity.NearbySnapshotEntity

@Dao
interface NearbySnapshotDao {
    @Query("SELECT * FROM nearby_snapshots WHERE cacheKey = :cacheKey LIMIT 1")
    suspend fun find(cacheKey: String): NearbySnapshotEntity?

    @Query("SELECT * FROM nearby_snapshots ORDER BY savedAtEpochMillis DESC LIMIT 1")
    suspend fun latest(): NearbySnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(snapshot: NearbySnapshotEntity)

    @Query(
        "DELETE FROM nearby_snapshots WHERE cacheKey NOT IN " +
            "(SELECT cacheKey FROM nearby_snapshots ORDER BY savedAtEpochMillis DESC LIMIT 10)",
    )
    suspend fun trim()

    @Query("DELETE FROM nearby_snapshots")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM nearby_snapshots")
    suspend fun count(): Int
}

@Dao
interface DetailsSnapshotDao {
    @Query("SELECT * FROM details_snapshots WHERE stationId = :stationId LIMIT 1")
    suspend fun find(stationId: String): DetailsSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(snapshot: DetailsSnapshotEntity)

    @Query(
        "DELETE FROM details_snapshots WHERE stationId NOT IN " +
            "(SELECT stationId FROM details_snapshots ORDER BY savedAtEpochMillis DESC LIMIT 200)",
    )
    suspend fun trim()

    @Query("DELETE FROM details_snapshots WHERE stationId = :stationId")
    suspend fun delete(stationId: String)

    @Query("DELETE FROM details_snapshots")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM details_snapshots")
    suspend fun count(): Int
}
