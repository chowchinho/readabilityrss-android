package co.chinho.readabilityreader.data.local.mapper

import co.chinho.readabilityreader.data.local.entity.ServerProfileEntity
import co.chinho.readabilityreader.domain.model.ServerProfile

fun ServerProfileEntity.toDomain(): ServerProfile = ServerProfile(
    id = id,
    name = name,
    serverUrl = serverUrl,
    username = username,
    isActive = isActive,
)

fun ServerProfile.toEntity(): ServerProfileEntity = ServerProfileEntity(
    id = id,
    name = name,
    serverUrl = serverUrl,
    username = username,
    isActive = isActive,
)
