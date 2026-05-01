package com.kail.location.xposed.core

import android.os.Bundle
import com.kail.location.utils.KailLog
import com.kail.location.xposed.hooks.LocationServiceHook
import com.kail.location.xposed.utils.FakeLoc
import kotlin.random.Random

internal object KailCommandHandler {
    private const val PROVIDER = "kail"
    private val keyRef = java.util.concurrent.atomic.AtomicReference<String?>(null)

    fun handle(provider: String?, command: String?, out: Bundle?): Boolean {
        if (provider != PROVIDER) return false
        if (out == null) return false
        if (command.isNullOrBlank()) return false

        if (command == "exchange_key") {
            val key = "k${Random.nextInt(100000, 999999)}${System.nanoTime()}"
            keyRef.set(key)
            out.putString("key", key)
            KailLog.d(null, "XPOSED", "PORTAL接收：交换密钥", isHighFrequency = true)
            return true
        }

        val key = keyRef.get() ?: return false
        if (command != key) return false

        val commandId = out.getString("command_id") ?: return false
        when (commandId) {
            "is_start" -> {
                out.putBoolean("is_start", FakeLocState.isEnabled())
                KailLog.d(null, "XPOSED", "PORTAL接收：查询启动状态 is_start=${FakeLocState.isEnabled()}")
                return true
            }
            "start" -> {
                FakeLocState.setEnabled(true)
                out.putBoolean("started", true)
                out.getDouble("altitude", Double.NaN).let { if (!it.isNaN()) FakeLocState.setAltitude(it) }
                KailLog.d(null, "XPOSED", "PORTAL接收：启动仿真 altitude=${out.getDouble("altitude", Double.NaN)}")
                return true
            }
            "stop" -> {
                FakeLocState.setEnabled(false)
                out.putBoolean("stopped", true)
                KailLog.d(null, "XPOSED", "PORTAL接收：停止仿真")
                return true
            }
            "get_location" -> {
                val loc = FakeLocState.injectInto(null)
                if (loc != null) {
                    out.putDouble("lat", loc.latitude)
                    out.putDouble("lon", loc.longitude)
                    out.putBoolean("ok", true)
                    KailLog.d(null, "XPOSED", "PORTAL接收：获取位置 lat=${loc.latitude} lon=${loc.longitude}", isHighFrequency = true)
                    return true
                }
                KailLog.d(null, "XPOSED", "PORTAL接收：获取位置失败", isHighFrequency = true)
                return false
            }
            "get_listener_size" -> {
                out.putInt("size", LocationServiceHook.locationListeners.size)
                KailLog.d(null, "XPOSED", "PORTAL接收：监听器数量 size=${LocationServiceHook.locationListeners.size}", isHighFrequency = true)
                return true
            }
            "broadcast_location" -> {
                LocationServiceHook.callOnLocationChanged()
                out.putBoolean("ok", true)
                KailLog.d(null, "XPOSED", "PORTAL接收：广播当前位置", isHighFrequency = true)
                return true
            }
            "set_speed" -> {
                val speed = out.getFloat("speed", 0f)
                FakeLocState.setSpeed(speed)
                out.putBoolean("ok", true)
                KailLog.d(null, "XPOSED", "PORTAL接收：设置速度 speed=$speed", isHighFrequency = true)
                return true
            }
            "set_bearing" -> {
                val bearing = out.getDouble("bearing", 0.0).toFloat()
                FakeLocState.setBearing(bearing)
                out.putBoolean("ok", true)
                KailLog.d(null, "XPOSED", "PORTAL接收：设置航向 bearing=$bearing", isHighFrequency = true)
                return true
            }
            "set_altitude" -> {
                val altitude = out.getDouble("altitude", Double.NaN)
                if (altitude.isNaN()) return false
                FakeLocState.setAltitude(altitude)
                out.putBoolean("ok", true)
                KailLog.d(null, "XPOSED", "PORTAL接收：设置海拔 altitude=$altitude", isHighFrequency = true)
                return true
            }
            "update_location" -> {
                val lat = out.getDouble("lat", Double.NaN)
                val lon = out.getDouble("lon", Double.NaN)
                if (lat.isNaN() || lon.isNaN()) return false
                FakeLocState.updateLocation(lat, lon)
                out.putBoolean("ok", true)
                KailLog.d(null, "XPOSED", "PORTAL接收：更新位置 lat=$lat lon=$lon", isHighFrequency = true)
                return true
            }
            "set_step_enabled" -> {
                val enabled = out.getBoolean("enabled", false)
                FakeLocState.setStepEnabled(enabled)
                out.putBoolean("ok", true)
                KailLog.d(null, "XPOSED", "PORTAL接收：步频开关 enabled=$enabled", isHighFrequency = true)
                return true
            }
            "set_step_cadence" -> {
                val cadence = out.getFloat("cadence", 0f)
                val spm = if (cadence <= 10f) cadence * 60f else cadence
                FakeLocState.setStepCadenceSpm(spm)
                out.putBoolean("ok", true)
                KailLog.d(null, "XPOSED", "PORTAL接收：步频 cadence=$cadence", isHighFrequency = true)
                return true
            }
            "load_library" -> {
                val path = out.getString("path") ?: return false
                val writeOffset = out.getString("write_offset") ?: ""
                try {
                    val result = FakeLocState.loadNativeLibrary(path, writeOffset)
                    out.putBoolean("ok", result.first)
                    out.putString("result", result.second)
                    KailLog.d(null, "XPOSED", "PORTAL接收：加载SO库 path=$path write_offset=$writeOffset result=${result.second}")
                    KailLog.i(null, "NativeHook", "Library load result: ${result.first}, write_offset=$writeOffset")
                } catch (e: Throwable) {
                    out.putBoolean("ok", false)
                    out.putString("result", e.message ?: "unknown error")
                    KailLog.e(null, "XPOSED", "PORTAL接收：加载SO库失败 path=$path error=${e.message}")
                    KailLog.e(null, "NativeHook", "Library load error: ${e.message}")
                }
                return true
            }
            "set_route_simulation" -> {
                val active = out.getBoolean("active", false)
                val spm = out.getFloat("spm", 120f)
                val mode = out.getInt("mode", 0)
                try {
                    FakeLocState.setRouteSimulation(active, spm, mode)
                    out.putBoolean("ok", true)
                    KailLog.d(null, "XPOSED", "PORTAL接收：路线模拟 active=$active spm=$spm mode=$mode")
                } catch (e: Throwable) {
                    out.putBoolean("ok", false)
                    KailLog.e(null, "XPOSED", "PORTAL接收：设置路线模拟失败 error=${e.message}")
                }
                return true
            }
            "set_config" -> {
                try {
                    out.getBoolean("enableMockGnss", FakeLoc.enableMockGnss).let { FakeLoc.enableMockGnss = it }
                    out.getBoolean("enableMockWifi", FakeLoc.enableMockWifi).let { FakeLoc.enableMockWifi = it }
                    out.getBoolean("disableGetCurrentLocation", FakeLoc.disableGetCurrentLocation).let { FakeLoc.disableGetCurrentLocation = it }
                    out.getBoolean("disableRegisterLocationListener", FakeLoc.disableRegisterLocationListener).let { FakeLoc.disableRegisterLocationListener = it }
                    out.getBoolean("disableFusedLocation", FakeLoc.disableFusedLocation).let { FakeLoc.disableFusedLocation = it }
                    out.getBoolean("disableNetworkLocation", FakeLoc.disableNetworkLocation).let { FakeLoc.disableNetworkLocation = it }
                    out.getBoolean("disableRequestGeofence", FakeLoc.disableRequestGeofence).let { FakeLoc.disableRequestGeofence = it }
                    out.getBoolean("disableGetFromLocation", FakeLoc.disableGetFromLocation).let { FakeLoc.disableGetFromLocation = it }
                    out.getBoolean("enableAGPS", FakeLoc.enableAGPS).let { FakeLoc.enableAGPS = it }
                    out.getBoolean("enableNMEA", FakeLoc.enableNMEA).let { FakeLoc.enableNMEA = it }
                    out.getBoolean("hideMock", FakeLoc.hideMock).let { FakeLoc.hideMock = it }
                    out.getBoolean("hookWifi", FakeLoc.hookWifi).let { FakeLoc.hookWifi = it }
                    out.getBoolean("needDowngradeToCdma", FakeLoc.needDowngradeToCdma).let { FakeLoc.needDowngradeToCdma = it }
                    out.getBoolean("loopBroadcastLocation", FakeLoc.loopBroadcastLocation).let { FakeLoc.loopBroadcastLocation = it }
                    out.getInt("minSatellites", FakeLoc.minSatellites).let { FakeLoc.minSatellites = it }
                    out.getFloat("accuracy", FakeLoc.accuracy).let { FakeLoc.accuracy = it }
                    out.getInt("reportIntervalMs", 100).let {
                        FakeLoc.reportIntervalMs = it
                    }
                    out.putBoolean("ok", true)
                    KailLog.d(null, "XPOSED", "PORTAL接收：批量配置更新")
                } catch (e: Throwable) {
                    out.putBoolean("ok", false)
                    KailLog.e(null, "XPOSED", "PORTAL接收：批量配置更新失败 error=${e.message}")
                }
                return true
            }
            else -> return false
        }
    }
}
