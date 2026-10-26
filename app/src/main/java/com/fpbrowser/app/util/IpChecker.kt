package com.fpbrowser.app.util

import com.fpbrowser.app.data.ProxyConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.Authenticator
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * 代理连通性测试：通过当前代理访问 IP 查询服务，返回出口 IP 与归属地。
 * 注意：这只是连通性测试，与浏览器内实际走代理相互独立。
 */
object IpChecker {

    data class Result(
        val success: Boolean,
        val ip: String? = null,
        val country: String? = null,
        val city: String? = null,
        val isp: String? = null,
        val latencyMs: Long = 0L,
        val error: String? = null,
    )

    suspend fun test(proxy: ProxyConfig): Result = withContext(Dispatchers.IO) {
        if (!proxy.isUsable) {
            return@withContext Result(false, error = "代理未配置或参数不完整")
        }
        val started = System.currentTimeMillis()
        // 为带账号的代理设置全局 Authenticator（SOCKS5 / HTTP 407 均适用）
        if (proxy.username.isNotBlank()) {
            Authenticator.setDefault(object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    if (requestingHost == proxy.host || requestingProtocol.startsWith("socks", true)) {
                        return PasswordAuthentication(proxy.username, proxy.password.toCharArray())
                    }
                    return super.getPasswordAuthentication()
                }
            })
        }
        try {
            val type = if (proxy.type == ProxyConfig.ProxyType.SOCKS5) Proxy.Type.SOCKS else Proxy.Type.HTTP
            val proxyObj = Proxy(type, InetSocketAddress(proxy.host, proxy.port))
            val conn = (URL("https://api.ipify.org?format=json").openConnection(proxyObj) as HttpsURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val ip = JSONObject(body).optString("ip").ifBlank { null }
                ?: return@withContext Result(false, error = "返回内容异常", latencyMs = System.currentTimeMillis() - started)

            var country: String? = null
            var city: String? = null
            var isp: String? = null
            try {
                val conn2 = (URL("https://ip-api.com/json/$ip?fields=country,city,isp&lang=zh-CN")
                    .openConnection() as HttpsURLConnection).apply {
                    connectTimeout = 6000
                    readTimeout = 6000
                }
                val j2 = JSONObject(conn2.inputStream.bufferedReader().use { it.readText() })
                country = j2.optString("country").ifBlank { null }
                city = j2.optString("city").ifBlank { null }
                isp = j2.optString("isp").ifBlank { null }
            } catch (_: Exception) {
                // 归属地查询失败不影响连通性结论
            }
            Result(
                success = true,
                ip = ip,
                country = country,
                city = city,
                isp = isp,
                latencyMs = System.currentTimeMillis() - started,
            )
        } catch (e: Exception) {
            Result(false, error = e.message ?: "连接失败", latencyMs = System.currentTimeMillis() - started)
        }
    }
}
