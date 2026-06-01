package com.kail.location.service.Root

import android.content.Context
import androidx.preference.PreferenceManager
import com.kail.location.utils.KailLog
import com.kail.location.utils.ShellUtils
import com.kail.location.utils.SimulationDiagnostics
import com.kail.location.viewmodels.SettingsViewModel
import java.io.File
import java.util.zip.ZipFile

/**
 * Helper for the "root" run mode.
 *
 * What this does on every service start:
 *   - Creates the kail staging directories with permissive SELinux labels.
 *   - Copies libkail_native_hook.so out of the APK into /data/local/kail-lib/
 *     so the in-app NativeSensorHook can dlopen it for step-counter mocking.
 *   - Grants the host package the AppOps `android:mock_location` permission
 *     via `appops set` so [com.kail.location.service.Developer.MockLocationProvider]
 *     can register a test provider without the user manually flipping
 *     "select mock location app" in Developer Settings.
 *
 * What this deliberately does NOT do:
 *   - Run kail_inject against system_server. Ptrace-injecting system_server
 *     is extremely fragile on production ROMs: a single mismatch between the
 *     loader's expected ART layout and the running framework version freezes
 *     the entire phone (system_server hangs in ptrace_stop, every UI thread
 *     blocks on it). The kail FakeLocation injection framework lives under
 *     [FAKELOC_DIR] and the injector binary lives at [STAGING_DIR]/kail_inject
 *     for operators who want to run it manually after vetting it on their
 *     specific ROM, but the controller app does not auto-run them.
 *
 * The opt-in helpers [stageInjectionPayloads] and [bootstrapInjection] are
 * provided so a developer can wire them to a manual button in the UI later.
 */
object RootDeployer {
    private const val TAG = "RootDeployer"

    const val STAGING_DIR = "/data/local/kail-lib"
    const val FAKELOC_DIR = "/data/kail-loc"
    const val NATIVE_HOOK_SO = "libkail_native_hook.so"
    const val INJECTOR_BIN = "kail_inject"

    /** FakeLocation loader/hook libraries packaged in the APK under lib/<abi>/. */
    private val FAKELOC_LIBS = listOf(
        "libfakeloc_init.so",
        "libfakeloc_initzygote.so",
        "libfakeloc_apphook.so",
        "liblhooker.so",
        "libStepSensor.so",
        "libantidetect.so"
    )

    /**
     * Idempotent setup that the service runs at every start.
     *
     * Stages the FakeLocation toolchain on disk, grants the AppOps mock
     * location permission, and runs `kail_inject` against system_server to
     * register the service_mock_* binders (matching the original FakeLocation
     * behaviour). The injector now has a 5-second watchdog (see
     * cpp/root/inject{,64}.cpp) so a hung remote dlopen detaches the tracee
     * cleanly instead of leaving system_server in ptrace_stop.
     */
    fun ensureBaseline(context: Context): Boolean {
        if (!ShellUtils.hasRoot()) {
            KailLog.w(null, TAG, "ensureBaseline: no root; skipping")
            return false
        }
        prepareDirs()
        syncInjectLogMarkers(context)
        deployNativeHookLib(context)
        deployInjectorBin(context)
        deployFakelocLibs(context)
        deployDexPayload(context)
        grantMockLocationAppOps(context)
        // Best-effort inject. If the watchdog trips we'll just log the
        // injector's "Inject fail" message; the service still functions
        // through the test-provider path.
        runCatching { bootstrapInjection() }
            .onFailure { KailLog.w(null, TAG, "bootstrapInjection: ${it.message}") }
        return true
    }

