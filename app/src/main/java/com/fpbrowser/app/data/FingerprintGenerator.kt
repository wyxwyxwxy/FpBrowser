package com.fpbrowser.app.data

import kotlin.random.Random

/**
 * 约束一致的随机指纹生成器：
 * 平台 / UA / 屏幕 / GPU / 时区 / 语言 / 触摸 等字段成套变化，避免出现自相矛盾的组合
 * （例如 Windows 桌面 UA + 触摸点 5 + 移动 WebGL 这类一眼假的情况）。
 */
object FingerprintGenerator {

    private val rng = Random(System.nanoTime())

    private val chromeMajors = listOf("130", "131", "132")
    private val chromeFulls = mapOf(
        "130" to "130.0.6723.91",
        "131" to "131.0.6778.86",
        "132" to "132.0.6834.84",
    )

    private val windowsScreens = listOf(
        Triple(1920, 1080, 1040), Triple(1366, 768, 728),
        Triple(2560, 1440, 1400), Triple(1536, 864, 824),
    )
    private val macScreens = listOf(
        Triple(1440, 900, 875), Triple(1680, 1050, 1025), Triple(2560, 1440, 1405),
    )
    private val linuxScreens = listOf(
        Triple(1920, 1080, 1050), Triple(2560, 1440, 1400),
    )
    private val intelGpus = listOf(
        "ANGLE (Intel, Intel(R) UHD Graphics 630 Direct3D11 vs_5_0 ps_5_0, D3D11)",
        "ANGLE (Intel, Intel(R) Iris(R) Xe Graphics Direct3D11 vs_5_0 ps_5_0, D3D11)",
        "ANGLE (Intel, Intel(R) HD Graphics 620 Direct3D11 vs_5_0 ps_5_0, D3D11)",
    )
    private val nvidiaGpus = listOf(
        "ANGLE (NVIDIA, NVIDIA GeForce GTX 1650 Direct3D11 vs_5_0 ps_5_0, D3D11)",
        "ANGLE (NVIDIA, NVIDIA GeForce RTX 3060 Direct3D11 vs_5_0 ps_5_0, D3D11)",
    )
    private val amdGpus = listOf(
        "ANGLE (AMD, Radeon RX 580 Series Direct3D11 vs_5_0 ps_5_0, D3D11)",
        "ANGLE (AMD, AMD Radeon Pro 5300M OpenGL Engine, Unspecified Version)",
    )
    private val desktopTimezones = listOf(
        "America/New_York", "America/Chicago", "America/Los_Angeles", "America/Sao_Paulo",
        "Europe/London", "Europe/Berlin", "Europe/Paris", "Europe/Madrid",
        "Asia/Tokyo", "Asia/Singapore", "Australia/Sydney",
    )
    private val androidTimezones = listOf(
        "Asia/Tokyo", "Asia/Shanghai", "Asia/Singapore", "America/Los_Angeles", "Europe/London",
    )
    private val androidModels = listOf(
        "Pixel 8 Pro", "Pixel 7", "Galaxy S24", "Galaxy S23", "Xiaomi 14", "OnePlus 12",
    )
    private val iosModels = listOf("iPhone 15 Pro", "iPhone 14", "iPhone 13")

    fun random(): FingerprintConfig = when (rng.nextInt(100)) {
        in 0..39 -> randomWindows()
        in 40..59 -> randomMacChrome()
        in 60..69 -> randomMacSafari()
        in 70..79 -> randomLinux()
        in 80..94 -> randomAndroid()
        else -> randomIphone()
    }

    private fun randomChromeVersion(): Pair<String, String> {
        val major = chromeMajors.random(rng)
        return major to (chromeFulls[major] ?: chromeFulls["131"]!!)
    }

