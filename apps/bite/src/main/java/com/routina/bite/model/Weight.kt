package com.routina.bite.model

import kotlinx.serialization.Serializable

@Serializable
enum class WeightSource { HOME_SCALE, INBODY }

@Serializable
data class WeightEntry(
    val id: String,
    val date: String,
    val kg: Double,
    val bodyFatPct: Double? = null,
    val source: WeightSource = WeightSource.HOME_SCALE,
    val note: String = ""
)
