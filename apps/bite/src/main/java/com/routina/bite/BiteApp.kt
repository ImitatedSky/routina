package com.routina.bite

import android.app.Application
import com.routina.bite.data.BiteRepository

/**
 * 整個 App 只有一份 [BiteRepository]，掛在 Application 上。
 * 資料量小，不需要 DI 框架，畫面端從 ViewModel 取得。
 */
class BiteApp : Application() {

    lateinit var repository: BiteRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = BiteRepository(this)
    }
}
