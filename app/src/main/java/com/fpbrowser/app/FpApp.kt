package com.fpbrowser.app

import android.app.Application
import android.os.Build
import androidx.webkit.ProcessGlobalConfig
import androidx.webkit.WebViewFeature
import android.util.Log

/**
 * 应用入口。每个浏览器槽位进程启动时都会走到这里：
 *  - 读取槽位文件得到当前进程绑定的环境 ID；
 *  - 在创建任何 WebView 之前设置数据目录后缀（fp_profile_<id>），实现环境间存储隔离；
 *  - 隔离不可用时置 degradedIsolation 标记，浏览器界面会提示用户。
 */
class FpApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        val process = currentProcessName()
        if (process != null && process.startsWith("$packageName:web")) {
            val slot = process.removePrefix("$packageName:web").toIntOrNull()
            if (slot != null) onBrowserProcessCreated(slot)
        }
    }

    private fun onBrowserProcessCreated(slot: Int) {
        val profileId = com.fpbrowser.app.fp.SlotManager.readSlotProfile(this, slot)
        if (profileId.isNullOrBlank()) {
            Log.w(TAG, "槽位 $slot 没有绑定环境，使用默认数据目录")
            return
        }
        degradedIsolation = !applyDataDirectorySuffix("fp_profile_$profileId")
        if (degradedIsolation) {
            Log.w(TAG, "数据目录隔离不可用：多个环境将共享 WebView 数据（请更新 Android System WebView）")
        }
    }

    /**
     * 优先使用 androidx.webkit 的公开 API（ProcessGlobalConfig），
     * 失败时回退到系统隐藏 API WebView.setDataDirectorySuffix（API 28+）。
     * 必须在进程内创建任何 WebView 之前调用。
     */
    private fun applyDataDirectorySuffix(suffix: String): Boolean {
        return try {
            val supported = try {
                WebViewFeature.isStartupFeatureSupported(
                    this, WebViewFeature.STARTUP_FEATURE_SET_DATA_DIRECTORY_SUFFIX
                )
            } catch (t: Throwable) { false }
            if (supported) {
                val config = ProcessGlobalConfig().setDataDirectorySuffix(this, suffix)
                ProcessGlobalConfig.apply(config)
            } else {
                @Suppress("UNCHECKED_CAST")
                val method = android.webkit.WebView::class.java
                    .getMethod("setDataDirectorySuffix", String::class.java)
                method.invoke(null, suffix)
            }
            true
        } catch (t: Throwable) {
            Log.e(TAG, "设置 WebView 数据目录后缀失败", t)
            false
        }
    }

    companion object {
        private const val TAG = "FpApp"

        lateinit var instance: FpApp
            private set

        /** 当前进程是否无法使用独立数据目录（隔离降级） */
        @Volatile
        var degradedIsolation: Boolean = false
            private set

        fun currentProcessName(): String? = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                getProcessName()
            } else {
                Class.forName("android.app.ActivityThread")
                    .getMethod("currentProcessName")
                    .invoke(null) as? String
            }
        } catch (t: Throwable) {
            null
        }
    }
}
