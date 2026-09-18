package com.routina.bite

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.routina.core.contract.CapabilityIntent

/**
 * Bite 的能力收件人：別的家族成員要呼叫 Bite 時走這裡。
 *
 * 透明、讀完 extras 就做事、立刻 finish。呼叫方可能是背景服務，
 * 所以任何失敗都只以 Toast 回饋，絕不崩潰。
 */
class CapabilityActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        run(CapabilityIntent.capabilityId(intent))
        finish()
    }

    private fun run(capabilityId: String?) {
        when (capabilityId) {
            CAPABILITY_OPEN_TODAY -> openToday()
            null -> toast(getString(R.string.capability_missing))
            else -> toast(getString(R.string.capability_unknown, capabilityId))
        }
    }

    private fun openToday() {
        // NEW_TASK：跳板在自己的 task 裡（taskAffinity=""），要把 Bite 的主 task 帶到前景。
        // 不需要 CLEAR_TOP —— MainActivity 是 singleTop，既有實例會被重用，
        // 帶著 EXTRA_OPEN_TODAY 讓它從其他畫面切回今日頁。
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(MainActivity.EXTRA_OPEN_TODAY, true)
        runCatching { startActivity(intent) }
            .onFailure { toast(getString(R.string.capability_open_failed)) }
    }

    private fun toast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        /** 與 res/xml/family_capabilities.xml 裡的 id 對應 */
        const val CAPABILITY_OPEN_TODAY = "open_today"
    }
}