    /**
     * 带诊断的 ensureBaseline：每一步都记入 [diag]，便于用户排障时一眼定位失败点。
     * 返回是否成功跑完注入引导（不代表 binder 一定就绪，后续由调用方核对）。
     */
    fun ensureBaselineDiagnosed(context: Context, diag: SimulationDiagnostics): Boolean {
        val rooted = ShellUtils.hasRoot()
        diag.step(
            "ROOT 权限",
            rooted,
            if (rooted) "su 可用"
            else "su 调用失败——应用未获 ROOT 授权（请在 KernelSU/Magisk 管理器里授权），将回退到测试Provider"
        )
        if (!rooted) {
            KailLog.w(null, TAG, "ensureBaseline: no root; skipping")
            return false
        }

        runCatching { prepareDirs() }
            .onSuccess { diag.step("准备目录", true, "$STAGING_DIR / $FAKELOC_DIR (chcon system_file)") }
            .onFailure { diag.error("准备目录", it) }

        syncInjectLogMarkers(context)

        val nativeOk = runCatching { deployNativeHookLib(context) }.getOrDefault(false)
        diag.step("部署 native hook 库", nativeOk, NATIVE_HOOK_SO)

        val injectorOk = runCatching { deployInjectorBin(context) }.getOrDefault(false)
        diag.step("部署注入器", injectorOk, INJECTOR_BIN)

        val loaderOk = runCatching { deployFakelocLibs(context) }.getOrDefault(false)
        diag.step("部署 FakeLocation 注入库", loaderOk, "libfakeloc_init.so 等 ${FAKELOC_LIBS.size} 个")

        val dexOk = runCatching { deployDexPayload(context) }.getOrDefault(false)
        diag.step("部署 inject.dex", dexOk, "libfakeloc.so (slim dex)")

        val appOpsOk = runCatching { grantMockLocationAppOps(context) }.getOrDefault(false)
        diag.step("授予 mock_location AppOps", appOpsOk)

        val (injected, injectDetail) = runCatching { bootstrapInjectionVerbose() }.getOrElse {
            diag.error("ptrace 注入 system_server", it)
            false to "注入抛异常：${it.message}"
        }
        diag.step("ptrace 注入 system_server", injected, injectDetail)
        return injected
    }

    /** 注入态日志标记目录（与 InjectLog 中的常量保持一致）。 */
    private const val INJECT_LOG_DIR = "/sdcard/Documents/KailLocation/logs"

    /**
     * 把宿主的日志开关同步成注入进程可读的标记文件。
     *
     * 注入态 Hook 运行在目标 App 进程里，读不到本应用的 SharedPreferences，
     * 因此用公共目录下的标记文件传递开关（见 [com.kail.location.inject.utils.InjectLog]）：
     *   .kail_debug    -> 启用日志
     *   .kail_log_file -> 额外落盘
     *   .kail_verbose  -> 启用高频(V)详细日志
     */
    fun syncInjectLogMarkers(context: Context) {
        runCatching {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val logEnabled = prefs.getBoolean(SettingsViewModel.KEY_LOG_ENABLED, false)
            val debugEnabled = prefs.getBoolean(SettingsViewModel.KEY_DEBUG_LOG_ENABLED, false)
            // 详细调试隐含开启基础日志。
            val enabled = logEnabled || debugEnabled
            ShellUtils.executeCommand("mkdir -p $INJECT_LOG_DIR")
            toggleMarker("$INJECT_LOG_DIR/.kail_debug", enabled)
            toggleMarker("$INJECT_LOG_DIR/.kail_log_file", logEnabled)
            toggleMarker("$INJECT_LOG_DIR/.kail_verbose", debugEnabled)
            KailLog.i(context, TAG, "syncInjectLogMarkers: enabled=$enabled file=$logEnabled verbose=$debugEnabled")
        }.onFailure { KailLog.w(context, TAG, "syncInjectLogMarkers: ${it.message}") }
    }

    private fun toggleMarker(path: String, on: Boolean) {
        if (on) {
            ShellUtils.executeCommand("touch $path && chmod 666 $path")
        } else {
            ShellUtils.executeCommand("rm -f $path")
        }
    }

    /**
     * Run kail_inject against system_server to register the FakeLocation
     * service_mock_* binders.
     *
     * The injector has a 5-second watchdog (see cpp/root/inject{,64}.cpp) that
     * trips PTRACE_DETACH if a remote function hangs — typically when the
     * remote dlopen blocks on a linker mutex held by a sibling thread. With
     * the watchdog, a hung inject leaves system_server runnable instead of
     * permafrozen, at the cost of an "Inject fail" return.
     */
    fun bootstrapInjection(): Boolean {
        return bootstrapInjectionVerbose().first
    }

