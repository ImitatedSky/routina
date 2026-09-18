package com.routina.core.contract

/**
 * 子 App 對家族開放的一個能力，例如「新增便條」、「執行例行程序」。
 *
 * 能力是「可以被別人呼叫的動作」，不是畫面。呼叫方（通常是 Routina Flow）
 * 只認得 [id] 與 [params]，不需要知道對方怎麼實作。
 */
data class Capability(
    val id: String,
    val label: String,
    val summary: String = "",
    val params: List<CapabilityParam> = emptyList()
)

/** 能力的一個輸入參數。值在 Intent 裡一律是字串，[type] 只是給呼叫方的編輯提示 */
data class CapabilityParam(
    val name: String,
    val label: String,
    val type: ParamType = ParamType.TEXT,
    val required: Boolean = false
)

enum class ParamType {
    /** 任意文字 */
    TEXT,

    /** 數字。呼叫方可以給數字鍵盤，但傳過去仍是字串（值可能來自變數代換） */
    NUMBER;

    companion object {
        /** XML 裡寫不認得的 type 時當成文字，不要讓整份能力清單讀不進來 */
        fun from(raw: String?): ParamType = when (raw?.lowercase()) {
            "number" -> NUMBER
            else -> TEXT
        }
    }
}
