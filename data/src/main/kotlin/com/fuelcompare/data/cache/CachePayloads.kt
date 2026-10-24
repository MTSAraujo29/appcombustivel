package com.fuelcompare.data.cache

import com.fuelcompare.core.network.dto.DetailsDataDto
import com.fuelcompare.core.network.dto.NearbyDataDto
import kotlinx.serialization.Serializable

@Serializable
internal data class NearbyCachePayload(
    val data: NearbyDataDto,
    val warnings: List<String> = emptyList(),
)

@Serializable
internal data class DetailsCachePayload(
    val data: DetailsDataDto,
    val warnings: List<String> = emptyList(),
)