    /**
     * Like [bootstrapInjection] but also returns the injector's raw stdout/stderr
     * so the diagnostics layer can surface the exact failure (watchdog trip,
     * remote dlopen hang, "Inject fail", missing files, …) instead of a generic
     * guess. Returns (success, humanReadableDetail).
     */
    fun bootstrapInjectionVerbose(): Pair<Boolean, String> {
        if (!ShellUtils.hasRoot()) return false to "su 不可用（未授权 ROOT）"
        val injector = File(STAGING_DIR, INJECTOR_BIN)
        val initLoader = File(FAKELOC_DIR, "libfakeloc_init.so")
        if (!injector.exists()) {
            val msg = "注入器缺失：${injector.absolutePath}（部署失败？）"
            KailLog.e(null, TAG, "bootstrapInjection: $msg")
            return false to msg
        }
        if (!initLoader.exists()) {
            val msg = "加载器缺失：${initLoader.absolutePath}（部署失败？）"
            KailLog.e(null, TAG, "bootstrapInjection: $msg")
            return false to msg
        }
        val cmd = "${injector.absolutePath} -P system_server -l ${initLoader.absolutePath} -n com.kail.location"
        val out = ShellUtils.executeCommand(cmd).trim()
        KailLog.i(null, TAG, "kail_inject -> $out")
        val ok = out.contains("Inject ok")
        val detail = when {
            ok -> "kail_inject 返回 Inject ok"
            out.contains("watchdog", ignoreCase = true) ->
                "注入超时：远程函数未返回，watchdog 触发（system_server 繁忙/刚开机未就绪）。原始输出：$out"
            out.contains("fail", ignoreCase = true) ->
                "注入器返回失败。原始输出：$out"
            out.isBlank() ->
                "注入器无输出（su 被拒/进程被杀？）"
            else -> "注入未确认成功。原始输出：$out"
        }
        return ok to detail
    }

    /**
     * Inject the FakeLocation app-hook loader into an arbitrary running
     * process by name. Used to bring the cell-tower pull APIs
     * (TelephonyManager.getAllCellInfo / getCellLocation, served by
     * com.android.phone's PhoneInterfaceManager) under
     * [com.kail.location.inject.fakelocation.hook.phone.PhoneInterfaceManagerHook].
     *
     * The system_server inject (registering the service_mock_* binders) is a
     * prerequisite — the app-hook InjectDex.hookApplication path reads mock
     * state through those binders. Safe to call repeatedly; injecting an
     * already-hooked process just re-runs hookApplication which no-ops the
     * already-installed hooks.
     */
    fun injectAppProcess(processName: String): Boolean {
        if (!ShellUtils.hasRoot()) return false
        val injector = File(STAGING_DIR, INJECTOR_BIN)
        // libfakeloc_apphook.so -> InjectDex.hookApplication (installs the
        // per-process hooks, including PhoneInterfaceManagerHook for phone).
        val appLoader = File(FAKELOC_DIR, "libfakeloc_apphook.so")
        if (!injector.exists() || !appLoader.exists()) {
            KailLog.e(null, TAG, "injectAppProcess: injector or apphook loader missing")
            return false
        }
        val cmd = "${injector.absolutePath} -P $processName -l ${appLoader.absolutePath} -n com.kail.location"
        val out = ShellUtils.executeCommand(cmd)
        KailLog.i(null, TAG, "kail_inject ($processName) -> $out")
        return out.contains("Inject ok")
    }

    /**
     * Side-effect free check for whether the ptrace-injection prerequisites
     * have already been staged on disk.
     */
    fun isInjectionStaged(): Boolean {
        if (!File(FAKELOC_DIR, "libfakeloc.so").exists()) return false
        if (!File(FAKELOC_DIR, "libfakeloc_init.so").exists()) return false
        if (!File(STAGING_DIR, INJECTOR_BIN).exists()) return false
        return true
    }

    // ------------------------------------------------------------------
    // Building blocks
    // ------------------------------------------------------------------

