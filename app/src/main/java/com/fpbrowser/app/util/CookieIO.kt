package com.fpbrowser.app.util

import android.net.Uri
import android.webkit.CookieManager

/**
 * Netscape 格式 Cookie 导入 / 导出。
 *
 * 平台限制：Android 的 CookieManager 无法枚举全部域名，只能操作"当前站点"的 Cookie，
 * 因此导入 / 导出均针对当前访问的域名（PC 指纹浏览器的全量导入需自行拆分多次导入）。
 */
object CookieIO {

    /** 导出当前站点的 Cookie 为 Netscape 文本 */
    fun exportNetscape(url: String): String {
        val host = Uri.parse(url).host ?: return ""
        val cookieStr = CookieManager.getInstance().getCookie(url) ?: return ""
        val expiry = System.currentTimeMillis() / 1000 + 365L * 24 * 3600
        val domain = if (host.startsWith(".")) host else ".$host"
        val secure = if (url.startsWith("https")) "TRUE" else "FALSE"
        val sb = StringBuilder()
        sb.append("# Netscape HTTP Cookie File\n")
        sb.append("# 由 FpBrowser 导出 · 域名 $host\n")
        cookieStr.split(";").forEach { pair ->
            val idx = pair.indexOf('=')
            if (idx <= 0) return@forEach
            val name = pair.substring(0, idx).trim()
            val value = pair.substring(idx + 1).trim()
            if (name.isEmpty()) return@forEach
            sb.append("$domain\tTRUE\t/\t$secure\t$expiry\t$name\t$value\n")
        }
        return sb.toString()
    }

    /** 从 Netscape 文本导入 Cookie 到当前站点，返回成功条数 */
    fun importNetscape(url: String, content: String): Int {
        val host = Uri.parse(url).host ?: return 0
        val cm = CookieManager.getInstance()
        var count = 0
        content.lineSequence().forEach { line ->
            val t = line.trim()
            if (t.isEmpty() || t.startsWith("#")) return@forEach
            val parts = t.split("\t")
            if (parts.size < 7) return@forEach
            val domain = parts[0].trim()
            val path = parts[2].trim().ifEmpty { "/" }
            val secure = parts[3].trim().equals("TRUE", ignoreCase = true)
            val name = parts[5].trim()
            val value = parts[6].trim()
            if (name.isEmpty()) return@forEach
            // 只导入属于当前域名（或其父域）的 Cookie
            val bare = domain.trimStart('.')
            if (!host.endsWith(bare)) return@forEach
            val scheme = if (secure) "https" else "http"
            val cookieUrl = "$scheme://$host$path"
            val builder = StringBuilder("$name=$value; Domain=$domain; Path=$path; Max-Age=31536000")
            if (secure) builder.append("; Secure")
            cm.setCookie(cookieUrl, builder.toString())
            count++
        }
        cm.flush()
        return count
    }
}
