package com.kenai.android.app

import android.app.Application

class KenaiApp : Application() {
    val model: AppModel by lazy { AppModel(this) }

    override fun onCreate() {
        super.onCreate()
        // kenai-0.2 KN-02: a phone that ran this version with the direct providers loses what that version left in it,
        // once. Nothing is logged, ever: what is deleted must not be described anywhere.
        ProviderPurge(AndroidLegacyProviderStorage(this)).run()
    }
}