    fun deployNativeHookLib(context: Context): Boolean {
        val src = File(context.applicationInfo.nativeLibraryDir, NATIVE_HOOK_SO)
        val dst = File(STAGING_DIR, NATIVE_HOOK_SO)
        val ok = copyAndChmod(context, src, "lib/${preferredAbi()}/$NATIVE_HOOK_SO", dst)
        // Also stage it under FAKELOC_DIR with the system_file SELinux label so
        // it can be System.load()ed from inside system_server by the inject
        // (NativeStepHook for root-mode step/gait simulation).
        runCatching {
            val fakelocDst = File(FAKELOC_DIR, NATIVE_HOOK_SO)
            ShellUtils.executeCommand("cp -f ${dst.absolutePath} ${fakelocDst.absolutePath}")
            ShellUtils.executeCommand("chmod 644 ${fakelocDst.absolutePath}")
            ShellUtils.executeCommand("chcon u:object_r:system_file:s0 ${fakelocDst.absolutePath}")
        }.onFailure { KailLog.w(null, TAG, "stage native hook into FAKELOC_DIR: ${it.message}") }
        return ok
    }

    fun deployInjectorBin(context: Context): Boolean {
        val abi = preferredAbi()
        val src = File(context.applicationInfo.nativeLibraryDir, "libkail_inject.so")
        val dst = File(STAGING_DIR, INJECTOR_BIN)
        val ok = copyAndChmod(context, src, "lib/$abi/libkail_inject.so", dst)
        if (ok) ShellUtils.executeCommand("chmod 755 ${dst.absolutePath}")
        return ok
    }

    fun deployFakelocLibs(context: Context): Boolean {
        var initLoader = false
        val abi = preferredAbi()
        val isArm64 = abi == "arm64-v8a"
        for (name in FAKELOC_LIBS) {
            val src = File(context.applicationInfo.nativeLibraryDir, name)
            val dst = File(FAKELOC_DIR, name)
            val ok = copyAndChmod(context, src, "lib/$abi/$name", dst)
            if (ok && name == "libfakeloc_init.so") initLoader = true

            // InjectDex.java probes both `<name>.so` (arm) and `<name>64.so`
            // (arm64) without checking which side actually exists. Mirror the
            // file under the matching suffix for the active ABI so the lookup
            // succeeds regardless of which path it picks first.
            if (ok && isArm64 && !name.contains("64.so")) {
                val sixtyFour = name.replace(".so", "64.so")
                val mirror = File(FAKELOC_DIR, sixtyFour)
                ShellUtils.executeCommand("cp -f ${dst.absolutePath} ${mirror.absolutePath}")
                ShellUtils.executeCommand("chmod 777 ${mirror.absolutePath}")
                ShellUtils.executeCommand("chcon u:object_r:system_file:s0 ${mirror.absolutePath}")
            }
        }
        return initLoader
    }

    fun deployDexPayload(context: Context): Boolean {
        val dst = File(FAKELOC_DIR, "libfakeloc.so")
        // Prefer the slim inject.dex we ship in assets — it contains only the
        // FakeLocation bootstrap classes (com.kail.location.inject.* +
        // com.kail.location.lib.lhooker.*), about 1-2 MB compared to the full
        // 33 MB APK. system_server's DexClassLoader can verify that small dex
        // in well under our 60 s ptrace watchdog window. The full APK path is
        // only used as a fallback if assets/inject.dex is missing (older builds
        // without the slim-dex Gradle task).
        val slim = runCatching {
            val out = File(context.cacheDir, "inject.dex")
            context.assets.open("inject.dex").use { input ->
                out.outputStream().use { input.copyTo(it) }
            }
            out
        }.getOrNull()

        return runCatching {
            if (slim != null && slim.exists() && slim.length() > 0) {
                ShellUtils.executeCommand("cp -f ${slim.absolutePath} ${dst.absolutePath}")
                KailLog.i(null, TAG, "deployDexPayload: using slim inject.dex (${slim.length()} bytes)")
            } else {
                val apkPath = context.applicationInfo.sourceDir ?: return@runCatching false
                ShellUtils.executeCommand("cp -f $apkPath ${dst.absolutePath}")
                KailLog.w(null, TAG, "deployDexPayload: assets/inject.dex missing; falling back to full APK ($apkPath)")
            }
            ShellUtils.executeCommand("chmod 644 ${dst.absolutePath}")
            ShellUtils.executeCommand("chcon u:object_r:system_file:s0 ${dst.absolutePath}")
            dst.exists() && dst.length() > 0
        }.getOrElse {
            KailLog.e(null, TAG, "deployDexPayload: ${it.message}")
            false
        }
    }

