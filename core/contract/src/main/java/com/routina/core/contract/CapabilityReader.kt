package com.routina.core.contract

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.content.res.XmlResourceParser
import org.xmlpull.v1.XmlPullParser

/**
 * 讀子 App 宣告的能力清單 XML。檔案住在對方的 APK 裡，
 * 用 [PackageManager.getResourcesForApplication] 借對方的 Resources 來解。
 *
 * XML 長這樣（屬性不帶 android: 命名空間，與 FileProvider 的 file_paths.xml 同樣作法）：
 * ```
 * <capabilities contract="1">
 *     <capability id="add_note" label="新增便條" summary="把文字存成一張新便條">
 *         <param name="text" label="內容" type="text" required="true" />
 *     </capability>
 * </capabilities>
 * ```
 *
 * label/summary 可以直接寫文字，也可以寫 @string 參照 —— 兩種都支援，
 * 這樣子 App 要做多語系時不必改契約。
 */
internal object CapabilityReader {

    fun read(pm: PackageManager, appInfo: ApplicationInfo, resId: Int): List<Capability> =
        runCatching {
            val res = pm.getResourcesForApplication(appInfo)
            val parser = res.getXml(resId)
            try {
                parse(parser, res)
            } finally {
                parser.close()
            }
        }.getOrDefault(emptyList())

    private fun parse(parser: XmlResourceParser, res: Resources): List<Capability> {
        val capabilities = mutableListOf<Capability>()

        // 邊掃邊累積：遇到 capability 開一筆、遇到 param 往當前這筆加、
        // 遇到 capability 收尾就收一筆。缺 id 的整筆丟掉（沒有 id 就無法被呼叫）。
        var id: String? = null
        var label: String? = null
        var summary = ""
        var params = mutableListOf<CapabilityParam>()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    TAG_CAPABILITY -> {
                        id = attr(parser, res, "id")
                        label = attr(parser, res, "label")
                        summary = attr(parser, res, "summary").orEmpty()
                        params = mutableListOf()
                    }

                    TAG_PARAM -> {
                        val name = attr(parser, res, "name")
                        if (!name.isNullOrBlank()) {
                            params.add(
                                CapabilityParam(
                                    name = name,
                                    label = attr(parser, res, "label")
                                        ?.takeIf { it.isNotBlank() } ?: name,
                                    type = ParamType.from(attr(parser, res, "type")),
                                    required = parser.getAttributeBooleanValue(
                                        null,
                                        "required",
                                        false
                                    )
                                )
                            )
                        }
                    }
                }

                XmlPullParser.END_TAG -> if (parser.name == TAG_CAPABILITY) {
                    val capabilityId = id
                    if (!capabilityId.isNullOrBlank()) {
                        capabilities.add(
                            Capability(
                                id = capabilityId,
                                label = label?.takeIf { it.isNotBlank() } ?: capabilityId,
                                summary = summary,
                                params = params.toList()
                            )
                        )
                    }
                    id = null
                }
            }
            event = parser.next()
        }
        return capabilities
    }

    /**
     * 取屬性值，同時支援字面文字與字串資源參照。
     * AAPT 會把資源參照編成 id，所以先問 id、問不到才當字面值。
     */
    private fun attr(parser: XmlResourceParser, res: Resources, name: String): String? {
        val resId = parser.getAttributeResourceValue(null, name, 0)
        if (resId != 0) {
            return runCatching { res.getString(resId) }.getOrNull()
        }
        return parser.getAttributeValue(null, name)
    }

    private const val TAG_CAPABILITY = "capability"
    private const val TAG_PARAM = "param"
}