    private fun randomWindows(): FingerprintConfig {
        val (major, full) = randomChromeVersion()
        val (w, h, ah) = windowsScreens.random(rng)
        val gpu = (intelGpus + nvidiaGpus + amdGpus).random(rng)
        val tz = desktopTimezones.random(rng)
        val base = Presets.windowsChrome(major, full)
        return base.copy(
            timezone = tz,
            language = "en-US",
            languages = listOf("en-US", "en"),
            screenWidth = w, screenHeight = h,
            availWidth = w, availHeight = ah,
            innerWidth = w, innerHeight = ah - 90,
            hardwareConcurrency = listOf(4, 8, 12, 16).random(rng),
            deviceMemory = listOf(4, 8).random(rng),
            webglVendor = "Google Inc. (${gpu.substringAfter('(').substringBefore(',')})",
            webglRenderer = gpu,
            canvasNoiseSeed = Presets.freshSeed("canvas"),
            audioNoiseSeed = Presets.freshSeed("audio"),
        )
    }

    private fun randomMacChrome(): FingerprintConfig {
        val (major, full) = randomChromeVersion()
        val (w, h, ah) = macScreens.random(rng)
        val tz = desktopTimezones.random(rng)
        val base = Presets.macChrome(major, full)
        return base.copy(
            timezone = tz,
            screenWidth = w, screenHeight = h,
            availWidth = w, availHeight = ah,
            innerWidth = w, innerHeight = ah - 60,
            hardwareConcurrency = listOf(8, 10).random(rng),
            canvasNoiseSeed = Presets.freshSeed("canvas"),
            audioNoiseSeed = Presets.freshSeed("audio"),
        )
    }

    private fun randomMacSafari(): FingerprintConfig {
        val (w, h, ah) = macScreens.random(rng)
        val base = Presets.macSafari()
        return base.copy(
            timezone = desktopTimezones.random(rng),
            screenWidth = w, screenHeight = h,
            availWidth = w, availHeight = ah,
            innerWidth = w, innerHeight = ah - 70,
            canvasNoiseSeed = Presets.freshSeed("canvas"),
            audioNoiseSeed = Presets.freshSeed("audio"),
        )
    }

    private fun randomLinux(): FingerprintConfig {
        val (major, full) = randomChromeVersion()
        val (w, h, ah) = linuxScreens.random(rng)
        val gpu = (nvidiaGpus + amdGpus + intelGpus).random(rng)
        val base = Presets.linuxChrome(major, full)
        return base.copy(
            timezone = desktopTimezones.random(rng),
            screenWidth = w, screenHeight = h,
            availWidth = w, availHeight = ah,
            innerWidth = w, innerHeight = ah - 90,
            webglVendor = "Google Inc. (${gpu.substringAfter('(').substringBefore(',')})",
            webglRenderer = gpu,
            canvasNoiseSeed = Presets.freshSeed("canvas"),
            audioNoiseSeed = Presets.freshSeed("audio"),
        )
    }

    private fun randomAndroid(): FingerprintConfig {
        val (major, full) = randomChromeVersion()
        val model = androidModels.random(rng)
        val tz = androidTimezones.random(rng)
        val ua = "Mozilla/5.0 (Linux; Android 14; $model) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$major.0.0.0 Mobile Safari/537.36"
        val base = Presets.androidChrome(major, full)
        return base.copy(
            userAgent = ua,
            appVersion = ua.removePrefix("Mozilla/"),
            timezone = tz,
            language = "en-US",
            languages = listOf("en-US", "en"),
            hardwareConcurrency = 8,
            canvasNoiseSeed = Presets.freshSeed("canvas"),
            audioNoiseSeed = Presets.freshSeed("audio"),
        )
    }

    private fun randomIphone(): FingerprintConfig {
        val model = iosModels.random(rng)
        val ua = "Mozilla/5.0 ($model; CPU iPhone OS 17_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.6 Mobile/15E148 Safari/604.1"
        val base = Presets.iphoneSafari()
        return base.copy(
            userAgent = ua,
            appVersion = ua.removePrefix("Mozilla/"),
            timezone = desktopTimezones.random(rng),
            canvasNoiseSeed = Presets.freshSeed("canvas"),
            audioNoiseSeed = Presets.freshSeed("audio"),
        )
    }
}
