package com.able.androidcamera

import android.app.Application
import android.util.Log
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraXConfig

class App:Application(),CameraXConfig.Provider {

    override fun getCameraXConfig(): CameraXConfig {
        return  CameraXConfig.Builder.fromConfig(Camera2Config.defaultConfig())
            //设置日志记录级别为Log.ERROR 避免过多的logcat消息
            .setMinimumLoggingLevel(Log.ERROR).build()
    }
}