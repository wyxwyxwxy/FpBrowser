package com.fpbrowser.app.fp

import android.content.Context
import com.fpbrowser.app.R
import com.fpbrowser.app.data.FingerprintConfig
import com.fpbrowser.app.data.Presets
import com.fpbrowser.app.data.UserAgentDataConfig
import com.fpbrowser.app.data.UaBrand
import com.google.gson.Gson
import com.google.gson.GsonBuilder

/**
 * 把 FingerprintConfig 组装成 document-start 注入脚本。
 *
 * 注入顺序（addDocumentStartJavaScript 按注册顺序执行）：
 *   1. prelude  —— 写入 window.__FP_CONFIG__（合并 window.name 中的临时覆盖）
 *   2. engine   —— res/raw/fp_inject.js 指纹引擎
 */
object FingerprintScript {

    private val gson: Gson = GsonBuilder().serializeNulls().create()

    /** 桌面 Chromium 常见的两个 MIME 类型 */
    private val DESKTOP_MIME_TYPES = listOf(
        mapOf("type" to "application/pdf", "suffixes" to "pdf", "description" to "Portable Document Format"),
        mapOf("type" to "application/x-google-chrome-pdf", "suffixes" to "pdf", "description" to "Portable Document Format"),
    )

    /** WebGL 扩展集（桌面 ANGLE 常见列表） */
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

    /** 移动端 WebGL 扩展集（Adreno / OpenGL ES） */
    private val MOBILE_WEBGL_EXTENSIONS = listOf(
        "EXT_blend_minmax", "EXT_color_buffer_float", "EXT_color_buffer_half_float",
        "EXT_depth_clamp", "EXT_disjoint_timer_query_webgl2", "EXT_float_blend",
        "EXT_frag_depth", "EXT_polygon_offset_clamp", "EXT_render_snorm16",
        "EXT_sRGB", "EXT_sRGB_write_control", "EXT_texture_compression_astc",
        "EXT_texture_compression_bptc", "EXT_texture_compression_dxt1",
        "EXT_texture_compression_dxt5", "EXT_texture_compression_etc2",
        "EXT_texture_compression_etc2_alpha", "EXT_texture_compression_s3tc",
        "EXT_texture_filter_anisotropic", "EXT_texture_filter_minmax",
        "EXT_texture_format_BGRA8888", "EXT_texture_mirror_clamp_to_edge",
        "EXT_texture_norm16", "KHR_parallel_shader_compile", "KHR_texture_compression_astc_hdr",
        "KHR_texture_compression_astc_ldr", "OES_element_index_uint",
        "OES_fbo_render_mipmap", "OES_standard_derivatives", "OES_texture_float",
        "OES_texture_float_linear", "OES_texture_half_float",
        "OES_texture_half_float_linear", "OES_vertex_array_object",
        "WEBGL_clip_cull_distance", "WEBGL_compressed_texture_astc",
        "WEBGL_compressed_texture_etc", "WEBGL_compressed_texture_s3tc",
        "WEBGL_compressed_texture_s3tc_srgb", "WEBGL_debug_renderer_info",
        "WEBGL_debug_shaders", "WEBGL_lose_context", "WEBGL_multi_draw",
        "WEBGL_provoking_vertex",
    )

