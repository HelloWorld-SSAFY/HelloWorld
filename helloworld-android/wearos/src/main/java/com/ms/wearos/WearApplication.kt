package com.ms.wearos

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class WearApplication : Application() {

    companion object {
        private const val TAG = "WearApplication"
    }

    override fun onCreate() {
        super.onCreate()
        android.util.Log.d(TAG, "WearOS Application started")
    }
}