package com.routina.hub

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.routina.core.contract.CapabilityIntent

/**
 * Hub 的能力收件人：別的家族成員（例如 Routina Flow）要呼叫 Hub 時走這裡。
 *
 * 形狀比照 Flow 的 RunRoutineActivity —— 透明、讀完 extras 就做事、立刻 finish。
 * 呼叫方可能是背景服務，所以任何失敗都只以 Toast 回饋，絕不崩潰：
 * 這是 App 外的入口，壞掉的呼叫不該把 Hub 拖下去。
 */
class CapabilityActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        run(CapabilityIntent.capabilityId(intent))
        finish()
    }

    private fun run(capabilityId: String?) {
        when (capabilityId) {
            CAPABILITY_OPEN_DIRECTORY -> openDirectory()
            null -> toast("呼叫沒有指定要執行哪個能力")
            else -> toast("Routina 沒有這個能力：$capabilityId")
        }
    }

    private fun openDirectory() {
        // NEW_TASK：跳板在自己的 task 裡（taskAffinity=""），要把 Hub 的主 task 帶到前景。
        // 不需要 CLEAR_TOP —— MainActivity 是 singleTop，既有實例會被重用。
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }
            .onFailure { toast("開不起來家族目錄") }
    }

    private fun toast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        /** 與 res/xml/family_capabilities.xml 裡的 id 對應 */
        const val CAPABILITY_OPEN_DIRECTORY = "open_directory"
    }
}
