package com.abito.android.app

import android.app.Application

class AbitoApp : Application() {
    val model: AppModel by lazy { AppModel(this) }
}
