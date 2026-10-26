package com.fpbrowser.app.data

import java.util.UUID

/**
 * 预置指纹模板 + 首次启动预置环境。
 *
 * 说明：
 * - 模板即"一台完整的虚拟设备"：UA / 平台 / 时区 / 屏幕 / GPU / 插件 等字段互相自洽，
 *   随机生成器也遵循同样的约束（见 FingerprintGenerator）。
 * - 模板中的 Chrome / Safari 版本号可按需修改，也可在编辑器里逐字段覆盖。
 */
object Presets {

    const val CHROME_MAJOR = "131"
    const val CHROME_FULL = "131.0.6778.86"

    // ---------------------------------------------------------------- 通用片段

    private val DESKTOP_PLUGINS = listOf(
        FingerprintPlugin("PDF Viewer", "internal-pdf-viewer", "Portable Document Format"),
        FingerprintPlugin("Chrome PDF Viewer", "internal-pdf-viewer", "Portable Document Format"),
        FingerprintPlugin("Chromium PDF Viewer", "internal-pdf-viewer", "Portable Document Format"),
        FingerprintPlugin("Microsoft Edge PDF Viewer", "internal-pdf-viewer", "Portable Document Format"),
        FingerprintPlugin("WebKit built-in PDF", "internal-pdf-viewer", "Portable Document Format"),
    )

    /** 桌面 Chromium 常见的 WebGL 扩展集（ANGLE / D3D11） */
    private val DESKTOP_WEBGL_EXTENSIONS = listOf(
        "EXT_blend_minmax", "EXT_clip_control", "EXT_color_buffer_float",
        "EXT_color_buffer_half_float", "EXT_depth_clamp", "EXT_disjoint_timer_query_webgl2",
        "EXT_float_blend", "EXT_frag_depth", "EXT_polygon_offset_clamp",
        "EXT_render_snorm16", "EXT_sRGB", "EXT_sRGB_write_control",
        "EXT_texture_compression_bptc", "EXT_texture_compression_rgtc",
        "EXT_texture_filter_anisotropic", "EXT_texture_mirror_clamp_to_edge",
        "EXT_texture_norm16", "KHR_parallel_shader_compile", "OES_element_index_uint",
        "OES_fbo_render_mipmap", "OES_standard_derivatives", "OES_texture_float",
        "OES_texture_float_linear", "OES_texture_half_float", "OES_texture_half_float_linear",
        "OES_vertex_array_object", "WEBGL_clip_cull_distance",
        "WEBGL_compressed_texture_s3tc", "WEBGL_compressed_texture_s3tc_srgb",
        "WEBGL_debug_renderer_info", "WEBGL_debug_shaders", "WEBGL_lose_context",
        "WEBGL_multi_draw", "WEBGL_polygon_mode",
    )

    private fun chromeUaData(platform: String, platformVersion: String, mobile: Boolean, full: String) =
        UserAgentDataConfig(
            brands = listOf(
                UaBrand("Chromium", full),
                UaBrand("Google Chrome", full),
                UaBrand("Not=A?Brand", "24.0.0.0"),
            ),
            mobile = mobile,
            platform = platform,
            platformVersion = platformVersion,
            fullVersion = full,
        )

    // ---------------------------------------------------------------- 模板

    enum class FingerprintTemplate(val label: String, val description: String) {
        WINDOWS_CHROME("Windows · Chrome", "Windows 11 桌面 + Chrome，最常用"),
        MACOS_CHROME("macOS · Chrome", "macOS 桌面 + Chrome"),
        MACOS_SAFARI("macOS · Safari", "macOS + Safari 17（WebKit 指纹）"),
        LINUX_CHROME("Linux · Chrome", "Ubuntu 桌面 + Chrome"),
        ANDROID_CHROME("Android · Chrome", "Android 14 移动端 + Chrome"),
        IPHONE_SAFARI("iPhone · Safari", "iOS 17 + Safari（尽力而为）"),
        RANDOM("随机生成", "按约束随机组合一台新设备"),
    }

