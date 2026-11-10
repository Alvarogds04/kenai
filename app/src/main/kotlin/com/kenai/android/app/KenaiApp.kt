package com.kenai.android.app

import android.app.Application

class KenaiApp : Application() {
    val model: AppModel by lazy { AppModel(this) }
}