    /**
     * Silently grant the host package the `android:mock_location` AppOps so
     * `LocationManager.addTestProvider` works without the user toggling
     * "select mock location app" in Developer Settings.
     */
    fun grantMockLocationAppOps(context: Context): Boolean {
        val pkg = context.packageName
        return runCatching {
            ShellUtils.executeCommand("appops set $pkg android:mock_location allow")
            // Some ROMs accept a numeric op id alias; harmless when it does not exist.
            ShellUtils.executeCommand("appops set $pkg 58 allow")
            true
        }.getOrElse {
            KailLog.e(null, TAG, "grantMockLocationAppOps: ${it.message}")
            false
        }
    }

    /** Convenience: revoke the AppOps grant when leaving root mode. */
    fun revokeMockLocationAppOps(context: Context): Boolean {
        val pkg = context.packageName
        return runCatching {
            ShellUtils.executeCommand("appops set $pkg android:mock_location ignore")
            true
        }.getOrElse { false }
    }

    private fun prepareDirs() {
        runCatching {
            ShellUtils.executeCommand("setenforce 0")
            for (d in listOf(STAGING_DIR, FAKELOC_DIR)) {
                ShellUtils.executeCommand("mkdir -p $d")
                ShellUtils.executeCommand("chmod 777 $d")
                ShellUtils.executeCommand("chcon u:object_r:system_file:s0 $d")
            }
            // libfakeloc_init.cpp uses /data/kail-loc/system_dex as the
            // DexClassLoader optimization output dir. If it doesn't exist
            // before we inject, ART falls back to compiling the 33MB APK in
            // an in-process buffer which can take >10s on cold cache and
            // sometimes never finishes (system_server gets killed by its
            // own watchdog). Pre-create it with permissive SELinux labels.
            for (d in listOf("$FAKELOC_DIR/system_dex", "$FAKELOC_DIR/oat")) {
                ShellUtils.executeCommand("mkdir -p $d")
                ShellUtils.executeCommand("chmod 777 $d")
                ShellUtils.executeCommand("chcon u:object_r:system_file:s0 $d")
            }
        }.onFailure { KailLog.e(null, TAG, "prepareDirs: ${it.message}") }
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private fun preferredAbi(): String =
        android.os.Build.SUPPORTED_ABIS.firstOrNull { it == "arm64-v8a" }
            ?: android.os.Build.SUPPORTED_ABIS.firstOrNull()
            ?: "arm64-v8a"

    private fun copyAndChmod(context: Context, src: File, zipEntry: String, dst: File): Boolean {
        return runCatching {
            if (src.exists() && src.length() > 0) {
                ShellUtils.executeCommand("cp -f ${src.absolutePath} ${dst.absolutePath}")
            } else {
                extractFromApk(context, zipEntry, dst)
            }
            ShellUtils.executeCommand("chmod 777 ${dst.absolutePath}")
            ShellUtils.executeCommand("chcon u:object_r:system_file:s0 ${dst.absolutePath}")
            dst.exists() && dst.length() > 0
        }.getOrElse {
            KailLog.e(null, TAG, "copyAndChmod ${dst.name}: ${it.message}")
            false
        }
    }

    private fun extractFromApk(context: Context, zipEntry: String, dst: File) {
        val apkPath = context.applicationInfo.sourceDir ?: return
        ZipFile(apkPath).use { zip ->
            val entry = zip.getEntry(zipEntry) ?: return
            zip.getInputStream(entry).use { input ->
                dst.outputStream().use { out -> input.copyTo(out) }
            }
        }
    }
}
