# FpBrowser — 安卓端指纹浏览器

对标 Multilogin / AdsPower / 比特浏览器 的 **Android 原生指纹浏览器**：在一部手机上创建多个相互隔离的浏览器环境，每个环境拥有独立的 Cookie / LocalStorage / IndexedDB / 缓存数据、独立的设备指纹伪装、独立的出口代理 IP。首次启动即预置 6 套环境，开箱即用。

- **多环境隔离**：每个环境运行在独立进程中，WebView 数据目录完全隔离
- **指纹伪装**：UA、屏幕、时区、WebGL、Canvas、Audio、WebRTC、navigator 全套伪装
- **独立 IP**：每个环境可单独配置 HTTP / SOCKS5 代理，内置连通性测试与出口 IP 查看
- **预置就绪**：6 套主流平台模板 + 6 个种子环境，装完即可用

---

## 一、功能特性

| 模块 | 能力 |
| --- | --- |
| 环境管理 | 环境卡片列表、搜索、新建、编辑、复制、删除、JSON 导入 / 导出 |
| 预置模板 | Windows Chrome、macOS Chrome、macOS Safari、Linux Chrome、Android Chrome、iPhone Safari 六套成套自洽模板 |
| 指纹编辑 | 30+ 字段可视化编辑、时区选择器、一键随机生成（约束一致：屏幕 / GPU / CPU / 时区成套匹配） |
| 代理 | 每环境独立 HTTP / SOCKS5 代理，支持账号密码认证，连通性测试直接显示出口 IP |
| 浏览器 | 多标签、前进后退刷新、桌面 / 移动 UA 一键切换（`window.name` 覆盖，免重建）、下载管理、Cookie 导入导出 |
| 隔离 | 8 个固定槽位进程，Cookie / Storage / IndexedDB / Cache 按环境完全隔离 |
| 检测 | 内置指纹检测页：出口 IP、WebRTC 泄漏、注入后指纹值、一致性自检报告 |

---

## 二、与 PC 端指纹浏览器功能对照

| PC 端能力（Multilogin / AdsPower 等） | 本工程实现 |
| --- | --- |
| 多浏览器配置文件管理 | ✅ 环境（Profile）卡片管理，Room 持久化 |
| 每个配置文件独立 Cookie / 存储 | ✅ 每环境独立进程 + `setDataDirectorySuffix` 数据目录隔离 |
| 指纹伪装（Canvas / WebGL / Audio / Font） | ✅ Canvas / Audio 种子噪声（同种子跨会话稳定）；WebGL 厂商 / 渲染器伪装；Font 通过 UA + 平台成套匹配 |
| 指纹随机生成 | ✅ `FingerprintGenerator` 约束一致随机（平台 ↔ 屏幕 ↔ GPU ↔ 时区成套） |
| 每配置文件独立代理 | ✅ `ProxyController.setProxyOverride` 进程级代理，HTTP / SOCKS5，407 认证自动应答 |
| 代理连通性测试 / 出口 IP 检测 | ✅ 编辑环境页内置测试，直接返回出口 IP |
| UA 切换 / 桌面模式 | ✅ 工具栏一键切换（预注入覆盖值，刷新即生效，无需重建 WebView） |
| Cookie 导入导出 | ✅ Netscape 格式导入 / 导出对话框 |
| 配置文件导入导出 | ✅ 全环境 JSON 导出 / 导入（SAF 选目录） |
| 团队 / 云端同步 | ❌ 未实现（本地单机存储，JSON 手动迁移） |
| 自动化 API（Selenium / Puppeteer 对接） | ❌ 未实现 |

---

## 三、快速开始

### 3.1 环境要求

| 项 | 要求 |
| --- | --- |
| JDK | 17 或更高 |
| Android Studio | Ladybug（2024.2）或更高；或命令行 Gradle 8.9+ |
| Android SDK | compileSdk 35（Android 15）、Build-Tools 35 |
| 最低设备 | Android 8.0（API 26） |
| 完整隔离要求 | Android 9（API 28）及以上（数据目录隔离依赖 `setDataDirectorySuffix`） |

### 3.2 命令行构建

```bash
# 进入工程（已含 Gradle Wrapper，无需单独安装 Gradle）
cd FpBrowser

# 首次构建会自动下载 Gradle 8.9 分发（约 130MB）
./gradlew assembleDebug

# 产物位置
# app/build/outputs/apk/debug/app-debug.apk
```

