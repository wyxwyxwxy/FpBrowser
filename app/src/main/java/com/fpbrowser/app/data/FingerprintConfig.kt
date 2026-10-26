package com.fpbrowser.app.data

/**
 * 浏览器指纹配置。
 * 所有字段都会被序列化为 JSON，在 document-start 阶段注入到页面中（见 fp/FingerprintScript.kt）。
 */
data class FingerprintConfig(
    /** navigator.userAgent */
    val userAgent: String = "",
    /** navigator.appVersion（通常等于 UA 去掉 "Mozilla/" 前缀） */
    val appVersion: String = "",
    /** navigator.platform：Win32 / MacIntel / Linux x86_64 / Linux armv8l / iPhone */
    val platform: String = "Win32",
    /** navigator.vendor */
    val vendor: String = "Google Inc.",
    /** navigator.language */
    val language: String = "en-US",
    /** navigator.languages */
    val languages: List<String> = listOf("en-US", "en"),
    /** IANA 时区，如 America/New_York */
    val timezone: String = "America/New_York",
    /** screen.width / screen.height */
    val screenWidth: Int = 1920,
    val screenHeight: Int = 1080,
    /** screen.availWidth / availHeight（减去任务栏/菜单栏） */
    val availWidth: Int = 1920,
    val availHeight: Int = 1040,
    /** screen.colorDepth / pixelDepth */
    val colorDepth: Int = 24,
    /** window.devicePixelRatio */
    val devicePixelRatio: Double = 1.0,
    /** navigator.hardwareConcurrency */
    val hardwareConcurrency: Int = 8,
    /** navigator.deviceMemory（GiB，仅 Chromium 有意义） */
    val deviceMemory: Int = 8,
    /** navigator.maxTouchPoints，>0 表示触摸设备 */
    val maxTouchPoints: Int = 0,
    /** WebGL 未伪装厂商（WEBGL_debug_renderer_info） */
    val webglVendor: String = "Google Inc. (Intel)",
    /** WebGL 未伪装渲染器 */
    val webglRenderer: String = "ANGLE (Intel, Intel(R) UHD Graphics 630 Direct3D11 vs_5_0 ps_5_0, D3D11)",
    /** Canvas 噪声种子：同一种子产生同一噪声图案（跨会话一致） */
    val canvasNoiseSeed: String = "canvas-00000000-0000-0000-0000-000000000000",
    /** AudioContext 噪声种子 */
    val audioNoiseSeed: String = "audio-00000000-0000-0000-0000-000000000000",
    /** Canvas 噪声模式：noise / off */
    val canvasMode: String = "noise",
    /** 音频噪声模式：noise / off */
    val audioMode: String = "noise",
    /** WebRTC 策略：default / relay（仅走中继）/ disabled（禁用） */
    val webRtcMode: String = "default",
    /** navigator.doNotTrack：null / "1" */
    val doNotTrack: String? = null,
    /** 伪装插件列表（桌面 Chromium 5 个，移动端为空） */
    val plugins: List<FingerprintPlugin> = emptyList(),
    /** 桌面环境是否伪装窗口几何（innerWidth/outerHeight 等） */
    val spoofViewport: Boolean = true,
    /** 引擎类型：chromium / webkit（决定 window.chrome、deviceMemory 等是否存在） */
    val browser: String = "chromium",
    /** screen.orientation.type */
    val orientation: String = "landscape-primary",
    /** navigator.userAgentData（仅 Chromium） */
    val userAgentData: UserAgentDataConfig? = null,
    /** window.innerWidth / innerHeight（0 = 根据屏幕自动推导） */
    val innerWidth: Int = 0,
    val innerHeight: Int = 0,
    /** window.screenX / screenY */
    val screenX: Int = 0,
    val screenY: Int = 0,
    /** navigator.connection */
    val downlink: Int = 10,
    val rtt: Int = 50,
    /** navigator.pdfViewerEnabled */
    val pdfViewerEnabled: Boolean = true,
)

data class FingerprintPlugin(
    val name: String,
    val filename: String,
    val description: String,
)

data class UserAgentDataConfig(
    /** [{brand, version}] */
    val brands: List<UaBrand>,
    val mobile: Boolean = false,
    /** "Windows" / "macOS" / "Android" */
    val platform: String = "Windows",
    val platformVersion: String = "15.0.0",
    val fullVersion: String = "131.0.6778.86",
)

data class UaBrand(
    val brand: String,
    val version: String,
)
