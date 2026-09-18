package com.routina.bite.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 家族基準色，與 Hub、Flow 同值，讓三個 App 放在一起看起來是同一家出的東西。
 * 之後抽 :core:ui 時會整批搬過去；現在各自複製一份。
 */
object FamilyColors {
    val Primary = Color(0xFF4B5699)
    val Secondary = Color(0xFF254C89)
    val Failure = Color(0xFFC62828)
}

/**
 * 三大營養素固定色。低飽和、明暗主題共用同一組值，
 * 顏色只用來分組，真正的辨識依據是旁邊的數字與名稱。
 */
object MacroColors {
    val Protein = Color(0xFF4E8A7B)
    val Fat = Color(0xFFC2895A)
    val Carbs = Color(0xFF8A7BB5)
}