> 国内网络下载 Gradle 分发慢时，可将 `gradle/wrapper/gradle-wrapper.properties` 中的
> `distributionUrl` 改为镜像：`https://mirrors.cloud.tencent.com/gradle/gradle-8.9-bin.zip`。
> 依赖本身走 `google()` + `mavenCentral()`，如仍缓慢可在 `~/.gradle/init.gradle` 中追加国内 Maven 镜像仓库。

### 3.3 Android Studio 构建

1. `File → Open` 选择 `FpBrowser` 目录
2. 等待 Gradle Sync 完成（首次会自动下载依赖）
3. 连接设备或模拟器，点击 Run
4. 或 `Build → Build APK(s)` 产出 `app-debug.apk`

### 3.4 安装到手机

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

首次启动自动完成数据库初始化与 6 个预置环境写入，进入环境列表即可使用。

---

## 四、使用指南

### 4.1 预置环境（开箱即用）

首次启动自动写入 6 个种子环境，代理均留空待填：

| 环境 | 平台伪装 | 地区 / 时区 |
| --- | --- | --- |
| 美区-Windows-Chrome | Windows 11 + Chrome 131 | America/New_York |
| 日区-Android-Chrome | Android 14 + Chrome 131 | Asia/Tokyo |
| 英区-macOS-Safari | macOS 14 + Safari 17 | Europe/London |
| 德区-Windows-Chrome | Windows 11 + Chrome 131 | Europe/Berlin |
| 美区-iPhone-Safari | iOS 17 + Safari | America/Los_Angeles |
| 欧区-Linux-Chrome | Linux x86_64 + Chrome 131 | Europe/Amsterdam |

### 4.2 更改 IP（每环境独立代理）

1. 环境卡片点击 **编辑** → **代理** 区块
2. 开启代理开关，选择类型（HTTP / SOCKS5），填写主机、端口、账号密码
3. 点击 **测试连通性**：通过代理访问检测接口，直接显示识别到的出口 IP
4. 保存后，该环境的所有网页请求均经此代理出口；**不勾选**「直连兜底」时代理失效绝不回落直连，防止真实 IP 泄漏

> 建议配合住宅代理使用。同一环境长期使用同一出口 IP 更符合真实用户特征。

### 4.3 自定义指纹

编辑环境 → **指纹** 区块：

- 点选模板芯片快速成套切换（6 模板字段成套自洽）
- **随机生成**：按当前平台约束生成全新指纹（屏幕尺寸 / GPU / 时区 / CPU 核数成套匹配，不会出现「macOS + 1080P + Windows GPU」这类穿帮组合）
- 逐项微调：UA、平台、语言、时区、屏幕分辨率、色深、DPR、硬件并发、内存、触摸点、WebGL 厂商 / 渲染器、Canvas / Audio 噪声种子与开关、WebRTC 策略（relay / disabled / 默认）、Do Not Track、插件列表等
- 支持 JSON 导入 / 导出指纹配置，便于批量分发

### 4.4 指纹检测（自证隔离效果）

浏览器内打开菜单 → **指纹检测页**（或访问任意网站）：

- **出口 IP**：经 ipify / ip-api 识别当前代理出口
- **WebRTC 泄漏**：枚举 ICE 候选，验证未暴露本机地址
- **注入后指纹**：UA、平台、屏幕、时区、WebGL、Canvas 指纹等注入结果一览
- **一致性自检**：UA ↔ 触摸点 ↔ platform ↔ userAgentData ↔ 时区是否互相印证，穿帮项红字提示
- 一键 **复制报告**，便于贴给代理商或团队核对

### 4.5 多环境并行与槽位机制

- 同时最多 **8 个环境**并行运行（对应 `:web1` ~ `:web8` 八个进程槽位）
- 第 9 个环境启动时，自动复用同环境已有窗口；无空闲槽位则按策略回收后启动，旧页面状态以槽位文件为准
- 每个进程的 WebView 数据目录为 `fp_profile_<环境ID>`，卸载应用或清除数据前环境间数据互不可见

---

## 五、架构与隔离原理

