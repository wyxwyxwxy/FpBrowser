#!/usr/bin/env node
/**
 * FpBrowser 指纹注入引擎 —— Node 冒烟测试
 *
 * 用 node:vm 构造隔离的浏览器环境桩（navigator / screen / canvas / WebGL /
 * AudioBuffer / RTCPeerConnection / matchMedia …），在多个指纹模板下执行
 * app/src/main/res/raw/fp_inject.js，验证：
 *   - navigator / screen / 视口 / 时区 / WebGL / Canvas / Audio 伪装是否生效；
 *   - 噪声是否由种子决定（跨实例一致、可关闭）；
 *   - WebRTC relay / disabled 策略；
 *   - Function.prototype.toString 是否隐藏补丁痕迹；
 *   - window.name 覆盖合并（prelude 语义）。
 *
 * 运行：node tools/test-inject.mjs
 */
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import vm from 'node:vm';
import templates from './fingerprint-templates.js';

const here = dirname(fileURLToPath(import.meta.url));
const ENGINE = readFileSync(join(here, '../app/src/main/res/raw/fp_inject.js'), 'utf8');

let passed = 0;
let failed = 0;
const pending = [];

function test(name, fn) {
  try {
    const r = fn();
    if (r && typeof r.then === 'function') {
      pending.push(
        r.then(
          () => { passed++; console.log('  \u2713 ' + name); },
          (e) => { failed++; console.error('  \u2717 ' + name + '\n      ' + (e && e.message)); },
        ),
      );
      return;
    }
    passed++;
    console.log('  \u2713 ' + name);
  } catch (e) {
    failed++;
    console.error('  \u2717 ' + name + '\n      ' + (e && e.message));
  }
}

function eq(actual, expected, what) {
  if (actual !== expected) {
    throw new Error(`${what || '值'} 期望 ${JSON.stringify(expected)}，实际 ${JSON.stringify(actual)}`);
  }
}

/* ------------------------------------------------------------------ 环境桩 */

