package com.fpbrowser.app.fp

import android.content.Context
import android.util.Log
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import com.fpbrowser.app.data.ProxyConfig
import java.util.concurrent.Executors

/**
 * 每环境独立代理。
 *
 * 实现方式：每个浏览器环境运行在独立进程中，进程内通过 androidx.webkit 的
 * ProxyController.setProxyOverride 设置全局代理——进程隔离天然实现了"每环境不同 IP"。
 * 带账号密码的代理：Chromium 代理规则不支持内联凭据，由 WebView 收到 407 后
 * 通过 WebViewClient.onReceivedHttpAuthRequest 自动应答（见 BrowserActivity）。
 */
object ProxyManager {

    private const val TAG = "ProxyManager"
    private val executor = Executors.newSingleThreadExecutor()

    fun isSupported(): Boolean = WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)

    /** 在当前进程应用代理配置（应在创建任何 WebView 之前调用） */
    fun apply(context: Context, proxy: ProxyConfig) {
        if (!isSupported()) {
            Log.w(TAG, "PROXY_OVERRIDE 不受支持，代理将不生效（请更新 Android System WebView）")
            return
        }
        val controller = ProxyController.getInstance()
        if (!proxy.isUsable) {
            controller.clearProxyOverride(executor, Runnable { })
            return
        }
        val builder = androidx.webkit.ProxyConfig.Builder()
            .addProxyRule("${proxy.type.scheme}://${proxy.host}:${proxy.port}")
        if (proxy.allowDirectFallback) builder.addDirect()
        // 默认不加 direct：代理不可达时直接失败，避免悄悄直连泄露真实 IP
        controller.setProxyOverride(builder.build(), executor, Runnable { })
        Log.i(TAG, "代理已应用：${proxy.type.scheme}://${proxy.host}:${proxy.port}")
    }
}
