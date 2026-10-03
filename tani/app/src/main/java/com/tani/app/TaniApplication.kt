package com.tani.app

import android.app.Application
import com.tani.app.data.Supabase
import com.tani.app.data.notifications.PushMessaging

class TaniApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Supabase.init(this)
        PushMessaging.initialize(this)
    }
}