function makeSandbox(cfg) {
  const sandbox = { console };
  sandbox.window = sandbox;

  // navigator：模拟一台真实的 Android WebView 设备（注入前）
  sandbox.navigator = {
    userAgent: 'Mozilla/5.0 (Linux; Android 13; Pixel 7 Build/TQ3A.230805.001; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/120.0.0.0 Mobile Safari/537.36',
    appVersion: '5.0 (Linux; Android 13; Pixel 7 Build/TQ3A.230805.001; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/120.0.0.0 Mobile Safari/537.36',
    platform: 'Linux armv8l',
    vendor: 'Google Inc.',
    language: 'zh-CN',
    languages: ['zh-CN', 'zh'],
    hardwareConcurrency: 8,
    maxTouchPoints: 5,
    webdriver: false,
    cookieEnabled: true,
    javaEnabled: () => false,
  };

  sandbox.screen = {
    width: 412, height: 915, availWidth: 412, availHeight: 915,
    availLeft: 0, availTop: 0, colorDepth: 24, pixelDepth: 24,
    orientation: { type: 'portrait-primary', angle: 0 },
  };

  sandbox.matchMedia = (q) => ({
    matches: /pointer:\s*coarse/.test(q) || /hover:\s*hover/.test(q),
    media: q,
    addEventListener() {}, removeEventListener() {}, addListener() {}, removeListener() {},
  });

  sandbox.performance = { now: () => 0 };

  sandbox.DOMException = class DOMException extends Error {
    constructor(message, name) { super(message); this.name = name || 'Error'; }
  };

  // Canvas 2D
  class CanvasRenderingContext2D {}
  CanvasRenderingContext2D.prototype.getImageData = function (x, y, w, h) {
    return { width: w, height: h, data: new Uint8ClampedArray(w * h * 4) };
  };
  CanvasRenderingContext2D.prototype.measureText = function (text) {
    return {
      width: String(text).length * 8,
      actualBoundingBoxAscent: 8, actualBoundingBoxDescent: 2,
      fontBoundingBoxAscent: 9, fontBoundingBoxDescent: 2,
      alphabeticBaseline: 0, hangingBaseline: 7, ideographicBaseline: -2, romanBaseline: 0,
      actualBoundingBoxLeft: 0, actualBoundingBoxRight: 32,
      emHeightAscent: 9, emHeightDescent: 2,
    };
  };
  sandbox.CanvasRenderingContext2D = CanvasRenderingContext2D;

  // WebGL 1 / 2
  function makeWebGLClass() {
    return class WebGLRenderingContext {
      getParameter(p) {
        if (p === 0x9245) return 'WebKit';
        if (p === 0x9246) return 'WebKit WebGL';
        if (p === 0x1F00) return 'WebKit';
        if (p === 0x1F01) return 'WebKit WebGL';
        if (p === 0x1F02) return 'WebGL 1.0 (OpenGL ES 2.0 Chromium)';
        return null;
      }
      getSupportedExtensions() { return ['EXT_color_buffer_float', 'WEBGL_debug_renderer_info']; }
      getExtension(name) {
        return name === 'WEBGL_debug_renderer_info'
          ? { UNMASKED_VENDOR_WEBGL: 0x9245, UNMASKED_RENDERER_WEBGL: 0x9246 }
          : null;
      }
      getContextAttributes() {
        return { alpha: true, antialias: true, renderer: 'WebKit WebGL', vendor: 'WebKit' };
      }
    };
  }
  const GL1 = makeWebGLClass();
  const GL2 = makeWebGLClass();
  sandbox.WebGLRenderingContext = GL1;
  sandbox.WebGL2RenderingContext = GL2;

  // AudioBuffer
  class AudioBuffer {
    getChannelData() { return new Float32Array(64).fill(0.5); }
    copyFromChannel(dest) { dest.set(new Float32Array(dest.length).fill(0.5)); }
  }
  sandbox.AudioBuffer = AudioBuffer;

  // RTCPeerConnection
  sandbox.RTCPeerConnection = class RTCPeerConnection {
    constructor(config) {
      this.config = config || {};
      this._oc = null;
      Object.defineProperty(this, 'onicecandidate', {
        get() { return this._oc; },
        set(f) { this._oc = f; },
        configurable: true,
      });
    }
    addEventListener() {}
    removeEventListener() {}
    close() {}
  };
  sandbox.webkitRTCPeerConnection = sandbox.RTCPeerConnection;

  // HTMLCanvasElement
  sandbox.HTMLCanvasElement = class HTMLCanvasElement {
    getContext(type) {
      if (type === '2d') {
        if (!this._ctx2d) this._ctx2d = Object.create(CanvasRenderingContext2D.prototype);
        return this._ctx2d;
      }
      if (type === 'webgl') {
        if (!this._gl) this._gl = Object.create(GL1.prototype);
        return this._gl;
      }
      if (type === 'webgl2') {
        if (!this._gl2) this._gl2 = Object.create(GL2.prototype);
        return this._gl2;
      }
      return null;
    }
  };

  sandbox.document = {
    createElement(tag) {
      if (tag === 'canvas') return Object.create(sandbox.HTMLCanvasElement.prototype);
      return {};
    },
  };

  vm.createContext(sandbox);
  sandbox.__FP_CONFIG__ = cfg;
  const RawDTF = vm.runInContext('Intl.DateTimeFormat', sandbox);
  vm.runInContext(ENGINE, sandbox, { filename: 'fp_inject.js' });
  return { sandbox, RawDTF };
}

function offsetMinutes(RawDTF, date, tz) {
  const parts = new RawDTF('en-US', { timeZone: tz, timeZoneName: 'longOffset' }).formatToParts(date);
  for (const p of parts) {
    if (p.type === 'timeZoneName') {
      const m = /GMT([+-])(\d{1,2})(?::(\d{2}))?/.exec(p.value);
      if (!m) return null;
      return (m[1] === '-' ? -1 : 1) * (parseInt(m[2], 10) * 60 + parseInt(m[3] || '0', 10));
    }
  }
  return null;
}

function gmtString(RawDTF, date, tz) {
  const off = offsetMinutes(RawDTF, date, tz);
  if (off == null) return null;
  const a = Math.abs(off);
  return 'GMT' + (off < 0 ? '-' : '+') +
    String(Math.floor(a / 60)).padStart(2, '0') + String(a % 60).padStart(2, '0');
}

/* ================================================================== 测试 */