    fun windowsChrome(major: String = CHROME_MAJOR, full: String = CHROME_FULL): FingerprintConfig {
        val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$major.0.0.0 Safari/537.36"
        return FingerprintConfig(
            userAgent = ua,
            appVersion = ua.removePrefix("Mozilla/"),
            platform = "Win32",
            vendor = "Google Inc.",
            language = "en-US",
            languages = listOf("en-US", "en"),
            timezone = "America/New_York",
            screenWidth = 1920, screenHeight = 1080,
            availWidth = 1920, availHeight = 1040,
            colorDepth = 24,
            devicePixelRatio = 1.0,
            hardwareConcurrency = 8,
            deviceMemory = 8,
            maxTouchPoints = 0,
            webglVendor = "Google Inc. (Intel)",
            webglRenderer = "ANGLE (Intel, Intel(R) UHD Graphics 630 Direct3D11 vs_5_0 ps_5_0, D3D11)",
            canvasNoiseSeed = freshSeed("canvas"),
            audioNoiseSeed = freshSeed("audio"),
            canvasMode = "noise", audioMode = "noise", webRtcMode = "default",
            doNotTrack = null,
            plugins = DESKTOP_PLUGINS,
            spoofViewport = true,
            browser = "chromium",
            orientation = "landscape-primary",
            userAgentData = chromeUaData("Windows", "15.0.0", false, full),
            innerWidth = 1920, innerHeight = 950,
            screenX = 0, screenY = 0,
            downlink = 10, rtt = 50,
            pdfViewerEnabled = true,
        )
    }

    fun macChrome(major: String = CHROME_MAJOR, full: String = CHROME_FULL): FingerprintConfig {
        val ua = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$major.0.0.0 Safari/537.36"
        return FingerprintConfig(
            userAgent = ua,
            appVersion = ua.removePrefix("Mozilla/"),
            platform = "MacIntel",
            vendor = "Google Inc.",
            language = "en-US",
            languages = listOf("en-US", "en"),
            timezone = "America/Los_Angeles",
            screenWidth = 1680, screenHeight = 1050,
            availWidth = 1680, availHeight = 1025,
            colorDepth = 30,
            devicePixelRatio = 2.0,
            hardwareConcurrency = 8,
            deviceMemory = 8,
            maxTouchPoints = 0,
            webglVendor = "Google Inc. (Apple)",
            webglRenderer = "ANGLE (Apple, ANGLE Metal Renderer: Apple M1 Pro, Unspecified Version)",
            canvasNoiseSeed = freshSeed("canvas"),
            audioNoiseSeed = freshSeed("audio"),
            plugins = DESKTOP_PLUGINS,
            spoofViewport = true,
            browser = "chromium",
            orientation = "landscape-primary",
            userAgentData = chromeUaData("macOS", "14.6.1", false, full),
            innerWidth = 1680, innerHeight = 965,
            screenX = 0, screenY = 0,
            downlink = 10, rtt = 50,
        )
    }

    fun macSafari(): FingerprintConfig {
        val ua = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.6 Safari/605.1.15"
        return FingerprintConfig(
            userAgent = ua,
            appVersion = ua.removePrefix("Mozilla/"),
            platform = "MacIntel",
            vendor = "Apple Computer, Inc.",
            language = "en-US",
            languages = listOf("en-US", "en"),
            timezone = "Europe/London",
            screenWidth = 1440, screenHeight = 900,
            availWidth = 1440, availHeight = 875,
            colorDepth = 30,
            devicePixelRatio = 2.0,
            hardwareConcurrency = 8,
            deviceMemory = 8,
            maxTouchPoints = 0,
            webglVendor = "Apple Inc.",
            webglRenderer = "Apple GPU",
            canvasNoiseSeed = freshSeed("canvas"),
            audioNoiseSeed = freshSeed("audio"),
            plugins = listOf(
                FingerprintPlugin("WebKit built-in PDF", "internal-pdf-viewer", "Portable Document Format"),
            ),
            spoofViewport = true,
            browser = "webkit",
            orientation = "landscape-primary",
            userAgentData = null,
            innerWidth = 1440, innerHeight = 830,
            screenX = 0, screenY = 0,
            downlink = 10, rtt = 50,
        )
    }

