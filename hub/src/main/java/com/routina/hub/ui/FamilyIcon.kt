package com.routina.hub.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import com.routina.core.contract.FamilyScanner

/**
 * 取家族成員的 App 圖示並轉成 Compose 畫得出來的點陣圖。
 *
 * 圖示來自別的 App，可能是 adaptive icon 也可能是老式 PNG，
 * 一律 toBitmap 成固定尺寸最省事。轉不出來就回 null，由呼叫端畫替代圖案 ——
 * 少一個圖示不該讓整列成員消失。
 */
@Composable
fun rememberFamilyIcon(packageName: String): ImageBitmap? {
    val context = LocalContext.current
    return remember(packageName) {
        val drawable = FamilyScanner.icon(context, packageName) ?: return@remember null
        val size = drawable.intrinsicWidth.takeIf { it > 0 } ?: ICON_PX
        runCatching { drawable.toBitmap(size, size).asImageBitmap() }.getOrNull()
    }
}

/** 圖示沒有內在尺寸時的退路（例如純色 ColorDrawable） */
private const val ICON_PX = 144