    /** 生成注入引擎读取的配置 JSON */
    fun configJson(cfg: FingerprintConfig): String {
        val mobile = cfg.maxTouchPoints > 0
        val m = LinkedHashMap<String, Any?>()
        m["userAgent"] = cfg.userAgent
        m["appVersion"] = cfg.appVersion
        m["platform"] = cfg.platform
        m["vendor"] = cfg.vendor
        m["language"] = cfg.language
        m["languages"] = cfg.languages
        m["timezone"] = cfg.timezone
        m["screenWidth"] = cfg.screenWidth
        m["screenHeight"] = cfg.screenHeight
        m["availWidth"] = cfg.availWidth
        m["availHeight"] = cfg.availHeight
        m["colorDepth"] = cfg.colorDepth
        m["devicePixelRatio"] = cfg.devicePixelRatio
        m["hardwareConcurrency"] = cfg.hardwareConcurrency
        m["deviceMemory"] = cfg.deviceMemory
        m["maxTouchPoints"] = cfg.maxTouchPoints
        m["webglVendor"] = cfg.webglVendor
        m["webglRenderer"] = cfg.webglRenderer
        m["webglExtensions"] = if (mobile) MOBILE_WEBGL_EXTENSIONS else DESKTOP_WEBGL_EXTENSIONS
        m["canvasNoiseSeed"] = cfg.canvasNoiseSeed
        m["audioNoiseSeed"] = cfg.audioNoiseSeed
        m["canvasMode"] = cfg.canvasMode
        m["audioMode"] = cfg.audioMode
        m["webRtcMode"] = cfg.webRtcMode
        cfg.doNotTrack?.let { m["doNotTrack"] = it }
        m["plugins"] = cfg.plugins.map { p ->
            mapOf("name" to p.name, "filename" to p.filename, "description" to p.description)
        }
        m["mimeTypes"] = if (cfg.plugins.isEmpty()) emptyList<Any>() else DESKTOP_MIME_TYPES
        m["spoofViewport"] = cfg.spoofViewport
        m["browser"] = cfg.browser
        m["orientation"] = cfg.orientation
        m["innerWidth"] = cfg.innerWidth
        m["innerHeight"] = cfg.innerHeight
        m["screenX"] = cfg.screenX
        m["screenY"] = cfg.screenY
        m["downlink"] = cfg.downlink
        m["rtt"] = cfg.rtt
        m["pdfViewerEnabled"] = cfg.pdfViewerEnabled
        cfg.userAgentData?.let { uad ->
            m["userAgentData"] = mapOf(
                "brands" to uad.brands.map { b -> mapOf("brand" to b.brand, "version" to b.version) },
                "mobile" to uad.mobile,
                "platform" to uad.platform,
                "platformVersion" to uad.platformVersion,
                "fullVersion" to uad.fullVersion,
            )
        }
        return gson.toJson(m)
    }

    /** 桌面模式覆盖配置（写入 window.name，刷新后由 prelude 合并） */
    fun desktopOverrideJson(base: FingerprintConfig): String {
        val m = LinkedHashMap<String, Any?>()
        m["userAgent"] = desktopUaOf(base)
        m["appVersion"] = desktopUaOf(base).removePrefix("Mozilla/")
        m["platform"] = "Win32"
        m["vendor"] = "Google Inc."
        m["maxTouchPoints"] = 0
        m["msMaxTouchPoints"] = 0
        m["screenWidth"] = 1920
        m["screenHeight"] = 1080
        m["availWidth"] = 1920
        m["availHeight"] = 1040
        m["colorDepth"] = 24
        m["devicePixelRatio"] = 1.0
        m["orientation"] = "landscape-primary"
        m["innerWidth"] = 1920
        m["innerHeight"] = 950
        m["spoofViewport"] = true
        m["userAgentData"] = mapOf(
            "brands" to listOf(
                mapOf("brand" to "Chromium", "version" to Presets.CHROME_FULL),
                mapOf("brand" to "Google Chrome", "version" to Presets.CHROME_FULL),
                mapOf("brand" to "Not=A?Brand", "version" to "24.0.0.0"),
            ),
            "mobile" to false,
            "platform" to "Windows",
            "platformVersion" to "15.0.0",
            "fullVersion" to Presets.CHROME_FULL,
        )
        return gson.toJson(m)
    }

    /** 桌面模式使用的 UA（保留原配置中的 Chrome 主版本） */
    fun desktopUaOf(base: FingerprintConfig): String {
        val major = Regex("Chrome/(\\d+)").find(base.userAgent)?.groupValues?.get(1) ?: Presets.CHROME_MAJOR
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$major.0.0.0 Safari/537.36"
    }

    /**
     * 生成 document-start 注入脚本列表（按顺序执行）。
     * prelude 同时合并 window.name 中的 "__FP_OVERRIDE__"（桌面模式等临时覆盖）。
     */
    fun buildScripts(context: Context, cfg: FingerprintConfig): List<String> {
        val engine = context.resources.openRawResource(R.raw.fp_inject)
            .bufferedReader().use { it.readText() }
        val configJson = configJson(cfg)
        val prelude = """
            (function () {
              try {
                var override = null;
                if (window.name && window.name.indexOf('__FP_OVERRIDE__') !== -1) {
                  var parsed = JSON.parse(window.name);
                  override = parsed && parsed['__FP_OVERRIDE__'];
                }
                window.__FP_CONFIG__ = Object.assign({}, $configJson, override || {});
              } catch (e) {
                window.__FP_CONFIG__ = $configJson;
              }
            })();
        """.trimIndent()
        return listOf(prelude, engine)
    }

    /** 生成写入 window.name 的覆盖脚本 */
    fun windowNameOverrideScript(overrideJson: String?): String =
        if (overrideJson == null) {
            "window.name = '';"
        } else {
            val wrapper = "{\"__FP_OVERRIDE__\":$overrideJson}"
            "window.name = " + com.google.gson.Gson().toJson(wrapper) + ";"
        }
}
