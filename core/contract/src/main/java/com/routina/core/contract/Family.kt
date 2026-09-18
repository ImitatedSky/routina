package com.routina.core.contract

/**
 * 家族契約：Hub 與子 App 之間唯一的約定。
 *
 * 刻意只用 manifest meta-data 與 Intent extras —— 都是字串層級的格式約定，
 * 不是程式碼依賴。子 App 因此可以待在自己的 repo、用自己的版本節奏，
 * 不必引用這個 library 也能掛進家族（照著下面的鍵名寫 manifest 就行）。
 *
 * 子 App 的 manifest 宣告長這樣：
 * ```
 * <application ...>
 *     <meta-data android:name="com.routina.family.member"  android:value="true" />
 *     <meta-data android:name="com.routina.family.id"      android:value="flow" />
 *     <meta-data android:name="com.routina.family.name"    android:value="Routina Flow" />
 *     <meta-data android:name="com.routina.family.summary" android:value="日常自動化" />
 *     <!-- 選用：對外開放的能力清單 -->
 *     <meta-data android:name="com.routina.family.capabilities"
 *                android:resource="@xml/family_capabilities" />
 * </application>
 * ```
 */
object Family {

    /** 契約版本。日後若改動鍵名或 Intent 形狀才會 +1，讓兩邊分得出「讀不懂」與「舊但可讀」 */
    const val CONTRACT_VERSION = 1

    // ---- manifest meta-data 鍵名 ----

    /** 必填，`android:value="true"`。沒有這個鍵的 App 一律不視為家族成員 */
    const val META_MEMBER = "com.routina.family.member"

    /** 必填，家族內的短識別（例：`flow`）。與 applicationId 無關，改套件名不影響它 */
    const val META_ID = "com.routina.family.id"

    /** 選填，顯示名稱。沒填就退回系統的 App 標籤 */
    const val META_NAME = "com.routina.family.name"

    /** 選填，一行說明 */
    const val META_SUMMARY = "com.routina.family.summary"

    /** 選填，指向 XML 資源的能力清單（見 [CapabilityReader]） */
    const val META_CAPABILITIES = "com.routina.family.capabilities"

    // ---- 呼叫能力用的 Intent ----

    /** 請子 App 執行某個能力。收件人必須是 exported 的跳板 Activity */
    const val ACTION_RUN_CAPABILITY = "com.routina.family.action.RUN_CAPABILITY"

    /** 要執行哪個能力：值是能力清單裡的 `id` */
    const val EXTRA_CAPABILITY_ID = "com.routina.family.extra.CAPABILITY_ID"

    /**
     * 參數的 extras 前綴：`com.routina.family.param.<參數名>`，值一律字串。
     *
     * 用前綴字串而不是包一層 Bundle，是為了跨 App 邊界時不可能出現
     * 反序列化失敗（對方沒有我們的類別），而且可以直接用 adb 下指令測：
     * `adb shell am start -a ... --es com.routina.family.param.text hello`
     */
    const val EXTRA_PARAM_PREFIX = "com.routina.family.param."

    /** Hub 自己的套件名。掃描時要把自己排除在成員清單外 */
    const val HUB_PACKAGE = "com.routina.hub"
}
