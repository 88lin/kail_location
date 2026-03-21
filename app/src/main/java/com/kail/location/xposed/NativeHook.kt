package com.kail.location.xposed

import com.kail.location.utils.KailLog

object NativeHook {
    private var isLoaded = false

    fun loadLibrary(path: String): Boolean {
        if (isLoaded && path.endsWith("libkail_native_hook.so")) return true
        return try {
            System.load(path)
            isLoaded = true
            KailLog.i(null, "NativeHook", "Native library loaded successfully from $path")
            true
        } catch (e: Throwable) {
            KailLog.e(null, "NativeHook", "Failed to load native library from $path: ${e.message}")
            throw e
        }
    }

    fun loadLocalLibrary(): Boolean {
        if (isLoaded) return true
        return try {
            System.loadLibrary("kail_native_hook")
            isLoaded = true
            KailLog.i(null, "NativeHook", "Native library loaded successfully (local)")
            true
        } catch (e: Throwable) {
            KailLog.e(null, "NativeHook", "Failed to load local native library: ${e.message}")
            false
        }
    }

    /**
     * 设置传感器 Hook 状态
     */
    external fun setStatus(status: Boolean)

    fun startHook() {
        if (isLoaded) {
            KailLog.i(null, "NativeHook", "Native Hook library loaded and initialized")
        } else {
            KailLog.e(null, "NativeHook", "startHook failed: library not loaded")
        }
    }

    fun setStepConfigSafe(enabled: Boolean, stepsPerMinute: Float) {
        if (isLoaded) {
            try {
                // Now using setStatus from Portal logic
                setStatus(enabled)
            } catch (e: Throwable) {
                KailLog.e(null, "NativeHook", "setStatus failed: ${e.message}")
            }
        } else {
            KailLog.w(null, "NativeHook", "setStatus skipped: library not loaded")
        }
    }
}
