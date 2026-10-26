package com.fpbrowser.app.web

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.HttpAuthHandler
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.JsResult
import android.webkit.JsPromptResult
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.fpbrowser.app.FpApp
import com.fpbrowser.app.data.ProfileEntity
import com.fpbrowser.app.data.ProfileRepository
import com.fpbrowser.app.fp.FingerprintScript
import com.fpbrowser.app.fp.ProxyManager
import com.fpbrowser.app.ui.theme.FpBrowserTheme
import com.fpbrowser.app.util.CookieIO
import org.json.JSONObject

/**
 * 浏览器宿主（抽象基类，不直接启动；由 BrowserSlot1~8Activity 在各独立进程中启动）。
 *
 * 职责：
 * - 按环境指纹配置创建 WebView（UA / document-start 指纹注入 / 代理）；
 * - 多标签管理（每个标签一个 WebView，共享当前环境的数据目录）；
 * - Cookie Netscape 导入导出、下载、文件选择、桌面模式等浏览器功能。
 */
abstract class BrowserActivity : ComponentActivity() {

    abstract val slotIndex: Int

    private lateinit var profile: ProfileEntity
    private lateinit var repo: ProfileRepository
    private var legacyInjection = false
    private val scripts: List<String> by lazy {
        FingerprintScript.buildScripts(this, profile.fingerprint)
    }

    private val container: FrameLayout by lazy { FrameLayout(this) }
    private val tabs = ArrayList<Tab>()
    private var current = -1

    private var urlText by mutableStateOf("")
    private var pageTitle by mutableStateOf("")
    private var pageProgress by mutableStateOf(0)
    private var canGoBack by mutableStateOf(false)
    private var canGoForward by mutableStateOf(false)
    private var desktopMode by mutableStateOf(false)
    private var menuExpanded by mutableStateOf(false)
    private var errorMessage by mutableStateOf<String?>(null)
    private var tabSummaries by mutableStateOf<List<TabSummary>>(emptyList())
    private var showDegradedBanner by mutableStateOf(false)
    private var cookieDialogText by mutableStateOf<String?>(null)
    private var cookieImportVisible by mutableStateOf(false)

    private lateinit var assetLoader: WebViewAssetLoader

