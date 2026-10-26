package com.fpbrowser.app.data

import java.util.UUID

/**
 * 导入 / 反序列化后的数据清洗。
 * Gson 绕过 Kotlin 构造器，缺失字段会留下 null / 0，这里统一补齐，防止运行期 NPE。
 */
@Suppress("UNNECESSARY_SAFE_CALL")
fun ProfileEntity.sanitized(): ProfileEntity {
    val fp = fingerprint ?: Presets.windowsChrome()
    val px = proxy ?: ProxyConfig()
    return copy(
        id = if (id.isNullOrBlank()) UUID.randomUUID().toString() else id,
        name = name?.ifBlank { null } ?: "未命名环境",
        remark = remark ?: "",
        tags = tags ?: emptyList(),
        colorHex = if (colorHex.isNullOrBlank()) "#5B8DEF" else colorHex,
        fingerprint = fp.sanitized(),
        proxy = px,
        createdAt = if (createdAt <= 0L) System.currentTimeMillis() else createdAt,
        updatedAt = System.currentTimeMillis(),
        lastUsedAt = lastUsedAt,
    )
}

@Suppress("UNNECESSARY_SAFE_CALL")
fun FingerprintConfig.sanitized(): FingerprintConfig {
    val defaults = Presets.windowsChrome()
    return copy(
        userAgent = userAgent?.ifBlank { null } ?: defaults.userAgent,
        appVersion = appVersion?.ifBlank { null } ?: defaults.appVersion,
        platform = platform?.ifBlank { null } ?: defaults.platform,
        vendor = vendor?.ifBlank { null } ?: defaults.vendor,
        language = language?.ifBlank { null } ?: defaults.language,
        languages = languages ?: defaults.languages,
        timezone = timezone?.ifBlank { null } ?: defaults.timezone,
        screenWidth = if (screenWidth <= 0) defaults.screenWidth else screenWidth,
        screenHeight = if (screenHeight <= 0) defaults.screenHeight else screenHeight,
        availWidth = if (availWidth <= 0) defaults.availWidth else availWidth,
        availHeight = if (availHeight <= 0) defaults.availHeight else availHeight,
        colorDepth = if (colorDepth <= 0) defaults.colorDepth else colorDepth,
        devicePixelRatio = if (devicePixelRatio <= 0.0) defaults.devicePixelRatio else devicePixelRatio,
        hardwareConcurrency = if (hardwareConcurrency <= 0) defaults.hardwareConcurrency else hardwareConcurrency,
        deviceMemory = if (deviceMemory <= 0) defaults.deviceMemory else deviceMemory,
        maxTouchPoints = maxTouchPoints,
        webglVendor = webglVendor?.ifBlank { null } ?: defaults.webglVendor,
        webglRenderer = webglRenderer?.ifBlank { null } ?: defaults.webglRenderer,
        canvasNoiseSeed = canvasNoiseSeed?.ifBlank { null } ?: defaults.canvasNoiseSeed,
        audioNoiseSeed = audioNoiseSeed?.ifBlank { null } ?: defaults.audioNoiseSeed,
        plugins = plugins ?: emptyList(),
        browser = if (browser == "webkit") "webkit" else "chromium",
        orientation = if (orientation.isNullOrBlank()) defaults.orientation else orientation,
        userAgentData = userAgentData,
        downlink = if (downlink <= 0) 10 else downlink,
        rtt = if (rtt <= 0) 50 else rtt,
    )
}
