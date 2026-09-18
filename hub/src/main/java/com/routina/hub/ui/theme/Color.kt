package com.routina.hub.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 家族基準色。這幾個值與 routina-flow 的 Theme.kt 一致，
 * 讓 Hub 與子 App 放在一起時看起來是同一家出的東西。
 *
 * 之後抽 :core:ui 時這裡會整批搬過去；現在只有 Hub 一個消費者，
 * 先留在 Hub 內，不為了一個使用者先做共用層。
 */
object FamilyColors {
    /** 主色：與 Flow 的主色同值 */
    val Primary = Color(0xFF4B5699)
    val Secondary = Color(0xFF254C89)
    val Failure = Color(0xFFC62828)
}