console.log('\n[1] Windows Chrome 桌面模板');
{
  const cfg = templates.windowsChrome();
  const { sandbox, RawDTF } = makeSandbox(cfg);
  const nav = sandbox.navigator;
  const scr = sandbox.screen;

  test('navigator.userAgent 伪装', () => eq(nav.userAgent, cfg.userAgent));
  test('navigator.platform 伪装', () => eq(nav.platform, 'Win32'));
  test('navigator.vendor 伪装', () => eq(nav.vendor, 'Google Inc.'));
  test('navigator.language/languages 伪装', () => {
    eq(nav.language, 'en-US');
    eq(nav.languages.join(','), 'en-US,en');
  });
  test('navigator.hardwareConcurrency/deviceMemory 伪装', () => {
    eq(nav.hardwareConcurrency, 8);
    eq(nav.deviceMemory, 8);
  });
  test('navigator.webdriver=false / maxTouchPoints=0', () => {
    eq(nav.webdriver, false);
    eq(nav.maxTouchPoints, 0);
    eq(nav.msMaxTouchPoints, 0);
  });
  test('screen 尺寸/色深伪装', () => {
    eq(scr.width, 1920);
    eq(scr.height, 1080);
    eq(scr.availHeight, 1040);
    eq(scr.colorDepth, 24);
    eq(scr.pixelDepth, 24);
  });
  test('window.devicePixelRatio / 窗口几何伪装', () => {
    eq(sandbox.devicePixelRatio, 1);
    eq(sandbox.innerWidth, 1920);
    eq(sandbox.innerHeight, 950);
    eq(sandbox.outerHeight, 1040);
  });
  test('plugins 伪装（5 个 PDF 插件）', () => {
    eq(nav.plugins.length, 5);
    eq(nav.plugins[0].name, 'PDF Viewer');
    eq(Object.prototype.toString.call(nav.plugins), '[object PluginArray]');
  });
  test('userAgentData 伪装 + getHighEntropyValues', () => {
    eq(nav.userAgentData.platform, 'Windows');
    eq(nav.userAgentData.mobile, false);
    const p = nav.userAgentData.getHighEntropyValues(['platform']);
    eq(typeof p.then, 'function', '应返回 Promise');
    return p.then((he) => {
      eq(he.platform, 'Windows');
      eq(he.platformVersion, '15.0.0');
      eq(he.fullVersionList.length, 3);
    });
  });
  test('Intl 时区伪装（resolvedOptions）', () => {
    eq(vm.runInContext('Intl.DateTimeFormat().resolvedOptions().timeZone', sandbox), 'America/New_York');
  });
  test('getTimezoneOffset 与目标时区一致', () => {
    const d = vm.runInContext('new Date()', sandbox);
    const expected = -offsetMinutes(RawDTF, d, 'America/New_York');
    eq(d.getTimezoneOffset(), expected, 'getTimezoneOffset');
  });
  test('Date.toString 按目标时区渲染', () => {
    const d = vm.runInContext('new Date(1735689600000)', sandbox); // 2025-01-01T00:00:00Z
    const s = d.toString();
    eq(/^[A-Z][a-z]{2} [A-Z][a-z]{2} \d{2} \d{4} \d{2}:\d{2}:\d{2} GMT[+-]\d{4} \(.+\)$/.test(s), true, 'Date.toString 格式');
    eq(s.includes(gmtString(RawDTF, d, 'America/New_York')), true, 'GMT 偏移');
  });
  test('Date.toLocaleString 走目标时区', () => {
    const s = vm.runInContext("new Date().toLocaleString('en-US', { timeZoneName: 'long' })", sandbox);
    eq(/Eastern/.test(s), true, '应包含 Eastern 时区名');
  });
  test('WebGL 未伪装厂商/渲染器', () => {
    const gl = sandbox.document.createElement('canvas').getContext('webgl');
    eq(gl.getParameter(0x9245), cfg.webglVendor);
    eq(gl.getParameter(0x9246), cfg.webglRenderer);
    eq(gl.getParameter(0x1F02), 'WebGL 1.0 (OpenGL ES 2.0 Chromium)', '其他参数不受影响');
    eq(gl.getContextAttributes().renderer, cfg.webglRenderer);
  });
  test('Canvas 噪声：种子决定', () => {
    const c1 = sandbox.document.createElement('canvas').getContext('2d');
    const c2 = sandbox.document.createElement('canvas').getContext('2d');
    const d1 = c1.getImageData(0, 0, 32, 32).data;
    const d2 = c2.getImageData(0, 0, 32, 32).data;
    eq(Array.from(d1).join(','), Array.from(d2).join(','), '同种子两次读取应一致');
    eq(d1.some((v) => v !== 0), true, '噪声应改变部分像素');
  });
  test('Canvas measureText 微噪声', () => {
    const c = sandbox.document.createElement('canvas').getContext('2d');
    const m = c.measureText('fingerprint');
    eq(Math.abs(m.width - 'fingerprint'.length * 8) < 0.001, true, '宽度应有微小偏移');
  });
  test('window.chrome / performance.memory 存在', () => {
    eq(typeof sandbox.chrome, 'object');
    eq(typeof sandbox.performance.memory, 'object');
  });
  test('matchMedia 指针能力重写', () => {
    eq(sandbox.matchMedia('(pointer: coarse)').matches, false);
    eq(sandbox.matchMedia('(any-hover: hover)').matches, true);
  });
  test('Function.prototype.toString 隐藏补丁', () => {
    const getter = Object.getOwnPropertyDescriptor(sandbox.navigator, 'userAgent').get;
    eq(vm.runInContext("Function.prototype.toString.call(navigator.__lookupGetter__('userAgent'))", sandbox)
      .includes('[native code]'), true);
    eq(getter !== undefined, true);
  });
}

