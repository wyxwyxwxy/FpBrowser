#!/usr/bin/env node
/**
 * 生成自包含的桌面预览工具 tools/fp-preview.standalone.html
 * （把指纹模板与注入引擎内联进单个 HTML，双击即可在桌面 Chrome 中验证引擎效果）
 *
 * 运行：node tools/build-preview.mjs
 */
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const engine = readFileSync(join(here, '../app/src/main/res/raw/fp_inject.js'), 'utf8');
const templates = readFileSync(join(here, 'fingerprint-templates.js'), 'utf8');

const html = `<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<title>FpBrowser 指纹引擎预览（桌面验证工具）</title>
<style>
  :root { color-scheme: dark; }
  * { box-sizing: border-box; }
  body { margin: 0; padding: 20px; background: #0F1115; color: #E6E8ED;
         font: 13px/1.6 -apple-system, "PingFang SC", "Microsoft YaHei", sans-serif; }
  h1 { font-size: 17px; margin: 0 0 4px; }
  .tip { color: #8A92A3; font-size: 12px; margin-bottom: 14px; }
  .card { background: #161A22; border: 1px solid #232936; border-radius: 12px; padding: 14px; margin-bottom: 12px; }
  select, textarea, button { font: inherit; }
  select, textarea { background: #0F1115; color: #E6E8ED; border: 1px solid #2A3140; border-radius: 8px; padding: 6px 8px; }
  textarea { width: 100%; height: 160px; font-family: ui-monospace, Menlo, monospace; font-size: 11.5px; }
  button { background: #5B8DEF; color: #fff; border: none; border-radius: 8px; padding: 7px 14px; cursor: pointer; }
  button.ghost { background: #232936; color: #B4BAC6; }
  button:disabled { opacity: .5; cursor: default; }
  table { width: 100%; border-collapse: collapse; margin-top: 6px; }
  td, th { padding: 5px 8px; border-bottom: 1px solid #1F2430; text-align: left; vertical-align: top; }
  th { color: #8A92A3; font-weight: 500; font-size: 12px; }
  td.v { font-family: ui-monospace, Menlo, monospace; font-size: 11.5px; word-break: break-all; max-width: 300px; }
  .changed { color: #3FB68B; }
  .same { color: #8A92A3; }
  .row { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
</style>
</head>
<body>
<h1>FpBrowser 指纹引擎预览</h1>
<div class="tip">
  在桌面 Chrome 中直接运行 app/src/main/res/raw/fp_inject.js：选择模板后点击「注入」，
  本页 navigator / screen / 时区 / WebGL / Canvas 等值会被真实改写，可与「注入前」对照。
  刷新页面恢复原始状态。安卓真机上的效果以 App 内「指纹与 IP 检测」页为准。
</div>

<div class="card">
  <div class="row">
    <label>模板：
      <select id="tpl">
        <option value="windowsChrome">Windows · Chrome</option>
        <option value="macChrome">macOS · Chrome</option>
        <option value="macSafari">macOS · Safari</option>
        <option value="linuxChrome">Linux · Chrome</option>
        <option value="androidChrome">Android · Chrome</option>
        <option value="iphoneSafari">iPhone · Safari</option>
      </select>
    </label>
    <button id="inject">注入并检测</button>
    <button class="ghost" onclick="location.reload()">恢复原始</button>
  </div>
  <div class="row" style="margin-top:10px">
    <button class="ghost" id="toggle-json">编辑配置 JSON</button>
  </div>
  <div id="json-box" style="display:none; margin-top:10px">
    <textarea id="json" spellcheck="false"></textarea>
    <div class="row" style="margin-top:8px">
      <button id="apply-json">应用 JSON 并注入</button>
    </div>
  </div>
</div>

<div class="card">
  <table>
    <thead><tr><th style="width:26%">检测项</th><th style="width:37%">注入前</th><th style="width:37%">注入后</th></tr></thead>
    <tbody id="result"></tbody>
  </table>
</div>

<script>${templates}</script>
<script>window.FP_ENGINE_SRC = ${JSON.stringify(engine)};</script>
<script>
(function () {
  var before = null;

  function canvasHash() {
    try {
      var c = document.createElement('canvas');
      c.width = 220; c.height = 80;
      var ctx = c.getContext('2d');
      ctx.textBaseline = 'top';
      ctx.font = '14px "Arial"';
      ctx.fillStyle = '#f60'; ctx.fillRect(10, 10, 80, 40);
      ctx.fillStyle = '#069'; ctx.fillText('FpBrowser preview \\u{1F600}', 5, 5);
      ctx.globalCompositeOperation = 'multiply';
      ctx.beginPath(); ctx.arc(100, 30, 30, 0, Math.PI * 2);
      ctx.fillStyle = 'rgba(255,0,255,.6)'; ctx.fill();
      return c.toDataURL().slice(-32);
    } catch (e) { return 'N/A'; }
  }
  function webglInfo() {
    try {
      var c = document.createElement('canvas');
      var gl = c.getContext('webgl');
      if (!gl) return 'N/A';
      var ext = gl.getExtension('WEBGL_debug_renderer_info');
      return (ext ? gl.getParameter(ext.UNMASKED_VENDOR_WEBGL) : gl.getParameter(gl.VENDOR)) + ' | ' +
             (ext ? gl.getParameter(ext.UNMASKED_RENDERER_WEBGL) : gl.getParameter(gl.RENDERER));
    } catch (e) { return 'N/A'; }
  }
  function snap() {
    var off = new Date().getTimezoneOffset();
    var sign = off <= 0 ? '+' : '-';
    var a = Math.abs(off);
    return {
      'navigator.userAgent': navigator.userAgent,
      'navigator.platform': navigator.platform,
      'navigator.vendor': navigator.vendor,
      'navigator.languages': (navigator.languages || []).join(','),
      'navigator.hardwareConcurrency': String(navigator.hardwareConcurrency),
      'navigator.deviceMemory': navigator.deviceMemory === undefined ? '(未定义)' : navigator.deviceMemory + ' GB',
      'navigator.maxTouchPoints': String(navigator.maxTouchPoints),
      'navigator.webdriver': String(navigator.webdriver),
      'navigator.plugins': String(navigator.plugins ? navigator.plugins.length : 'N/A'),
      'userAgentData': navigator.userAgentData ? navigator.userAgentData.platform + ' / mobile=' + navigator.userAgentData.mobile : '(未定义)',
      'Intl 时区': Intl.DateTimeFormat().resolvedOptions().timeZone,
      'getTimezoneOffset': 'UTC' + sign + Math.floor(a / 60) + ':' + (a % 60 < 10 ? '0' : '') + (a % 60),
      'new Date().toString()': new Date().toString(),
      'screen': screen.width + '×' + screen.height + ' @' + devicePixelRatio + 'x depth=' + screen.colorDepth,
      'window 几何': 'inner ' + innerWidth + '×' + innerHeight,
      'matchMedia(pointer:coarse)': String(matchMedia('(pointer: coarse)').matches),
      'WebGL': webglInfo(),
      'window.chrome': typeof chrome !== 'undefined' ? '存在' : '不存在',
      'Canvas 指纹(尾)': canvasHash(),
      'Function.toString': Function.prototype.toString.call(navigator.__lookupGetter__('userAgent') || function(){})
    };
  }

  function render(b, a) {
    var tb = document.getElementById('result');
    tb.innerHTML = '';
    Object.keys(b).forEach(function (k) {
      var changed = b[k] !== a[k];
      var tr = document.createElement('tr');
      tr.innerHTML = '<td>' + k + '</td><td class="v same">' + esc(b[k]) + '</td>' +
        '<td class="v ' + (changed ? 'changed' : 'same') + '">' + esc(a[k]) + '</td>';
      tb.appendChild(tr);
    });
  }
  function esc(s) {
    return String(s).replace(/[&<>"]/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c];
    });
  }

  function currentConfig() {
    var tpl = document.getElementById('tpl').value;
    return window.FP_TEMPLATES[tpl]();
  }

  document.getElementById('inject').addEventListener('click', function () {
    if (!before) before = snap();
    var cfg = currentConfig();
    window.__FP_CONFIG__ = cfg;
    (0, eval)(window.FP_ENGINE_SRC);
    render(before, snap());
  });

  document.getElementById('toggle-json').addEventListener('click', function () {
    var box = document.getElementById('json-box');
    var show = box.style.display === 'none';
    box.style.display = show ? 'block' : 'none';
    if (show) document.getElementById('json').value = JSON.stringify(currentConfig(), null, 2);
  });

  document.getElementById('apply-json').addEventListener('click', function () {
    if (!before) before = snap();
    try {
      var cfg = JSON.parse(document.getElementById('json').value);
      window.__FP_CONFIG__ = cfg;
      (0, eval)(window.FP_ENGINE_SRC);
      render(before, snap());
    } catch (e) {
      alert('JSON 解析失败：' + e.message);
    }
  });
})();
</script>
</body>
</html>
`;

writeFileSync(join(here, 'fp-preview.standalone.html'), html);
console.log('已生成 tools/fp-preview.standalone.html（' + Math.round(html.length / 1024) + ' KB）');
