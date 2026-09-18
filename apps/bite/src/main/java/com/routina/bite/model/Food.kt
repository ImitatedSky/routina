package com.routina.bite.model

import kotlinx.serialization.Serializable

/**
 * 一份食物的營養標示。鈉與膽固醇的單位是毫克，其餘是公克，熱量是大卡。
 * 欄位名與 assets/seed_foods.json、備份檔完全一致。
 */
@Serializable
data class Nutrients(
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val fat: Double = 0.0,
    val satFat: Double = 0.0,
    val transFat: Double = 0.0,
    val carbs: Double = 0.0,
    val sugar: Double = 0.0,
    val sodium: Double = 0.0,
    val cholesterol: Double = 0.0
) {
    operator fun times(factor: Double) = Nutrients(
        kcal = kcal * factor,
        protein = protein * factor,
        fat = fat * factor,
        satFat = satFat * factor,
        transFat = transFat * factor,
        carbs = carbs * factor,
        sugar = sugar * factor,
        sodium = sodium * factor,
        cholesterol = cholesterol * factor
    )

    operator fun plus(other: Nutrients) = Nutrients(
        kcal = kcal + other.kcal,
        protein = protein + other.protein,
        fat = fat + other.fat,
        satFat = satFat + other.satFat,
        transFat = transFat + other.transFat,
        carbs = carbs + other.carbs,
        sugar = sugar + other.sugar,
        sodium = sodium + other.sodium,
        cholesterol = cholesterol + other.cholesterol
    )

    companion object {
        val EMPTY = Nutrients()
    }
}

/**
 * 食物庫的一筆。[nutrients] 是「每一份」的量；
 * [servingGrams] 為 null 表示這個食物沒有標示每份幾公克，只能用份數記錄。
 */
@Serializable
data class Food(
    val id: String,
    val name: String,
    val servingGrams: Double? = null,
    val nutrients: Nutrients,
    val note: String = "",
    val favorite: Boolean = false,
    val createdAt: Long
)