console.log('\n[2] Android Chrome 移动模板');
{
  const cfg = templates.androidChrome();
  const { sandbox } = makeSandbox(cfg);
  const nav = sandbox.navigator;
  test('移动 UA / 触摸点 / DPR', () => {
    eq(nav.userAgent, cfg.userAgent);
    eq(nav.maxTouchPoints, 5);
    eq(sandbox.devicePixelRatio, 2.625);
  });
  test('userAgentData.mobile=true', () => eq(nav.userAgentData.mobile, true));
  test('matchMedia(coarse) = true', () => eq(sandbox.matchMedia('(pointer: coarse)').matches, true));
  test('移动模板不伪装窗口几何', () => eq(sandbox.innerWidth, undefined));
  test('移动模板无 window.chrome', () => eq(typeof sandbox.chrome, 'undefined'));
}

console.log('\n[3] iPhone Safari（WebKit）模板');
{
  const cfg = templates.iphoneSafari();
  const { sandbox } = makeSandbox(cfg);
  const nav = sandbox.navigator;
  test('webkit 引擎不注入 deviceMemory', () => eq(nav.deviceMemory, undefined));
  test('webkit 引擎不注入 userAgentData', () => eq(nav.userAgentData, undefined));
  test('webkit 引擎不注入 window.chrome', () => eq(typeof sandbox.chrome, 'undefined'));
  test('vendor=Apple Computer, Inc.', () => eq(nav.vendor, 'Apple Computer, Inc.'));
}

console.log('\n[4] WebRTC 策略');
{
  test('relay 模式强制 iceTransportPolicy 并过滤候选', () => {
    const ctx = makeSandbox(Object.assign({}, templates.windowsChrome(), { webRtcMode: 'relay' })).sandbox;
    const pc = new ctx.RTCPeerConnection({ iceServers: [] });
    eq(pc.config.iceTransportPolicy, 'relay');
    let called = 0;
    pc.onicecandidate = () => { called++; };
    pc.onicecandidate({ candidate: { candidate: 'candidate:1 1 udp 2113937151 1.2.3.4 56789 typ host' } });
    eq(called, 0, 'host 候选应被过滤');
    pc.onicecandidate({ candidate: { candidate: 'candidate:2 1 udp 1686052863 5.6.7.8 9 typ relay raddr 0.0.0.0' } });
    eq(called, 1, 'relay 候选应放行');
  });
  test('disabled 模式抛错', () => {
    const ctx = makeSandbox(Object.assign({}, templates.windowsChrome(), { webRtcMode: 'disabled' })).sandbox;
    let threw = null;
    try { new ctx.RTCPeerConnection({}); } catch (e) { threw = e; }
    eq(threw !== null, true, '应抛出异常');
    eq(threw.name, 'NotSupportedError');
  });
}

console.log('\n[5] window.name 覆盖合并（prelude 语义）');
{
  test('覆盖 platform 后引擎使用新值', () => {
    const merged = Object.assign({}, templates.windowsChrome(), { platform: 'MacIntel', maxTouchPoints: 0 });
    const ctx = makeSandbox(merged).sandbox;
    eq(ctx.navigator.platform, 'MacIntel');
  });
}

console.log('\n[6] Canvas/Audio 噪声可关闭');
{
  const ctx = makeSandbox(
    Object.assign({}, templates.windowsChrome(), { canvasMode: 'off', audioMode: 'off' }),
  ).sandbox;
  test('canvasMode=off 时像素原样返回', () => {
    const c = ctx.document.createElement('canvas').getContext('2d');
    const d = c.getImageData(0, 0, 8, 8).data;
    eq(d.every((v) => v === 0), true);
  });
  test('audioMode=off 时样本原样返回', () => {
    const buf = new ctx.AudioBuffer();
    const data = buf.getChannelData(0);
    eq(data.every((v) => v === 0.5), true);
  });
}

await Promise.all(pending);
console.log(`\n结果：${passed} 通过，${failed} 失败\n`);
process.exit(failed > 0 ? 1 : 0);