```
┌────────────────────────────────────────────────────────────┐
│  主进程 (:com.fpbrowser.app)                                │
│  ┌──────────────┐  ┌───────────────┐  ┌─────────────────┐  │
│  │ MainActivity │  │ProfileEditAct │  │ Room 数据库     │  │
│  │ 环境卡片列表  │  │ 指纹/代理编辑  │  │ profiles 表     │  │
│  └──────┬───────┘  └───────────────┘  └─────────────────┘  │
│         │ SlotManager.launch(profileId)                    │
│         ▼                                                   │
│  filesDir/slots/slot_N.json  ← 槽位 → 环境绑定              │
└─────────┬──────────────────────────────────────────────────┘
          │ 启动对应 Activity（携带 slot）
┌─────────▼──────────────────────────────────────────────────┐
│  浏览器进程 :web1 … :web8（每进程同时仅承载一个环境）          │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ FpApp.onCreate（按进程名识别 :webN）                  │   │
│  │  1. 读 slot_N.json → profileId                      │   │
│  │  2. applyDataDirectorySuffix("fp_profile_<id>")      │   │
│  │     → Cookie / LocalStorage / IndexedDB / Cache 隔离 │   │
│  │  3. 读取指纹配置 + 代理配置                           │   │
│  ├─────────────────────────────────────────────────────┤   │
│  │ BrowserActivity                                        │   │
│  │  · addDocumentStartJavaScript(指纹引擎)  ← 文档起始注入 │   │
│  │  · ProxyController.setProxyOverride(代理)  ← 进程级    │   │
│  │  · WebViewAssetLoader → 本地检测页 https 加载          │   │
│  └─────────────────────────────────────────────────────┘   │
└────────────────────────────────────────────────────────────┘
```

**隔离三要素**

1. **进程隔离**：8 个固定槽位 Activity（`android:process=":webN"`），一个环境一个进程，天然的内存与组件隔离
2. **数据目录隔离**：`androidx.webkit` 的 `ProcessGlobalConfig.setDataDirectorySuffix`（API 28+，公开 API；不可用时反射 `WebView.setDataDirectorySuffix` 隐藏 API，仍不可用时降级为共享数据并弹出警告横幅）
3. **网络隔离**：`ProxyController` 按进程覆盖代理；代理认证（HTTP 407）由 `WebViewClient.onReceivedHttpAuthRequest` 自动应答

**指纹注入时机**：`addDocumentStartJavaScript` 在文档脚本执行前注入引擎（`res/raw/fp_inject.js`），先于站点脚本读取 `navigator` / `screen` / `canvas` 等，杜绝「先读后改」时序漏洞。

**桌面 / 移动切换**：桌面 UA 与覆盖指纹通过 `window.name` 中的 `__FP_OVERRIDE__` 传递，prelude 在每次文档起始时合并覆盖值，切换后刷新即生效，无需重建 WebView 或重设代理。

---

## 六、指纹伪装能力清单

| 类别 | 伪装项 |
| --- | --- |
| navigator | userAgent、appVersion、platform、vendor、product、language、languages、hardwareConcurrency、deviceMemory、maxTouchPoints、plugins / mimeTypes、doNotTrack、userAgentData（含 getHighEntropyValues） |
| screen | width、height、availWidth/Height、availLeft/Top、colorDepth、pixelDepth |
| 窗口 | innerWidth/Height、outerWidth/Height、screenX/Y、devicePixelRatio、matchMedia（pointer / hover / 分辨率媒体查询） |
| 时区 | Intl.DateTimeFormat resolvedOptions.timeZone、Date 全系方法、getTimezoneOffset（三者互相自洽） |
| WebGL | UNMASKED_VENDOR_WEBGL / UNMASKED_RENDERER_WEBGL 伪装（0x9245 / 0x9246） |
| Canvas | getImageData / toDataURL 种子噪声（mulberry32 PRNG，同种子跨会话结果一致，非纯随机） |
| Canvas 文本 | measureText 微幅噪声 |
| Audio | AudioBuffer 通道样本噪声 |
| WebRTC | relay（强制走 TURN，过滤本机候选）/ disabled（直接抛错阻断） |
| 其他 | window.chrome / external、performance.memory、`Function.prototype.toString` 对补丁函数返回 `[native code]`（WeakSet 标记，防「改过的函数一眼假」检测） |

