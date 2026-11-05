package com.example.smoothcamera.data

import android.content.Context
import com.example.smoothcamera.camera.CameraSettings
import com.example.smoothcamera.camera.RecordingConfig
import com.example.smoothcamera.camera.TransitionCurve
import com.example.smoothcamera.camera.TransitionSettings

class CameraSettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("camera_settings", Context.MODE_PRIVATE)

    fun load(): CameraSettings {
        val codec = prefs.getString("codec", "video/avc") ?: "video/avc"
        val bitrate = prefs.getInt("bitrate", 0).takeIf { it > 0 }
        val curve = runCatching {
            TransitionCurve.valueOf(prefs.getString("curve", TransitionCurve.EASE_IN_OUT.name)!!)
        }.getOrDefault(TransitionCurve.EASE_IN_OUT)

        return CameraSettings(
            recording = RecordingConfig(
                width = prefs.getInt("width", 1920),
                height = prefs.getInt("height", 1080),
                fps = prefs.getInt("fps", 30),
                codecMime = codec,
                bitrate = bitrate,
                recordAudio = prefs.getBoolean("audio", true),
            ),
            transition = TransitionSettings(
                enabled = prefs.getBoolean("transition_enabled", true),
                durationMs = prefs.getLong("transition_duration", 500L),
                curve = curve,
            ),
            stabilizationEnabled = prefs.getBoolean("stabilization", true),
            tapToFocusEnabled = prefs.getBoolean("tap_focus", true),
            microphoneEnabled = prefs.getBoolean("microphone", true),
            gridEnabled = prefs.getBoolean("grid", false),
            exposureCompensation = prefs.getInt("ev", 0),
            debugOverlayEnabled = prefs.getBoolean("debug", false),
        )
    }

    fun save(settings: CameraSettings) {
        prefs.edit()
            .putInt("width", settings.recording.width)
            .putInt("height", settings.recording.height)
            .putInt("fps", settings.recording.fps)
            .putString("codec", settings.recording.codecMime)
            .putInt("bitrate", settings.recording.bitrate ?: 0)
            .putBoolean("audio", settings.recording.recordAudio)
            .putBoolean("transition_enabled", settings.transition.enabled)
            .putLong("transition_duration", settings.transition.durationMs)
            .putString("curve", settings.transition.curve.name)
            .putBoolean("stabilization", settings.stabilizationEnabled)
            .putBoolean("tap_focus", settings.tapToFocusEnabled)
            .putBoolean("microphone", settings.microphoneEnabled)
            .putBoolean("grid", settings.gridEnabled)
            .putInt("ev", settings.exposureCompensation)
            .putBoolean("debug", settings.debugOverlayEnabled)
            .apply()
    }
}