    fun linuxChrome(major: String = CHROME_MAJOR, full: String = CHROME_FULL): FingerprintConfig {
        val ua = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$major.0.0.0 Safari/537.36"
        return FingerprintConfig(
            userAgent = ua,
            appVersion = ua.removePrefix("Mozilla/"),
            platform = "Linux x86_64",
            vendor = "Google Inc.",
            language = "en-US",
            languages = listOf("en-US", "en"),
            timezone = "Europe/Berlin",
            screenWidth = 1920, screenHeight = 1080,
            availWidth = 1920, availHeight = 1050,
            colorDepth = 24,
            devicePixelRatio = 1.0,
            hardwareConcurrency = 12,
            deviceMemory = 8,
            maxTouchPoints = 0,
            webglVendor = "Google Inc. (NVIDIA)",
            webglRenderer = "ANGLE (NVIDIA, NVIDIA GeForce GTX 1650 Direct3D11 vs_5_0 ps_5_0, D3D11)",
            canvasNoiseSeed = freshSeed("canvas"),
            audioNoiseSeed = freshSeed("audio"),
            plugins = DESKTOP_PLUGINS,
            spoofViewport = true,
            browser = "chromium",
            orientation = "landscape-primary",
            userAgentData = chromeUaData("Linux", "6.8.0", false, full),
            innerWidth = 1920, innerHeight = 960,
            screenX = 0, screenY = 0,
            downlink = 10, rtt = 50,
        )
    }

    fun androidChrome(major: String = CHROME_MAJOR, full: String = CHROME_FULL): FingerprintConfig {
        val ua = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$major.0.0.0 Mobile Safari/537.36"
        return FingerprintConfig(
            userAgent = ua,
            appVersion = ua.removePrefix("Mozilla/"),
            platform = "Linux armv8l",
            vendor = "Google Inc.",
            language = "ja-JP",
            languages = listOf("ja-JP", "ja", "en-US", "en"),
            timezone = "Asia/Tokyo",
            screenWidth = 412, screenHeight = 915,
            availWidth = 412, availHeight = 915,
            colorDepth = 24,
            devicePixelRatio = 2.625,
            hardwareConcurrency = 8,
            deviceMemory = 8,
            maxTouchPoints = 5,
            webglVendor = "Qualcomm",
            webglRenderer = "ANGLE (Qualcomm, Adreno (TM) 730, OpenGL ES 3.2)",
            canvasNoiseSeed = freshSeed("canvas"),
            audioNoiseSeed = freshSeed("audio"),
            plugins = emptyList(),
            spoofViewport = false,
            browser = "chromium",
            orientation = "portrait-primary",
            userAgentData = chromeUaData("Android", "14.0.0", true, full),
            innerWidth = 412, innerHeight = 915,
            screenX = 0, screenY = 0,
            downlink = 10, rtt = 50,
        )
    }

    fun iphoneSafari(): FingerprintConfig {
        val ua = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.6 Mobile/15E148 Safari/604.1"
        return FingerprintConfig(
            userAgent = ua,
            appVersion = ua.removePrefix("Mozilla/"),
            platform = "iPhone",
            vendor = "Apple Computer, Inc.",
            language = "en-US",
            languages = listOf("en-US", "en"),
            timezone = "America/New_York",
            screenWidth = 390, screenHeight = 844,
            availWidth = 390, availHeight = 844,
            colorDepth = 32,
            devicePixelRatio = 3.0,
            hardwareConcurrency = 8,
            deviceMemory = 8,
            maxTouchPoints = 5,
            webglVendor = "Apple Inc.",
            webglRenderer = "Apple GPU",
            canvasNoiseSeed = freshSeed("canvas"),
            audioNoiseSeed = freshSeed("audio"),
            plugins = emptyList(),
            spoofViewport = false,
            browser = "webkit",
            orientation = "portrait-primary",
            userAgentData = null,
            innerWidth = 390, innerHeight = 664,
            screenX = 0, screenY = 0,
            downlink = 10, rtt = 50,
        )
    }

