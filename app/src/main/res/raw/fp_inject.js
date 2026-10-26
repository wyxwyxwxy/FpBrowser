/*
 * FpBrowser · 指纹注入引擎
 * ------------------------------------------------------------------
 * 注入时机：document-start（WebViewCompat.addDocumentStartJavaScript），
 *           早于页面自身脚本执行，覆盖所有 frame。
 * 配置来源：window.__FP_CONFIG__（由宿主 App 在同一批注入的 prelude 脚本中写入，
 *           并合并 window.name 中的 "__FP_OVERRIDE__" 临时覆盖，用于"请求桌面版"等）。
 *
 * 设计原则：
 * 1. 只覆盖、不新增全局泄漏——所有补丁集中在 IIFE 内；
 * 2. Function.prototype.toString 伪装，使补丁函数呈现 [native code]；
 * 3. 噪声由种子驱动，同一种子跨会话产生同一结果（保证指纹稳定、跨站一致）。
 *
 * 平台限制（无法在 JS 层解决，详见 README）：
 * - Sec-CH-UA 等 User-Agent Client Hints 请求头由 Chromium 网络栈发出，JS 无法覆盖；
 * - CSS 媒体查询按物理视口计算，JS 覆盖 innerWidth 不影响 @media；
 * - 字体列表取真实系统字体。
 */
