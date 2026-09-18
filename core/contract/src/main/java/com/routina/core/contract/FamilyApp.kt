package com.routina.core.contract

/**
 * 一個已安裝的家族成員。
 *
 * 刻意不帶 Drawable —— 圖示會隨主題／密度變，放進資料類別等於把一份可能過時的
 * 點陣圖釘在記憶體裡。要圖示時另外問 [FamilyScanner.icon]。
 */
data class FamilyApp(
    val packageName: String,
    /** 家族內的短識別，例 `flow`。取自 meta-data，缺少時退回 packageName */
    val familyId: String,
    val name: String,
    val summary: String,
    val versionName: String,
    /** 這個成員對外開放的能力；沒宣告就是空清單 */
    val capabilities: List<Capability> = emptyList()
)