    fun freshSeed(prefix: String): String = "$prefix-${UUID.randomUUID()}"

    fun template(t: FingerprintTemplate): FingerprintConfig = when (t) {
        FingerprintTemplate.WINDOWS_CHROME -> windowsChrome()
        FingerprintTemplate.MACOS_CHROME -> macChrome()
        FingerprintTemplate.MACOS_SAFARI -> macSafari()
        FingerprintTemplate.LINUX_CHROME -> linuxChrome()
        FingerprintTemplate.ANDROID_CHROME -> androidChrome()
        FingerprintTemplate.IPHONE_SAFARI -> iphoneSafari()
        FingerprintTemplate.RANDOM -> FingerprintGenerator.random()
    }

    // ---------------------------------------------------------------- 预置环境

    /** 首次安装后直接可用的环境（代理留空，用户在编辑器里填入自己的线路即可"更改 IP"） */
    fun seedProfiles(): List<ProfileEntity> {
        val now = System.currentTimeMillis()
        fun env(
            name: String,
            remark: String,
            tags: List<String>,
            color: String,
            fp: FingerprintConfig,
            proxy: ProxyConfig = ProxyConfig(),
        ) = ProfileEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            remark = remark,
            tags = tags,
            colorHex = color,
            fingerprint = fp,
            proxy = proxy,
            createdAt = now,
            updatedAt = now,
        )

        return listOf(
            env(
                "美区 · Windows Chrome · 主号",
                "Windows 11 + Chrome 131，适合电商店铺 / 广告账户。代理请在编辑页填入。",
                listOf("预置", "Windows", "桌面"),
                "#5B8DEF",
                windowsChrome(),
            ),
            env(
                "日区 · Android Chrome · 移动",
                "Android 14 + Chrome 131 移动端指纹，适合移动端业务。",
                listOf("预置", "Android", "移动"),
                "#3FB68B",
                androidChrome(),
            ),
            env(
                "英区 · macOS Safari · 备用",
                "macOS + Safari 17 WebKit 指纹，适合需要 Safari 特征的场景。",
                listOf("预置", "macOS", "桌面"),
                "#8E6FD8",
                macSafari(),
            ),
            env(
                "德区 · Windows Chrome · 备用",
                "Windows + Chrome，德语区时区与语言。",
                listOf("预置", "Windows", "桌面"),
                "#D8804A",
                windowsChrome().copy(
                    timezone = "Europe/Berlin",
                    language = "de-DE",
                    languages = listOf("de-DE", "de", "en-US", "en"),
                    screenWidth = 1536, screenHeight = 864,
                    availWidth = 1536, availHeight = 824,
                    innerWidth = 1536, innerHeight = 774,
                    hardwareConcurrency = 6,
                ),
            ),
            env(
                "iOS · Safari · 测试",
                "iOS 17 Safari 指纹（尽力而为：底层仍是 Chromium 引擎，少数 WebKit 私有特性无法伪装）。",
                listOf("预置", "iOS", "移动"),
                "#4A4F5C",
                iphoneSafari(),
            ),
            env(
                "Linux · Chrome · 数据采集",
                "Ubuntu + Chrome，适合爬虫 / 自动化测试场景。",
                listOf("预置", "Linux", "桌面"),
                "#C75B5B",
                linuxChrome(),
            ),
        )
    }
}
