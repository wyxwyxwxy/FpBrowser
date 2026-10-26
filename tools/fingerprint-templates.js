/*
 * FpBrowser 开发工具用指纹模板（与 app/src/main/java/com/fpbrowser/app/data/Presets.kt 保持一致）。
 * 用途：
 *   - tools/test-inject.mjs        ：Node 环境下验证注入引擎
 *   - tools/fp-preview.standalone.html：桌面浏览器中预览注入效果
 * 注意：修改 Kotlin 侧模板后请同步更新本文件。
 */
(function (root, factory) {
  var templates = factory();
  if (typeof module !== 'undefined' && module.exports) module.exports = templates;
  if (root) root.FP_TEMPLATES = templates;
})(typeof self !== 'undefined' ? self : globalThis, function () {
  'use strict';

  var CHROME_FULL = '131.0.6778.86';

  var DESKTOP_PLUGINS = [
    { name: 'PDF Viewer', filename: 'internal-pdf-viewer', description: 'Portable Document Format' },
    { name: 'Chrome PDF Viewer', filename: 'internal-pdf-viewer', description: 'Portable Document Format' },
    { name: 'Chromium PDF Viewer', filename: 'internal-pdf-viewer', description: 'Portable Document Format' },
    { name: 'Microsoft Edge PDF Viewer', filename: 'internal-pdf-viewer', description: 'Portable Document Format' },
    { name: 'WebKit built-in PDF', filename: 'internal-pdf-viewer', description: 'Portable Document Format' }
  ];

  function chromeUaData(platform, platformVersion, mobile, full) {
    return {
      brands: [
        { brand: 'Chromium', version: full },
        { brand: 'Google Chrome', version: full },
        { brand: 'Not=A?Brand', version: '24.0.0.0' }
      ],
      mobile: mobile,
      platform: platform,
      platformVersion: platformVersion,
      fullVersion: full
    };
  }

  function base(overrides) {
    var d = {
      canvasMode: 'noise',
      audioMode: 'noise',
      webRtcMode: 'default',
      doNotTrack: null,
      spoofViewport: true,
      browser: 'chromium',
      screenX: 0,
      screenY: 0,
      downlink: 10,
      rtt: 50,
      pdfViewerEnabled: true,
      innerWidth: 0,
      innerHeight: 0
    };
    for (var k in overrides) d[k] = overrides[k];
    return d;
  }

  return {
    windowsChrome: function () {
      var ua = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36';
      return base({
        userAgent: ua,
        appVersion: ua.replace('Mozilla/', ''),
        platform: 'Win32',
        vendor: 'Google Inc.',
        language: 'en-US',
        languages: ['en-US', 'en'],
        timezone: 'America/New_York',
        screenWidth: 1920, screenHeight: 1080,
        availWidth: 1920, availHeight: 1040,
        colorDepth: 24, devicePixelRatio: 1.0,
        hardwareConcurrency: 8, deviceMemory: 8, maxTouchPoints: 0,
        webglVendor: 'Google Inc. (Intel)',
        webglRenderer: 'ANGLE (Intel, Intel(R) UHD Graphics 630 Direct3D11 vs_5_0 ps_5_0, D3D11)',
        canvasNoiseSeed: 'canvas-test-windows',
        audioNoiseSeed: 'audio-test-windows',
        plugins: DESKTOP_PLUGINS,
        orientation: 'landscape-primary',
        userAgentData: chromeUaData('Windows', '15.0.0', false, CHROME_FULL),
        innerWidth: 1920, innerHeight: 950
      });
    },

    macChrome: function () {
      var ua = 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36';
      return base({
        userAgent: ua,
        appVersion: ua.replace('Mozilla/', ''),
        platform: 'MacIntel',
        vendor: 'Google Inc.',
        language: 'en-US',
        languages: ['en-US', 'en'],
        timezone: 'America/Los_Angeles',
        screenWidth: 1680, screenHeight: 1050,
        availWidth: 1680, availHeight: 1025,
        colorDepth: 30, devicePixelRatio: 2.0,
        hardwareConcurrency: 8, deviceMemory: 8, maxTouchPoints: 0,
        webglVendor: 'Google Inc. (Apple)',
        webglRenderer: 'ANGLE (Apple, ANGLE Metal Renderer: Apple M1 Pro, Unspecified Version)',
        canvasNoiseSeed: 'canvas-test-macchrome',
        audioNoiseSeed: 'audio-test-macchrome',
        plugins: DESKTOP_PLUGINS,
        orientation: 'landscape-primary',
        userAgentData: chromeUaData('macOS', '14.6.1', false, CHROME_FULL),
        innerWidth: 1680, innerHeight: 965
      });
    },

    macSafari: function () {
      var ua = 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.6 Safari/605.1.15';
      return base({
        userAgent: ua,
        appVersion: ua.replace('Mozilla/', ''),
        platform: 'MacIntel',
        vendor: 'Apple Computer, Inc.',
        language: 'en-US',
        languages: ['en-US', 'en'],
        timezone: 'Europe/London',
        screenWidth: 1440, screenHeight: 900,
        availWidth: 1440, availHeight: 875,
        colorDepth: 30, devicePixelRatio: 2.0,
        hardwareConcurrency: 8, deviceMemory: 8, maxTouchPoints: 0,
        webglVendor: 'Apple Inc.',
        webglRenderer: 'Apple GPU',
        canvasNoiseSeed: 'canvas-test-macsafari',
        audioNoiseSeed: 'audio-test-macsafari',
        plugins: [{ name: 'WebKit built-in PDF', filename: 'internal-pdf-viewer', description: 'Portable Document Format' }],
        browser: 'webkit',
        orientation: 'landscape-primary',
        userAgentData: null,
        innerWidth: 1440, innerHeight: 830
      });
    },

    linuxChrome: function () {
      var ua = 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36';
      return base({
        userAgent: ua,
        appVersion: ua.replace('Mozilla/', ''),
        platform: 'Linux x86_64',
        vendor: 'Google Inc.',
        language: 'en-US',
        languages: ['en-US', 'en'],
        timezone: 'Europe/Berlin',
        screenWidth: 1920, screenHeight: 1080,
        availWidth: 1920, availHeight: 1050,
        colorDepth: 24, devicePixelRatio: 1.0,
        hardwareConcurrency: 12, deviceMemory: 8, maxTouchPoints: 0,
        webglVendor: 'Google Inc. (NVIDIA)',
        webglRenderer: 'ANGLE (NVIDIA, NVIDIA GeForce GTX 1650 Direct3D11 vs_5_0 ps_5_0, D3D11)',
        canvasNoiseSeed: 'canvas-test-linux',
        audioNoiseSeed: 'audio-test-linux',
        plugins: DESKTOP_PLUGINS,
        orientation: 'landscape-primary',
        userAgentData: chromeUaData('Linux', '6.8.0', false, CHROME_FULL),
        innerWidth: 1920, innerHeight: 960
      });
    },

    androidChrome: function () {
      var ua = 'Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36';
      return base({
        userAgent: ua,
        appVersion: ua.replace('Mozilla/', ''),
        platform: 'Linux armv8l',
        vendor: 'Google Inc.',
        language: 'ja-JP',
        languages: ['ja-JP', 'ja', 'en-US', 'en'],
        timezone: 'Asia/Tokyo',
        screenWidth: 412, screenHeight: 915,
        availWidth: 412, availHeight: 915,
        colorDepth: 24, devicePixelRatio: 2.625,
        hardwareConcurrency: 8, deviceMemory: 8, maxTouchPoints: 5,
        webglVendor: 'Qualcomm',
        webglRenderer: 'ANGLE (Qualcomm, Adreno (TM) 730, OpenGL ES 3.2)',
        canvasNoiseSeed: 'canvas-test-android',
        audioNoiseSeed: 'audio-test-android',
        plugins: [],
        spoofViewport: false,
        orientation: 'portrait-primary',
        userAgentData: chromeUaData('Android', '14.0.0', true, CHROME_FULL),
        innerWidth: 412, innerHeight: 915
      });
    },

    iphoneSafari: function () {
      var ua = 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.6 Mobile/15E148 Safari/604.1';
      return base({
        userAgent: ua,
        appVersion: ua.replace('Mozilla/', ''),
        platform: 'iPhone',
        vendor: 'Apple Computer, Inc.',
        language: 'en-US',
        languages: ['en-US', 'en'],
        timezone: 'America/New_York',
        screenWidth: 390, screenHeight: 844,
        availWidth: 390, availHeight: 844,
        colorDepth: 32, devicePixelRatio: 3.0,
        hardwareConcurrency: 8, deviceMemory: 8, maxTouchPoints: 5,
        webglVendor: 'Apple Inc.',
        webglRenderer: 'Apple GPU',
        canvasNoiseSeed: 'canvas-test-iphone',
        audioNoiseSeed: 'audio-test-iphone',
        plugins: [],
        spoofViewport: false,
        browser: 'webkit',
        orientation: 'portrait-primary',
        userAgentData: null,
        innerWidth: 390, innerHeight: 664
      });
    }
  };
});
