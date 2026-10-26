package com.fpbrowser.app.data

/**
 * 每个环境独立的代理配置。
 * 说明：
 * - 代理在当前环境所在进程内通过 androidx.webkit ProxyController 全局生效；
 * - 需要账号密码的代理通过 WebViewClient.onReceivedHttpAuthRequest 自动响应 407 认证；
 * - allowDirectFallback=false 时，代理不可达将直接失败，避免绕过代理直连泄露真实 IP。
 */
data class ProxyConfig(
    val enabled: Boolean = false,
    val type: ProxyType = ProxyType.SOCKS5,
    val host: String = "",
    val port: Int = 1080,
    val username: String = "",
    val password: String = "",
    val allowDirectFallback: Boolean = false,
) {
    enum class ProxyType(val label: String, val scheme: String) {
        HTTP("HTTP", "http"),
        SOCKS5("SOCKS5", "socks5"),
    }

    val isUsable: Boolean get() = enabled && host.isNotBlank() && port in 1..65535

    val summary: String
        get() = if (isUsable) "${type.label} · $host:$port" else "直连（未配置代理）"
}