**设计原则**：所有伪装值成套自洽（UA 声明的平台 ↔ navigator.platform ↔ 触摸点 ↔ UA-CH ↔ 时区 ↔ 屏幕分辨率），避免单点伪装导致交叉验证穿帮；Canvas / Audio 采用种子伪随机而非纯随机，保证同一环境多次访问指纹稳定。

---

## 七、检测与开发验证工具

```bash
# 引擎回归测试：Node vm 隔离桩中运行真实 fp_inject.js
# 覆盖 6 模板 / WebRTC / 覆盖合并 / 噪声开关，共 34 项断言
node tools/test-inject.mjs

# 生成桌面 Chrome 可视化验证页（before/after 对照）
node tools/build-preview.mjs
# 产物：tools/fp-preview.standalone.html，用桌面浏览器打开即可直观核对引擎效果
```

`tools/fingerprint-templates.js` 与 `data/Presets.kt` 为同一套模板的 UMD / Kotlin 双实现，修改时需同步两侧。

---

## 八、目录结构

```
FpBrowser/
├── gradlew / gradlew.bat / gradle/wrapper/   # Gradle 8.9 Wrapper
├── settings.gradle / build.gradle            # 插件版本声明（AGP 8.6.0 / Kotlin 2.0.20 / KSP）
├── app/
│   ├── build.gradle                           # compileSdk 35 / minSdk 26 / targetSdk 35
│   └── src/main/
│       ├── AndroidManifest.xml                # 主进程 + 8 个 :webN 进程声明
│       ├── assets/fp-check.html               # 指纹检测页（https 经 AssetLoader 加载）
│       ├── res/raw/fp_inject.js               # 指纹注入引擎（约 700 行）
│       └── java/com/fpbrowser/app/
│           ├── FpApp.kt                       # 进程识别 / 数据目录后缀应用
│           ├── data/                          # Fingerprint / Proxy / Room / 模板 / 生成器
│           ├── fp/                            # FingerprintScript / ProxyManager / SlotManager
│           ├── ui/                            # MainActivity / ProfileEditActivity / 主题
│           └── web/                           # BrowserActivity / 8 个槽位 Activity
└── tools/                                     # 引擎测试与可视化验证工具
```

---

## 九、平台限制与注意事项

1. **完整数据隔离要求 Android 9+（API 28）**。API 26 / 27 设备 `setDataDirectorySuffix` 不可用，将降级为多进程（内存 / 组件仍隔离）但共享 WebView 数据，应用会显示降级警告横幅——不建议在此类设备上存放敏感环境。
2. **同屏最多 8 个并行环境**（固定槽位）。需要更多并行窗口时可在 `BrowserSlots.kt` 中按现有模式扩展 `:web9` 及对应 Manifest 声明。
3. **代理为进程级**：同一环境的所有标签共享一个出口；不支持按标签分流代理。
4. **不内置任何代理服务**：需自备 HTTP / SOCKS5 代理。
5. **WebView 内核**：使用系统 WebView，指纹伪装作用于 JS 层；TCP / TLS 指纹（如 JA3）不在本工程范围内，如需该级别伪装需替换为自编译 Chromium 内核（如 Crosswalk / GeckoView 方案）。
6. **Root / 刷机检测**：部分高危站点会检测 Magisk 等 root 特征，本工程不做 root 隐藏。
7. 清除应用数据 / 卸载将删除全部环境及其本地数据，请先 **导出 JSON 备份**。

---

## 十、合规声明

本项目为**浏览器隐私增强工具**：用于在同一设备上隔离多个浏览会话（如多账号运营、隐私测试、开发调试）。请遵守目标网站服务条款与当地法律法规，不得用于欺诈、刷量、绕过风控等违规用途。使用者需自行承担合规责任。

---

## 十一、技术栈

| 层 | 选型 |
| --- | --- |
| 语言 / 构建 | Kotlin 2.0.20 · AGP 8.6.0 · Gradle 8.9 · JDK 17 |
| UI | Jetpack Compose（BOM 2024.09.03）· Material3 |
| 存储 | Room 2.6.1（KSP）· Gson |
| WebView | androidx.webkit 1.12.1（document-start 脚本 / 进程代理 / 数据目录后缀） |
| 最低 / 目标 | minSdk 26（Android 8.0）· targetSdk 35（Android 15） |
