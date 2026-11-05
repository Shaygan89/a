package com.example.smoothcamera.camera

import android.util.Log

object CameraLog {
    private const val TAG = "smoothcamera"

    fun d(message: String) {
        if (com.example.smoothcamera.BuildConfig.DEBUG) Log.d(TAG, message)
    }

    fun w(message: String, throwable: Throwable? = null) {
        if (com.example.smoothcamera.BuildConfig.DEBUG) Log.w(TAG, message, throwable)
    }
}