    private var pendingFileCallback: ValueCallback<Array<Uri>>? = null
    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val cb = pendingFileCallback ?: return@registerForActivityResult
            pendingFileCallback = null
            val uris = if (result.resultCode == RESULT_OK) {
                WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            } else null
            cb.onReceiveValue(uris)
        }

    private class Tab(val webView: WebView) {
        var url: String = ""
        var title: String = ""
        var desktop: Boolean = false
    }

    private data class TabSummary(val index: Int, val title: String, val selected: Boolean)

    companion object {
        const val EXTRA_PROFILE_ID = "profile_id"
        const val EXTRA_SLOT = "slot"
        const val HOME_URL = "https://appassets.androidplatform.net/assets/fp-check.html"
    }

    /* ------------------------------------------------------------------ 生命周期 */

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val profileId = intent.getStringExtra(EXTRA_PROFILE_ID)
            ?: com.fpbrowser.app.fp.SlotManager.readSlotProfile(this, slotIndex)
        if (profileId.isNullOrBlank()) {
            finish()
            return
        }
        repo = ProfileRepository.get(this)
        val loaded = repo.getSync(profileId)
        if (loaded == null) {
            Toast.makeText(this, "环境不存在或已被删除", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        profile = loaded
        showDegradedBanner = FpApp.degradedIsolation

        // 代理必须在创建任何 WebView 之前于本进程应用
        ProxyManager.apply(this, profile.proxy)

        assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val wv = tabs.getOrNull(current)?.webView
                if (wv != null && wv.canGoBack()) {
                    wv.goBack()
                } else if (tabs.size > 1) {
                    closeTab(current)
                } else {
                    finish()
                }
            }
        })

        setContent {
            FpBrowserTheme {
                BrowserScreen()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val newProfileId = intent.getStringExtra(EXTRA_PROFILE_ID)
        if (newProfileId != null && newProfileId != profile.id) {
            // 极端竞态：同一槽位被分配到不同环境，重建以加载正确环境
            recreate()
        }
    }

    override fun onPause() {
        super.onPause()
        tabs.getOrNull(current)?.webView?.onPause()
    }

    override fun onResume() {
        super.onResume()
        tabs.getOrNull(current)?.webView?.onResume()
    }

    override fun onDestroy() {
        tabs.forEach { tab ->
            (tab.webView.parent as? ViewGroup)?.removeView(tab.webView)
            tab.webView.destroy()
        }
        tabs.clear()
        super.onDestroy()
    }

    /* ------------------------------------------------------------------ 标签管理 */

    private fun refreshTabs() {
        tabSummaries = tabs.mapIndexed { i, t ->
            TabSummary(i, t.title.ifBlank { "新标签页" }, i == current)
        }
    }

    private fun newTab(url: String = HOME_URL): Int {
        val wv = createWebView()
        val tab = Tab(wv)
        tab.desktop = desktopMode
        tabs.add(tab)
        attachTab(tabs.size - 1)
        wv.loadUrl(url)
        return tabs.size - 1
    }

    private fun attachTab(index: Int) {
        if (index !in tabs.indices) return
        current = index
        val tab = tabs[index]
        container.removeAllViews()
        container.addView(
            tab.webView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        urlText = tab.url
        pageTitle = tab.title
        desktopMode = tab.desktop
        canGoBack = tab.webView.canGoBack()
        canGoForward = tab.webView.canGoForward()
        errorMessage = null
        refreshTabs()
    }

    private fun closeTab(index: Int) {
        if (index !in tabs.indices) return
        val tab = tabs.removeAt(index)
        (tab.webView.parent as? ViewGroup)?.removeView(tab.webView)
        tab.webView.destroy()
        if (tabs.isEmpty()) {
            finish()
            return
        }
        attachTab(index.coerceIn(0, tabs.size - 1))
    }

    /* ------------------------------------------------------------------ WebView 工厂 */

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView {
        val wv = WebView(this)
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            @Suppress("DEPRECATION")
            databaseEnabled = true
            loadsImagesAutomatically = true
            useWideViewPort = true
            loadWithOverviewMode = true
            mediaPlaybackRequiresUserGesture = false
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            allowFileAccess = false
            allowContentAccess = true
            userAgentString = if (desktopMode) {
                FingerprintScript.desktopUaOf(profile.fingerprint)
            } else {
                profile.fingerprint.userAgent
            }
        }

        // 指纹注入：document-start（优先公开 API，降级 onPageStarted）
        legacyInjection = !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        if (!legacyInjection) {
            scripts.forEach { script ->
                WebViewCompat.addDocumentStartJavaScript(wv, script, setOf("*"))
            }
        }

        wv.webViewClient = FpWebViewClient()
        wv.webChromeClient = FpWebChromeClient()
        wv.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            startDownload(url, userAgent, contentDisposition, mimetype)
        }
        wv.addJavascriptInterface(FpJsBridge(), "AndroidHost")
        return wv
    }

    /* ------------------------------------------------------------------ 浏览器动作 */

    private fun go(url: String) {
        val wv = tabs.getOrNull(current)?.webView ?: return
        var target = url.trim()
        if (target.isEmpty()) return
        if (!target.startsWith("http://") && !target.startsWith("https://")) {
            target = if (target.contains(".") && !target.contains(" ")) {
                "https://$target"
            } else {
                "https://www.bing.com/search?q=${Uri.encode(target)}"
            }
        }
        errorMessage = null
        wv.loadUrl(target)
    }

    private fun toggleDesktop() {
        val tab = tabs.getOrNull(current) ?: return
        tab.desktop = !tab.desktop
        desktopMode = tab.desktop
        val override = if (tab.desktop) FingerprintScript.desktopOverrideJson(profile.fingerprint) else null
        tabs.forEach { t ->
            t.webView.settings.userAgentString = if (t.desktop) {
                FingerprintScript.desktopUaOf(profile.fingerprint)
            } else {
                profile.fingerprint.userAgent
            }
            t.webView.evaluateJavascript(FingerprintScript.windowNameOverrideScript(override), null)
        }
        tabs.getOrNull(current)?.webView?.reload()
    }

    private fun startDownload(url: String, userAgent: String, contentDisposition: String, mimetype: String) {
        try {
            val fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setMimeType(mimetype)
                addRequestHeader("User-Agent", userAgent)
                CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
                setTitle(fileName)
                setDescription("FpBrowser 下载")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, fileName)
            }
            (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            Toast.makeText(this, "已开始下载：$fileName", Toast.LENGTH_SHORT).show()
        } catch (t: Throwable) {
            Toast.makeText(this, "下载失败：${t.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyToClipboard(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("FpBrowser", text))
        Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show()
    }

    private fun currentUrl(): String = tabs.getOrNull(current)?.webView?.url ?: ""

    /* ------------------------------------------------------------------ Clients */

    private inner class FpWebViewClient : WebViewClient() {

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest,
        ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            if (url.startsWith("http://") || url.startsWith("https://")) return false
            return try {
                startActivity(Intent(Intent.ACTION_VIEW, request.url))
                true
            } catch (t: Throwable) {
                true
            }
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            val tab = tabs.firstOrNull { it.webView === view } ?: return
            tab.url = url ?: ""
            if (current in tabs.indices && tabs[current] === tab) {
                urlText = tab.url
                errorMessage = null
            }
            if (legacyInjection) {
                // 降级注入：时序晚于页面脚本，可能被检测，仅作兜底
                view.evaluateJavascript(scripts.joinToString("\n"), null)
            }
        }

        override fun onPageFinished(view: WebView, url: String?) {
            val tab = tabs.firstOrNull { it.webView === view } ?: return
            tab.url = url ?: ""
            if (current in tabs.indices && tabs[current] === tab) {
                urlText = tab.url
                canGoBack = view.canGoBack()
                canGoForward = view.canGoForward()
            }
        }

        override fun onReceivedHttpAuthRequest(
            view: WebView,
            handler: HttpAuthHandler,
            host: String?,
            realm: String?,
        ) {
            val p = profile.proxy
            if (p.isUsable && host != null && host == p.host && p.username.isNotBlank()) {
                handler.proceed(p.username, p.password)
            } else {
                handler.cancel()
            }
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: android.net.http.SslError) {
            AlertDialog.Builder(this@BrowserActivity)
                .setTitle("证书安全警告")
                .setMessage("当前站点安全证书存在问题（${error.primaryError}）。指纹环境建议谨慎继续，是否仍要访问？")
                .setPositiveButton("继续访问") { _, _ -> handler.proceed() }
                .setNegativeButton("取消") { _, _ -> handler.cancel() }
                .setOnCancelListener { handler.cancel() }
                .show()
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            if (request.isForMainFrame) {
                val tab = tabs.firstOrNull { it.webView === view }
                if (tab != null && current in tabs.indices && tabs[current] === tab) {
                    errorMessage = error.description?.toString() ?: "加载失败"
                }
            }
        }
    }

    private inner class FpWebChromeClient : WebChromeClient() {

        override fun onProgressChanged(view: WebView, newProgress: Int) {
            val tab = tabs.firstOrNull { it.webView === view } ?: return
            if (current in tabs.indices && tabs[current] === tab) {
                pageProgress = newProgress
            }
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            val tab = tabs.firstOrNull { it.webView === view } ?: return
            tab.title = title ?: ""
            if (current in tabs.indices && tabs[current] === tab) {
                pageTitle = tab.title
            }
            refreshTabs()
        }

        override fun onCreateWindow(
            view: WebView,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message?,
        ): Boolean {
            if (resultMsg == null) return false
            val newWebView = createWebView()
            val tab = Tab(newWebView)
            tab.desktop = desktopMode
            tabs.add(tab)
            attachTab(tabs.size - 1)
            (resultMsg.obj as? WebView.WebViewTransport)?.webView = newWebView
            resultMsg.sendToTarget()
            return true
        }

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams,
        ): Boolean {
            pendingFileCallback?.onReceiveValue(null)
            pendingFileCallback = filePathCallback
            return try {
                fileChooserLauncher.launch(fileChooserParams.createIntent())
                true
            } catch (t: Throwable) {
                pendingFileCallback = null
                false
            }
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            // 指纹环境默认拒绝摄像头 / 麦克风 / 定位等敏感权限
            request.deny()
        }

        override fun onJsAlert(
            view: WebView,
            url: String?,
            message: String?,
            result: JsResult,
        ): Boolean {
            AlertDialog.Builder(this@BrowserActivity)
                .setMessage(message ?: "")
                .setPositiveButton("确定") { _, _ -> result.confirm() }
                .setOnCancelListener { result.cancel() }
                .show()
            return true
        }

        override fun onJsConfirm(
            view: WebView,
            url: String?,
            message: String?,
            result: JsResult,
        ): Boolean {
            AlertDialog.Builder(this@BrowserActivity)
                .setMessage(message ?: "")
                .setPositiveButton("确定") { _, _ -> result.confirm() }
                .setNegativeButton("取消") { _, _ -> result.cancel() }
                .setOnCancelListener { result.cancel() }
                .show()
            return true
        }

        override fun onJsPrompt(
            view: WebView,
            url: String?,
            message: String?,
            defaultValue: String?,
            result: JsPromptResult,
        ): Boolean {
            val input = EditText(this@BrowserActivity).apply { setText(defaultValue ?: "") }
            val layout = LinearLayout(this@BrowserActivity).apply {
                orientation = LinearLayout.VERTICAL
                val pad = (16 * resources.displayMetrics.density).toInt()
                setPadding(pad, pad, pad, pad)
                addView(input)
            }
            AlertDialog.Builder(this@BrowserActivity)
                .setMessage(message ?: "")
                .setView(layout)
                .setPositiveButton("确定") { _, _ -> result.confirm(input.text.toString()) }
                .setNegativeButton("取消") { _, _ -> result.cancel() }
                .setOnCancelListener { result.cancel() }
                .show()
            return true
        }
    }

    /** 提供给检测页的极小桥（复制报告等） */
    private inner class FpJsBridge {
        @android.webkit.JavascriptInterface
        fun profileName(): String = profile.name

        @android.webkit.JavascriptInterface
        fun copyText(text: String) {
            runOnUiThread { copyToClipboard(text) }
        }
    }

    /* ------------------------------------------------------------------ Compose UI */

    @Composable
    fun BrowserScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            // 标签栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .background(MaterialTheme.colorScheme.surface)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabSummaries.forEach { summary ->
                    val selected = summary.selected
                    Surface(
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(horizontal = 3.dp),
                        onClick = { if (!selected) attachTab(summary.index) },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 10.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
                        ) {
                            Text(
                                text = summary.title,
                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.size(width = 110.dp, height = 22.dp),
                            )
                            IconButton(
                                onClick = { closeTab(summary.index) },
                                modifier = Modifier.size(26.dp),
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "关闭标签",
                                    tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }
                IconButton(onClick = { newTab() }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "新建标签")
                }
            }

            // 地址栏
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("输入网址或搜索词") },
                    leadingIcon = {
                        Icon(
                            if (urlText.startsWith("https")) Icons.Default.Lock else Icons.Default.Public,
                            contentDescription = null,
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = { go(urlText) }) {
                            Icon(Icons.Default.Search, contentDescription = "打开")
                        }
                    },
                )
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "菜单")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (desktopMode) "关闭桌面模式" else "请求桌面版") },
                            leadingIcon = { Icon(Icons.Default.DesktopWindows, null) },
                            onClick = { menuExpanded = false; toggleDesktop() },
                        )
                        DropdownMenuItem(
                            text = { Text("新建标签页") },
                            leadingIcon = { Icon(Icons.Default.Add, null) },
                            onClick = { menuExpanded = false; newTab() },
                        )
                        DropdownMenuItem(
                            text = { Text("复制链接") },
                            leadingIcon = { Icon(Icons.Default.ContentPaste, null) },
                            onClick = {
                                menuExpanded = false
                                copyToClipboard(currentUrl())
                            },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("导入 Cookie（当前站点）") },
                            onClick = { menuExpanded = false; cookieImportVisible = true },
                        )
                        DropdownMenuItem(
                            text = { Text("导出 Cookie（当前站点）") },
                            onClick = {
                                menuExpanded = false
                                val text = CookieIO.exportNetscape(currentUrl())
                                cookieDialogText = text.ifBlank { "当前站点没有可导出的 Cookie" }
                            },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("指纹与 IP 检测") },
                            onClick = { menuExpanded = false; go(HOME_URL) },
                        )
                        DropdownMenuItem(
                            text = { Text("退出环境") },
                            onClick = { menuExpanded = false; finish() },
                        )
                    }
                }
            }

            if (pageProgress in 1..99) {
                LinearProgressIndicator(
                    progress = { pageProgress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                AndroidView(factory = { container }, modifier = Modifier.fillMaxSize())

                if (showDegradedBanner) {
                    Surface(
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(8.dp),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Icon(Icons.Default.Warning, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Text(
                                "  数据目录隔离不可用（请更新 Android System WebView），各环境将共享 Cookie/存储",
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                errorMessage?.let { msg ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(20.dp),
                        ) {
                            Text("加载失败", style = MaterialTheme.typography.titleMedium)
                            Text(
                                msg,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                            OutlinedButton(onClick = { tabs.getOrNull(current)?.webView?.reload() }) {
                                Text("重试")
                            }
                        }
                    }
                }
            }

            // 底部工具栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    enabled = canGoBack,
                    onClick = { tabs.getOrNull(current)?.webView?.takeIf { it.canGoBack() }?.goBack() },
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "后退") }
                IconButton(
                    enabled = canGoForward,
                    onClick = { tabs.getOrNull(current)?.webView?.takeIf { it.canGoForward() }?.goForward() },
                ) { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "前进") }
                IconButton(onClick = { tabs.getOrNull(current)?.webView?.reload() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新")
                }
                IconButton(onClick = { go(HOME_URL) }) {
                    Icon(Icons.Default.Home, contentDescription = "检测页")
                }
                IconButton(onClick = { toggleDesktop() }) {
                    Icon(
                        Icons.Default.DesktopWindows,
                        contentDescription = "桌面模式",
                        tint = if (desktopMode) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Cookie 导出对话框
        cookieDialogText?.let { text ->
            AlertDialog(
                onDismissRequest = { cookieDialogText = null },
                title = { Text("Cookie（Netscape）") },
                text = {
                    OutlinedTextField(
                        value = text,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.height(280.dp),
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        copyToClipboard(text)
                        cookieDialogText = null
                    }) { Text("复制") }
                },
                dismissButton = {
                    TextButton(onClick = { cookieDialogText = null }) { Text("关闭") }
                },
            )
        }

        // Cookie 导入对话框
        if (cookieImportVisible) {
            var importText by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { cookieImportVisible = false },
                title = { Text("导入 Cookie 到 ${Uri.parse(currentUrl()).host ?: "当前站点"}") },
                text = {
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        modifier = Modifier.height(280.dp),
                        placeholder = { Text("粘贴 Netscape 格式 Cookie 文本…") },
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val count = CookieIO.importNetscape(currentUrl(), importText)
                        Toast.makeText(this@BrowserActivity, "已导入 $count 条 Cookie", Toast.LENGTH_SHORT).show()
                        cookieImportVisible = false
                        tabs.getOrNull(current)?.webView?.reload()
                    }) { Text("导入") }
                },
                dismissButton = {
                    TextButton(onClick = { cookieImportVisible = false }) { Text("取消") }
                },
            )
        }
    }
}
