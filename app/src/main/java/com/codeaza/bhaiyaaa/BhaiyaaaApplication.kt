package com.codeaza.bhaiyaaa

import android.app.Application
import com.codeaza.bhaiyaaa.call.VipNotifier

class BhaiyaaaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        VipNotifier.createChannel(this)
    }
}