(function () {
  'use strict';

  var CFG = {};
  try {
    CFG = window.__FP_CONFIG__ || {};
  } catch (e) {}

  var IS_DESKTOP = CFG.platform === 'Win32' || CFG.platform === 'MacIntel' || CFG.platform === 'Linux x86_64';
  var IS_CHROMIUM = CFG.browser !== 'webkit';
  var HAS_TOUCH = !!(CFG.maxTouchPoints && CFG.maxTouchPoints > 0);

  /* ============================ 基础工具 ============================ */

  var PATCHED = new WeakSet();
  function mark(fn) { try { PATCHED.add(fn); } catch (e) {} return fn; }

  function defValue(obj, prop, value, enumerable) {
    try {
      Object.defineProperty(obj, prop, {
        get: mark(function () { return value; }),
        set: mark(function () {}),
        configurable: true,
        enumerable: enumerable !== false
      });
    } catch (e) {}
  }

  function defFn(obj, prop, fn) {
    try {
      Object.defineProperty(obj, prop, {
        value: mark(fn), writable: true, configurable: true, enumerable: false
      });
    } catch (e) {}
  }

  function hashStr(s) {
    var h = 2166136261 >>> 0;
    s = String(s);
    for (var i = 0; i < s.length; i++) { h ^= s.charCodeAt(i); h = Math.imul(h, 16777619) >>> 0; }
    return h >>> 0;
  }

  function mulberry32(a) {
    return function () {
      a |= 0; a = (a + 0x6D2B79F5) | 0;
      var t = Math.imul(a ^ (a >>> 15), 1 | a);
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }

  /* ============ 0. Function.prototype.toString 伪装（最先执行） ============ */

  (function () {
    var origToString = Function.prototype.toString;
    var fake = function toString() {
      if (PATCHED.has(this)) {
        var n = '';
        try { n = this.name || ''; } catch (e) {}
        return 'function ' + n + '() { [native code] }';
      }
      return origToString.call(this);
    };
    Object.defineProperty(Function.prototype, 'toString', {
      value: mark(fake), writable: true, configurable: true
    });
  })();

  /* ============================ 1. navigator ============================ */

  var nav = navigator;
  if (CFG.userAgent) defValue(nav, 'userAgent', CFG.userAgent);
  if (CFG.appVersion) defValue(nav, 'appVersion', CFG.appVersion);
  if (CFG.platform) defValue(nav, 'platform', CFG.platform);
  if (CFG.vendor) defValue(nav, 'vendor', CFG.vendor);
  defValue(nav, 'product', 'Gecko');
  defValue(nav, 'productSub', '20030107');
  defValue(nav, 'vendorSub', '');
  if (CFG.language) defValue(nav, 'language', CFG.language);
  if (CFG.languages && CFG.languages.length) defValue(nav, 'languages', CFG.languages.slice());
  if (typeof CFG.hardwareConcurrency === 'number' && CFG.hardwareConcurrency > 0) {
    defValue(nav, 'hardwareConcurrency', CFG.hardwareConcurrency);
  }
  if (IS_CHROMIUM && typeof CFG.deviceMemory === 'number' && CFG.deviceMemory > 0) {
    defValue(nav, 'deviceMemory', CFG.deviceMemory);
  }
  defValue(nav, 'maxTouchPoints', HAS_TOUCH ? (CFG.maxTouchPoints || 1) : 0);
  defValue(nav, 'msMaxTouchPoints', HAS_TOUCH ? (CFG.maxTouchPoints || 1) : 0);
  defValue(nav, 'webdriver', false);
  defValue(nav, 'pdfViewerEnabled', CFG.pdfViewerEnabled !== false);
  defValue(nav, 'cookieEnabled', true);
  if (CFG.doNotTrack) defValue(nav, 'doNotTrack', CFG.doNotTrack);
  defFn(nav, 'javaEnabled', function javaEnabled() { return false; });

  /* ---- 插件 / MIME 类型（桌面 Chromium 特征） ---- */
  function makePluginArray(list) {
    var items = [];
    for (var i = 0; i < list.length; i++) {
      items.push({
        name: list[i].name,
        filename: list[i].filename,
        description: list[i].description,
        length: 1,
        item: function () { return null; },
        namedItem: function () { return null; }
      });
    }
    var arr = items.slice();
    arr.item = function (i) { return (typeof i === 'number' && items[i]) || null; };
    arr.namedItem = function (n) {
      for (var j = 0; j < items.length; j++) if (items[j].name === n) return items[j];
      return null;
    };
    arr.refresh = function () {};
    try { arr[Symbol.toStringTag] = 'PluginArray'; } catch (e) {}
    return arr;
  }

  function makeMimeTypeArray(list) {
    var items = [];
    for (var i = 0; i < list.length; i++) {
      items.push({
        type: list[i].type,
        suffixes: list[i].suffixes,
        description: list[i].description,
        enabledPlugin: null
      });
    }
    var arr = items.slice();
    arr.item = function (i) { return (typeof i === 'number' && items[i]) || null; };
    arr.namedItem = function (n) {
      for (var j = 0; j < items.length; j++) if (items[j].type === n) return items[j];
      return null;
    };
    try { arr[Symbol.toStringTag] = 'MimeTypeArray'; } catch (e) {}
    return arr;
  }

  if (CFG.plugins && CFG.plugins.length) defValue(nav, 'plugins', makePluginArray(CFG.plugins), false);
  if (CFG.mimeTypes && CFG.mimeTypes.length) defValue(nav, 'mimeTypes', makeMimeTypeArray(CFG.mimeTypes), false);

  /* ---- User-Agent Client Hints（JS 层） ---- */
  if (CFG.userAgentData && IS_CHROMIUM) {
    var uad = {
      brands: CFG.userAgentData.brands,
      mobile: !!CFG.userAgentData.mobile,
      platform: CFG.userAgentData.platform,
      getHighEntropyValues: function getHighEntropyValues(hints) {
        return Promise.resolve({
          architecture: 'x86',
          bitness: '64',
          model: '',
          platformVersion: CFG.userAgentData.platformVersion || '15.0.0',
          uaFullVersion: CFG.userAgentData.fullVersion || '',
          fullVersionList: CFG.userAgentData.brands,
          wow64: false,
          mobile: !!CFG.userAgentData.mobile,
          platform: CFG.userAgentData.platform,
          brands: CFG.userAgentData.brands
        });
      },
      toJSON: function toJSON() {
        return {
          brands: CFG.userAgentData.brands,
          mobile: !!CFG.userAgentData.mobile,
          platform: CFG.userAgentData.platform
        };
      }
    };
    defValue(nav, 'userAgentData', uad);
  }

  /* ---- NetworkInformation（Chromium） ---- */
  if (IS_CHROMIUM) {
    defValue(nav, 'connection', {
      effectiveType: '4g',
      downlink: typeof CFG.downlink === 'number' ? CFG.downlink : 10,
      rtt: typeof CFG.rtt === 'number' ? CFG.rtt : 50,
      saveData: false,
      onchange: null,
      addEventListener: function addEventListener() {},
      removeEventListener: function removeEventListener() {},
      dispatchEvent: function dispatchEvent() { return true; }
    });
  }

  /* ---- Battery API（Safari 没有，补一个稳定值） ---- */
  if (!nav.getBattery) {
    defFn(nav, 'getBattery', function getBattery() {
      return Promise.resolve({
        level: 0.72 + (hashStr(CFG.userAgent || 'fp') % 20) / 100,
        charging: true,
        chargingTime: 0,
        dischargingTime: Infinity,
        onlevelchange: null,
        onchargingchange: null,
        addEventListener: function addEventListener() {},
        removeEventListener: function removeEventListener() {},
        dispatchEvent: function dispatchEvent() { return true; }
      });
    });
  }

  /* ============================ 2. screen / 窗口几何 ============================ */

  var scr = window.screen;
  if (scr) {
    if (CFG.screenWidth) defValue(scr, 'width', CFG.screenWidth | 0);
    if (CFG.screenHeight) defValue(scr, 'height', CFG.screenHeight | 0);
    defValue(scr, 'availWidth', (CFG.availWidth || CFG.screenWidth || 0) | 0);
    defValue(scr, 'availHeight', (CFG.availHeight || CFG.screenHeight || 0) | 0);
    defValue(scr, 'availTop', 0);
    defValue(scr, 'availLeft', 0);
    defValue(scr, 'top', 0);
    defValue(scr, 'left', 0);
    if (CFG.colorDepth) {
      defValue(scr, 'colorDepth', CFG.colorDepth | 0);
      defValue(scr, 'pixelDepth', CFG.colorDepth | 0);
    }
    if (scr.orientation) {
      defValue(scr.orientation, 'type', CFG.orientation || 'landscape-primary');
      defValue(scr.orientation, 'angle', 0);
    }
  }

  if (typeof CFG.devicePixelRatio === 'number') {
    defValue(window, 'devicePixelRatio', CFG.devicePixelRatio);
  }

  if (IS_DESKTOP && CFG.spoofViewport !== false) {
    var aw = (CFG.availWidth || CFG.screenWidth || 1920) | 0;
    var ah = (CFG.availHeight || CFG.screenHeight || 1080) | 0;
    defValue(window, 'outerWidth', aw);
    defValue(window, 'outerHeight', ah);
    defValue(window, 'innerWidth', (CFG.innerWidth || aw) | 0);
    defValue(window, 'innerHeight', (CFG.innerHeight || (ah - 90)) | 0);
    defValue(window, 'screenX', (CFG.screenX || 0) | 0);
    defValue(window, 'screenY', (CFG.screenY || 0) | 0);
    defValue(window, 'pageXOffset', 0);
    defValue(window, 'pageYOffset', 0);
  }

  if (HAS_TOUCH) {
    defValue(window, 'ontouchstart', function ontouchstart() {});
  } else {
    try { delete window.ontouchstart; } catch (e) {}
  }

  /* ---- matchMedia：指针 / 悬停能力 ---- */
  (function () {
    var orig = window.matchMedia;
    if (typeof orig !== 'function') return;
    function spoof(q) {
      if (typeof q !== 'string') return orig.call(window, q);
      var ptr = HAS_TOUCH ? 'coarse' : 'fine';
      var hov = HAS_TOUCH ? 'none' : 'hover';
      var s = q;
      s = s.replace(/\(\s*(any-)?pointer\s*:\s*(coarse|fine)\s*\)/g,
        function (m, any) { return '(' + (any || '') + 'pointer: ' + ptr + ')'; });
      s = s.replace(/\(\s*(any-)?hover\s*:\s*(none|hover)\s*\)/g,
        function (m, any) { return '(' + (any || '') + 'hover: ' + hov + ')'; });
      return orig.call(window, s);
    }
    defFn(window, 'matchMedia', spoof);
  })();

  /* ==================== 3. 时区（Intl + Date） ==================== */

  var TZ = CFG.timezone || 'UTC';
  var offsetCache = {};
  var origGetTimezoneOffset = Date.prototype.getTimezoneOffset;

  function pad2(n) { return (n < 10 ? '0' : '') + n; }

  function offsetMinutes(date) {
    var key = TZ + '|' + Math.floor(date.getTime() / 60000);
    if (offsetCache.hasOwnProperty(key)) return offsetCache[key];
    var off = -origGetTimezoneOffset.call(date);
    try {
      var parts = new Intl.DateTimeFormat('en-US', { timeZone: TZ, timeZoneName: 'longOffset' }).formatToParts(date);
      for (var i = 0; i < parts.length; i++) {
        if (parts[i].type === 'timeZoneName') {
          var m = /GMT([+-])(\d{1,2})(?::(\d{2}))?/.exec(parts[i].value);
          if (m) off = (m[1] === '-' ? -1 : 1) * (parseInt(m[2], 10) * 60 + parseInt(m[3] || '0', 10));
        }
      }
    } catch (e) {}
    offsetCache[key] = off;
    return off;
  }

  var OrigDTF = Intl.DateTimeFormat;
  function PatchedDTF(locales, options) {
    var o = options;
    if (!o || typeof o !== 'object') {
      // 无参 / 非对象参数：默认按目标时区构造（检测脚本常用 new Intl.DateTimeFormat().resolvedOptions()）
      o = { timeZone: TZ };
    } else if (!('timeZone' in o)) {
      o = Object.assign({}, o, { timeZone: TZ });
    }
    return new OrigDTF(locales, o);
  }
  PatchedDTF.prototype = OrigDTF.prototype;
  PatchedDTF.supportedLocalesOf = function supportedLocalesOf(locales, options) {
    return OrigDTF.supportedLocalesOf(locales, options);
  };
  try { Intl.DateTimeFormat = PatchedDTF; } catch (e) {}

  var DAY = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
  var MON = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
  var tzNameCache = null;

  function tzDisplayName() {
    if (tzNameCache) return tzNameCache;
    try {
      var parts = new OrigDTF('en-US', { timeZone: TZ, timeZoneName: 'long' }).formatToParts(new Date());
      for (var i = 0; i < parts.length; i++) {
        if (parts[i].type === 'timeZoneName') { tzNameCache = parts[i].value; break; }
      }
    } catch (e) {}
    if (!tzNameCache) tzNameCache = TZ;
    return tzNameCache;
  }

  function tzParts(d) {
    var p = {};
    try {
      var parts = new OrigDTF('en-US', {
        timeZone: TZ, hour12: false,
        weekday: 'short', year: 'numeric', month: '2-digit', day: '2-digit',
        hour: '2-digit', minute: '2-digit', second: '2-digit'
      }).formatToParts(d);
      for (var i = 0; i < parts.length; i++) p[parts[i].type] = parts[i].value;
    } catch (e) {}
    return p;
  }

  function gmtString(off) {
    var a = Math.abs(off);
    return 'GMT' + (off < 0 ? '-' : '+') + pad2(Math.floor(a / 60)) + pad2(a % 60);
  }

  // getTimezoneOffset 语义为「UTC − 本地时间」（东八区返回 -480），需对 UTC 偏移取反
  Date.prototype.getTimezoneOffset = mark(function getTimezoneOffset() { return -offsetMinutes(this); });

  Date.prototype.toString = mark(function toString() {
    var p = tzParts(this);
    var off = offsetMinutes(this);
    var h = parseInt(p.hour, 10); if (h === 24) h = 0;
    return (DAY[p.weekday] || 'Thu') + ' ' + (MON[(parseInt(p.month, 10) || 1) - 1] || 'Jan') + ' ' +
      pad2(parseInt(p.day, 10) || 1) + ' ' + (p.year || '1970') + ' ' +
      pad2(h) + ':' + (p.minute || '00') + ':' + (p.second || '00') + ' ' +
      gmtString(off) + ' (' + tzDisplayName() + ')';
  });

  Date.prototype.toDateString = mark(function toDateString() {
    var p = tzParts(this);
    return (DAY[p.weekday] || 'Thu') + ' ' + (MON[(parseInt(p.month, 10) || 1) - 1] || 'Jan') + ' ' +
      pad2(parseInt(p.day, 10) || 1) + ' ' + (p.year || '1970');
  });

  Date.prototype.toTimeString = mark(function toTimeString() {
    var p = tzParts(this);
    var off = offsetMinutes(this);
    var h = parseInt(p.hour, 10); if (h === 24) h = 0;
    return pad2(h) + ':' + (p.minute || '00') + ':' + (p.second || '00') + ' ' +
      gmtString(off) + ' (' + tzDisplayName() + ')';
  });

  Date.prototype.toLocaleString = mark(function toLocaleString(locales, options) {
    try { return new PatchedDTF(locales, options).format(this); } catch (e) { return OrigDTF.prototype.format.call(new OrigDTF('en-US', { timeZone: TZ }), this); }
  });
  Date.prototype.toLocaleDateString = mark(function toLocaleDateString(locales, options) {
    try { return new PatchedDTF(locales, options).format(this); } catch (e) { return OrigDTF.prototype.format.call(new OrigDTF('en-US', { timeZone: TZ }), this); }
  });
  Date.prototype.toLocaleTimeString = mark(function toLocaleTimeString(locales, options) {
    try { return new PatchedDTF(locales, options).format(this); } catch (e) { return OrigDTF.prototype.format.call(new OrigDTF('en-US', { timeZone: TZ }), this); }
  });

  /* ==================== 4. WebGL ==================== */

  function patchWebGL(ctor) {
    if (!ctor || !ctor.prototype) return;
    var proto = ctor.prototype;
    if (proto.__fpPatched) return;
    proto.__fpPatched = true;

    var origGetParameter = proto.getParameter;
    if (origGetParameter) {
      proto.getParameter = mark(function getParameter(pname) {
        if (pname === 0x9245 && CFG.webglVendor) return CFG.webglVendor;      // UNMASKED_VENDOR_WEBGL
        if (pname === 0x9246 && CFG.webglRenderer) return CFG.webglRenderer;  // UNMASKED_RENDERER_WEBGL
        return origGetParameter.call(this, pname);
      });
    }
    var origExt = proto.getSupportedExtensions;
    if (origExt && CFG.webglExtensions && CFG.webglExtensions.length) {
      proto.getSupportedExtensions = mark(function getSupportedExtensions() {
        return CFG.webglExtensions.slice();
      });
    }
    var origAttr = proto.getContextAttributes;
    if (origAttr) {
      proto.getContextAttributes = mark(function getContextAttributes() {
        var a = origAttr.call(this);
        try {
          if (CFG.webglRenderer) a.renderer = CFG.webglRenderer;
          if (CFG.webglVendor) a.vendor = CFG.webglVendor;
        } catch (e) {}
        return a;
      });
    }
  }
  patchWebGL(window.WebGLRenderingContext);
  patchWebGL(window.WebGL2RenderingContext);

  /* ==================== 5. Canvas 2D（种子噪声） ==================== */

  (function () {
    var proto = window.CanvasRenderingContext2D && window.CanvasRenderingContext2D.prototype;
    if (!proto || proto.__fpPatched) return;
    proto.__fpPatched = true;
    var seed = hashStr(CFG.canvasNoiseSeed || 'fp');

    var origGetImageData = proto.getImageData;
    if (origGetImageData) {
      proto.getImageData = mark(function getImageData(x, y, w, h) {
        var img = origGetImageData.call(this, x, y, w, h);
        try {
          if (CFG.canvasMode !== 'off' && img && img.data) {
            var d = img.data;
            // 同一 (种子, x, y) 恒定输出同一噪声 → 跨会话一致
            var r = mulberry32((seed ^ Math.imul(x | 0, 73856093) ^ Math.imul(y | 0, 19349663)) >>> 0);
            var n = Math.min(d.length, 1052672); // 上限约 26 万像素，控制性能
            for (var i = 0; i < n; i += 4) {
              if (r() < 0.05) {
                var v = d[i] + (r() < 0.5 ? 1 : -1);
                d[i] = v < 0 ? 0 : (v > 255 ? 255 : v);
              }
            }
          }
        } catch (e) {}
        return img;
      });
    }

    var origMeasure = proto.measureText;
    if (origMeasure) {
      proto.measureText = mark(function measureText(text) {
        var m = origMeasure.call(this, text);
        try {
          if (CFG.canvasMode !== 'off' && m) {
            var r = mulberry32((seed ^ hashStr(String(text))) >>> 0)();
            var eps = 0.00005 + r * 0.00045;
            var keys = ['actualBoundingBoxAscent', 'actualBoundingBoxDescent',
              'actualBoundingBoxLeft', 'actualBoundingBoxRight', 'alphabeticBaseline',
              'emHeightAscent', 'emHeightDescent', 'fontBoundingBoxAscent',
              'fontBoundingBoxDescent', 'hangingBaseline', 'ideographicBaseline', 'romanBaseline'];
            var fake = {};
            for (var i = 0; i < keys.length; i++) fake[keys[i]] = m[keys[i]];
            fake.width = m.width + (r < 0.5 ? eps : -eps);
            return fake;
          }
        } catch (e) {}
        return m;
      });
    }
  })();

  /* ==================== 6. AudioContext（种子噪声） ==================== */

  (function () {
    var proto = window.AudioBuffer && window.AudioBuffer.prototype;
    if (!proto || proto.__fpPatched) return;
    proto.__fpPatched = true;
    var seed = hashStr(CFG.audioNoiseSeed || 'fp');

    var origGet = proto.getChannelData;
    if (origGet) {
      proto.getChannelData = mark(function getChannelData(ch) {
        var data = origGet.call(this, ch);
        try {
          if (CFG.audioMode !== 'off' && data && data.length) {
            var r = mulberry32((seed ^ Math.imul((ch | 0) + 1, 2246822519)) >>> 0);
            var n = Math.min(data.length, 1 << 16);
            for (var i = 0; i < n; i++) {
              if (r() < 0.0006) data[i] = data[i] + (r() - 0.5) * 0.0001;
            }
          }
        } catch (e) {}
        return data;
      });
    }

    var origCopy = proto.copyFromChannel;
    if (origCopy) {
      proto.copyFromChannel = mark(function copyFromChannel(dest, ch, off) {
        var ret = origCopy.call(this, dest, ch, off);
        try {
          if (CFG.audioMode !== 'off' && dest && dest.length) {
            var r = mulberry32((seed ^ 0x9E3779B9 ^ Math.imul((ch | 0) + 1, 2246822519)) >>> 0);
            for (var i = 0; i < dest.length; i++) {
              if (r() < 0.0006) dest[i] = dest[i] + (r() - 0.5) * 0.0001;
            }
          }
        } catch (e) {}
        return ret;
      });
    }
  })();

  /* ==================== 7. WebRTC（防真实 IP 泄漏） ==================== */

  (function () {
    var mode = CFG.webRtcMode || 'default';
    if (mode === 'default') return;
    var RTC = window.RTCPeerConnection || window.webkitRTCPeerConnection;
    if (typeof RTC !== 'function') return;

    function passCandidate(ev) {
      try {
        if (ev && ev.candidate && ev.candidate.candidate &&
            ev.candidate.candidate.indexOf(' relay ') === -1) return false;
      } catch (e) {}
      return true;
    }

    var PatchedRTC = function RTCPeerConnection(config, constraints) {
      if (mode === 'disabled') {
        throw new DOMException('WebRTC has been disabled by fingerprint policy', 'NotSupportedError');
      }
      var c = {};
      if (config) { for (var k in config) c[k] = config[k]; }
      c.iceTransportPolicy = 'relay'; // 仅走 TURN 中继，不暴露主机地址
      var pc = new RTC(c, constraints);

      var origAEL = pc.addEventListener.bind(pc);
      pc.addEventListener = function (type, fn, opts) {
        if (type === 'icecandidate' && typeof fn === 'function') {
          var wrapped = function (ev) { if (passCandidate(ev)) return fn.call(this, ev); };
          return origAEL(type, wrapped, opts);
        }
        return origAEL(type, fn, opts);
      };

      var ocDesc = Object.getOwnPropertyDescriptor(pc, 'onicecandidate');
      if (ocDesc && ocDesc.configurable) {
        Object.defineProperty(pc, 'onicecandidate', {
          get: function () { return ocDesc.get.call(pc); },
          set: function (fn) {
            if (typeof fn !== 'function') { ocDesc.set.call(pc, fn); return; }
            ocDesc.set.call(pc, function (ev) { if (passCandidate(ev)) return fn.call(this, ev); });
          },
          configurable: true
        });
      }
      return pc;
    };
    PatchedRTC.prototype = RTC.prototype;
    window.RTCPeerConnection = PatchedRTC;
    if (window.webkitRTCPeerConnection) window.webkitRTCPeerConnection = PatchedRTC;
  })();

  /* ==================== 8. 桌面 Chromium 专属对象 ==================== */

  if (IS_DESKTOP && IS_CHROMIUM && !window.chrome) {
    window.chrome = {
      app: {
        isInstalled: false,
        InstallState: { DISABLED: 'disabled', INSTALLED: 'installed', NOT_INSTALLED: 'not_installed' },
        getDetails: function getDetails() { return null; },
        getIsInstalled: function getIsInstalled() { return false; },
        installState: function installState(cb) { try { if (cb) cb('not_installed'); } catch (e) {} }
      },
      runtime: {},
      csi: function csi() {
        return {
          startE: Date.now(), onloadT: Date.now(),
          pageT: Date.now() + 120 + (hashStr(CFG.userAgent || 'fp') % 300), tran: 15
        };
      },
      loadTimes: function loadTimes() {
        var t = Date.now() / 1000;
        return {
          requestTime: t, startLoadTime: t, commitLoadTime: t,
          finishDocumentLoadTime: t, finishLoadTime: t, firstPaintTime: t,
          firstPaintAfterLoadTime: 0, navigationType: 'Other',
          wasFetchedViaSpdy: true, wasNpnNegotiated: true,
          npnNegotiatedProtocol: 'h2', wasAlternateProtocolAvailable: false,
          connectionInfo: 'h2'
        };
      }
    };
  }

  if (IS_DESKTOP && IS_CHROMIUM && !window.external) {
    window.external = {
      AddSearchProvider: function AddSearchProvider() {},
      IsSearchProviderInstalled: function IsSearchProviderInstalled() { return false; }
    };
  }

  if (IS_DESKTOP && IS_CHROMIUM && window.performance && !performance.memory) {
    try {
      Object.defineProperty(performance, 'memory', {
        value: {
          jsHeapSizeLimit: 4294705152,
          totalJSHeapSize: 32000000 + (hashStr(CFG.canvasNoiseSeed || 'fp') % 9000000),
          usedJSHeapSize: 26000000 + (hashStr(CFG.audioNoiseSeed || 'fp') % 6000000)
        },
        configurable: true
      });
    } catch (e) {}
  }
})();
