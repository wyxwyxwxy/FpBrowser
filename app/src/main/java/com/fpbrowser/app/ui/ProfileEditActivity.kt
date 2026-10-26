package com.fpbrowser.app.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.fpbrowser.app.data.FingerprintConfig
import com.fpbrowser.app.data.FingerprintGenerator
import com.fpbrowser.app.data.Presets.FingerprintTemplate
import com.fpbrowser.app.data.Presets
import com.fpbrowser.app.data.ProfileEntity
import com.fpbrowser.app.data.ProfileRepository
import com.fpbrowser.app.data.ProxyConfig
import com.fpbrowser.app.data.sanitized
import com.fpbrowser.app.ui.theme.FpBrowserTheme
import com.fpbrowser.app.util.IpChecker
import com.google.gson.Gson
import kotlinx.coroutines.launch
import java.util.UUID

/** 可选时区（IANA） */
private val TIMEZONES = listOf(
    "America/New_York", "America/Chicago", "America/Denver", "America/Los_Angeles",
    "America/Anchorage", "America/Sao_Paulo", "America/Mexico_City", "America/Toronto",
    "Europe/London", "Europe/Dublin", "Europe/Lisbon", "Europe/Paris", "Europe/Madrid",
    "Europe/Berlin", "Europe/Rome", "Europe/Amsterdam", "Europe/Brussels", "Europe/Warsaw",
    "Europe/Stockholm", "Europe/Zurich", "Europe/Moscow", "Europe/Istanbul",
    "Asia/Dubai", "Asia/Kolkata", "Asia/Bangkok", "Asia/Jakarta", "Asia/Manila",
    "Asia/Shanghai", "Asia/Singapore", "Asia/Seoul", "Asia/Tokyo",
    "Australia/Sydney", "Australia/Perth", "Pacific/Auckland",
    "Africa/Cairo", "Africa/Lagos", "Africa/Johannesburg", "UTC",
)

/**
 * 环境编辑器：基础信息 / 代理 / 指纹三块。
 * 新建时默认载入 Windows Chrome 模板，也可一键套用其他预置模板或随机生成。
 */
class ProfileEditActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PROFILE_ID = "profile_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val profileId = intent.getStringExtra(EXTRA_PROFILE_ID)
        setContent {
            FpBrowserTheme {
                var existing by remember { mutableStateOf<ProfileEntity?>(null) }
                var loading by remember { mutableStateOf(!profileId.isNullOrBlank()) }
                LaunchedEffect(profileId) {
                    if (!profileId.isNullOrBlank()) {
                        existing = ProfileRepository.get(this@ProfileEditActivity).get(profileId)
                    }
                    loading = false
                }
                if (loading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    ProfileEditScreen(
                        initial = existing,
                        onSave = { entity ->
                            lifecycleScope.launch {
                                ProfileRepository.get(this@ProfileEditActivity).save(entity)
                                finish()
                            }
                        },
                        onCancel = { finish() },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(
    initial: ProfileEntity?,
    onSave: (ProfileEntity) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ---- 基础信息 ----
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var nameError by remember { mutableStateOf(false) }
    var remark by remember { mutableStateOf(initial?.remark ?: "") }
    var tagsText by remember { mutableStateOf(initial?.tags?.joinToString(", ") ?: "") }

    // ---- 代理 ----
    var proxyEnabled by remember { mutableStateOf(initial?.proxy?.enabled ?: false) }
    var proxyType by remember { mutableStateOf(initial?.proxy?.type ?: ProxyConfig.ProxyType.SOCKS5) }
    var proxyHost by remember { mutableStateOf(initial?.proxy?.host ?: "") }
    var proxyPort by remember { mutableStateOf((initial?.proxy?.port ?: 1080).toString()) }
    var proxyUser by remember { mutableStateOf(initial?.proxy?.username ?: "") }
    var proxyPass by remember { mutableStateOf(initial?.proxy?.password ?: "") }
    var directFallback by remember { mutableStateOf(initial?.proxy?.allowDirectFallback ?: false) }
    var proxyTestResult by remember { mutableStateOf<String?>(null) }
    var proxyTesting by remember { mutableStateOf(false) }

    // ---- 指纹 ----
    var fp by remember {
        mutableStateOf(initial?.fingerprint ?: Presets.template(FingerprintTemplate.WINDOWS_CHROME))
    }
    var tzDialog by remember { mutableStateOf(false) }
    var jsonExportText by remember { mutableStateOf<String?>(null) }
    var jsonImportVisible by remember { mutableStateOf(false) }

    fun buildEntity(): ProfileEntity {
        val tags = tagsText.split(',', '，', ' ', ';').map { it.trim() }.filter { it.isNotEmpty() }
        return ProfileEntity(
            id = initial?.id ?: UUID.randomUUID().toString(),
            name = name.trim(),
            remark = remark.trim(),
            tags = tags,
            colorHex = initial?.colorHex ?: "#5B8DEF",
            fingerprint = fp,
            proxy = ProxyConfig(
                enabled = proxyEnabled && proxyHost.isNotBlank(),
                type = proxyType,
                host = proxyHost.trim(),
                port = proxyPort.toIntOrNull() ?: 1080,
                username = proxyUser,
                password = proxyPass,
                allowDirectFallback = directFallback,
            ),
            createdAt = initial?.createdAt ?: System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            lastUsedAt = initial?.lastUsedAt ?: 0L,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (initial == null) "新建环境" else "编辑环境") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
        ) {
            // ============ 基础信息 ============
            SectionCard(title = "基础信息") {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = it.isBlank()
                    },
                    label = { Text("环境名称") },
                    singleLine = true,
                    isError = nameError,
                    supportingText = if (nameError) {
                        { Text("名称不能为空") }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = remark,
                    onValueChange = { remark = it },
                    label = { Text("备注（用途 / 归属等）") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = tagsText,
                    onValueChange = { tagsText = it },
                    label = { Text("标签（逗号分隔）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ============ 代理 ============
            SectionCard(title = "网络代理（更改 IP）") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = proxyEnabled, onCheckedChange = { proxyEnabled = it })
                    Spacer(Modifier.width(8.dp))
                    Text(if (proxyEnabled) "已启用代理" else "未启用（直连）")
                }
                if (proxyEnabled) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProxyConfig.ProxyType.entries.forEach { type ->
                            FilterChip(
                                selected = proxyType == type,
                                onClick = { proxyType = type },
                                label = { Text(type.label) },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = proxyHost,
                        onValueChange = { proxyHost = it },
                        label = { Text("代理地址（IP 或域名）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = proxyPort,
                        onValueChange = { proxyPort = it.filter { c -> c.isDigit() } },
                        label = { Text("端口") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = proxyUser,
                        onValueChange = { proxyUser = it },
                        label = { Text("用户名（可空）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = proxyPass,
                        onValueChange = { proxyPass = it },
                        label = { Text("密码（可空）") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = directFallback, onCheckedChange = { directFallback = it })
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "代理失败时允许直连兜底（关闭更安全：避免泄露真实 IP）",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            proxyTesting = true
                            proxyTestResult = null
                            scope.launch {
                                val cfg = ProxyConfig(
                                    enabled = true,
                                    type = proxyType,
                                    host = proxyHost.trim(),
                                    port = proxyPort.toIntOrNull() ?: 0,
                                    username = proxyUser,
                                    password = proxyPass,
                                )
                                val r = IpChecker.test(cfg)
                                proxyTestResult = if (r.success) {
                                    buildString {
                                        append("连接成功，出口 IP：")
                                        append(r.ip ?: "未知")
                                        r.country?.let { append(" · $it") }
                                        r.city?.let { append(" $it") }
                                        r.isp?.let { append(" · $it") }
                                        append("（${r.latencyMs}ms）")
                                    }
                                } else {
                                    "连接失败：${r.error ?: "未知错误"}（${r.latencyMs}ms）"
                                }
                                proxyTesting = false
                            }
                        },
                        enabled = !proxyTesting && proxyHost.isNotBlank(),
                    ) {
                        if (proxyTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(if (proxyTesting) "测试中…" else "测试连通性 / 查看出口 IP")
                    }
                    proxyTestResult?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (it.startsWith("连接成功")) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                    }
                }
            }

            // ============ 指纹 ============
            SectionCard(title = "指纹环境") {
                Text("预置模板", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FingerprintTemplate.entries.filter { it != FingerprintTemplate.RANDOM }.forEach { t ->
                        FilterChip(
                            selected = false,
                            onClick = { fp = Presets.template(t) },
                            label = { Text(t.label) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { fp = FingerprintGenerator.random() }) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("随机生成")
                    }
                    OutlinedButton(onClick = { jsonExportText = Gson().toJson(fp) }) {
                        Text("导出 JSON")
                    }
                    OutlinedButton(onClick = { jsonImportVisible = true }) {
                        Text("导入 JSON")
                    }
                }

                Spacer(Modifier.height(10.dp))
                FieldLabel("User-Agent")
                OutlinedTextField(
                    value = fp.userAgent,
                    onValueChange = { fp = fp.copy(userAgent = it) },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        FieldLabel("平台 platform")
                        OutlinedTextField(
                            value = fp.platform,
                            onValueChange = { fp = fp.copy(platform = it) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Box(Modifier.weight(1f)) {
                        FieldLabel("厂商 vendor")
                        OutlinedTextField(
                            value = fp.vendor,
                            onValueChange = { fp = fp.copy(vendor = it) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))
                FieldLabel("语言 language / languages")
                OutlinedTextField(
                    value = fp.language,
                    onValueChange = { fp = fp.copy(language = it) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = fp.languages.joinToString(","),
                    onValueChange = { v ->
                        fp = fp.copy(languages = v.split(',').map { it.trim() }.filter { s -> s.isNotEmpty() })
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(6.dp))
                FieldLabel("时区 timezone")
                OutlinedButton(onClick = { tzDialog = true }) {
                    Text(fp.timezone)
                }

                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        FieldLabel("屏幕宽")
                        NumberField(fp.screenWidth.toString()) { v -> fp = fp.copy(screenWidth = v, availWidth = if (fp.availWidth > v) v else fp.availWidth) }
                    }
                    Box(Modifier.weight(1f)) {
                        FieldLabel("屏幕高")
                        NumberField(fp.screenHeight.toString()) { v -> fp = fp.copy(screenHeight = v, availHeight = if (fp.availHeight > v) v else fp.availHeight) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        FieldLabel("色深")
                        NumberField(fp.colorDepth.toString()) { v -> fp = fp.copy(colorDepth = v) }
                    }
                    Box(Modifier.weight(1f)) {
                        FieldLabel("DPR")
                        OutlinedTextField(
                            value = fp.devicePixelRatio.toString(),
                            onValueChange = { v -> fp = fp.copy(devicePixelRatio = v.toDoubleOrNull() ?: 1.0) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        FieldLabel("CPU 核心")
                        NumberField(fp.hardwareConcurrency.toString()) { v -> fp = fp.copy(hardwareConcurrency = v) }
                    }
                    Box(Modifier.weight(1f)) {
                        FieldLabel("内存 GB")
                        NumberField(fp.deviceMemory.toString()) { v -> fp = fp.copy(deviceMemory = v) }
                    }
                    Box(Modifier.weight(1f)) {
                        FieldLabel("触摸点")
                        NumberField(fp.maxTouchPoints.toString()) { v -> fp = fp.copy(maxTouchPoints = v) }
                    }
                }

                Spacer(Modifier.height(6.dp))
                FieldLabel("WebGL 厂商")
                OutlinedTextField(
                    value = fp.webglVendor,
                    onValueChange = { fp = fp.copy(webglVendor = it) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                FieldLabel("WebGL 渲染器")
                OutlinedTextField(
                    value = fp.webglRenderer,
                    onValueChange = { fp = fp.copy(webglRenderer = it) },
                    singleLine = false,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(6.dp))
                FieldLabel("Canvas 噪声种子（决定噪声图案，同种子结果一致）")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = fp.canvasNoiseSeed,
                        onValueChange = { fp = fp.copy(canvasNoiseSeed = it) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(6.dp))
                    OutlinedButton(onClick = { fp = fp.copy(canvasNoiseSeed = Presets.freshSeed("canvas")) }) {
                        Text("换一个")
                    }
                }
                Spacer(Modifier.height(6.dp))
                FieldLabel("Audio 噪声种子")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = fp.audioNoiseSeed,
                        onValueChange = { fp = fp.copy(audioNoiseSeed = it) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(6.dp))
                    OutlinedButton(onClick = { fp = fp.copy(audioNoiseSeed = Presets.freshSeed("audio")) }) {
                        Text("换一个")
                    }
                }

                Spacer(Modifier.height(8.dp))
                ChipRow("Canvas 噪声", listOf("noise", "off"), fp.canvasMode) { v -> fp = fp.copy(canvasMode = v) }
                ChipRow("Audio 噪声", listOf("noise", "off"), fp.audioMode) { v -> fp = fp.copy(audioMode = v) }
                ChipRow("WebRTC", listOf("default", "relay", "disabled"), fp.webRtcMode) { v -> fp = fp.copy(webRtcMode = v) }
                ChipRow("引擎", listOf("chromium", "webkit"), fp.browser) { v -> fp = fp.copy(browser = v) }
                ChipRow("Do Not Track", listOf("", "1"), fp.doNotTrack ?: "") { v -> fp = fp.copy(doNotTrack = v.ifBlank { null }) }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        when {
                            name.isBlank() -> {
                                nameError = true
                                Toast.makeText(context, "请填写环境名称", Toast.LENGTH_SHORT).show()
                            }
                            fp.userAgent.isBlank() -> {
                                Toast.makeText(context, "User-Agent 不能为空", Toast.LENGTH_SHORT).show()
                            }
                            proxyEnabled && proxyHost.isBlank() -> {
                                Toast.makeText(context, "请填写代理地址", Toast.LENGTH_SHORT).show()
                            }
                            proxyEnabled && (proxyPort.toIntOrNull() ?: 0) !in 1..65535 -> {
                                Toast.makeText(context, "代理端口无效", Toast.LENGTH_SHORT).show()
                            }
                            else -> onSave(buildEntity())
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("保存") }
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text("取消")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // ---- 时区选择对话框 ----
    if (tzDialog) {
        var query by remember { mutableStateOf("") }
        val filtered = remember(query) { TIMEZONES.filter { it.contains(query, ignoreCase = true) } }
        AlertDialog(
            onDismissRequest = { tzDialog = false },
            title = { Text("选择时区") },
            text = {
                Column {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        placeholder = { Text("搜索时区…") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.height(320.dp)) {
                        items(filtered) { tz ->
                            DropdownMenuItem(
                                text = { Text(tz) },
                                onClick = {
                                    fp = fp.copy(timezone = tz)
                                    tzDialog = false
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { tzDialog = false }) { Text("关闭") }
            },
        )
    }

    // ---- 指纹 JSON 导出 ----
    jsonExportText?.let { text ->
        AlertDialog(
            onDismissRequest = { jsonExportText = null },
            title = { Text("指纹 JSON") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.height(320.dp),
                )
            },
            confirmButton = { TextButton(onClick = { jsonExportText = null }) { Text("关闭") } },
        )
    }

    // ---- 指纹 JSON 导入 ----
    if (jsonImportVisible) {
        var importText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { jsonImportVisible = false },
            title = { Text("导入指纹 JSON") },
            text = {
                OutlinedTextField(
                    value = importText,
                    onValueChange = { importText = it },
                    placeholder = { Text("粘贴配置 JSON…") },
                    modifier = Modifier.height(320.dp),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    runCatching {
                        val parsed = Gson().fromJson(importText, FingerprintConfig::class.java)
                        fp = parsed.sanitized()
                    }.onFailure {
                        Toast.makeText(context, "JSON 解析失败：${it.message}", Toast.LENGTH_SHORT).show()
                    }
                    jsonImportVisible = false
                }) { Text("导入") }
            },
            dismissButton = {
                TextButton(onClick = { jsonImportVisible = false }) { Text("取消") }
            },
        )
    }
}

/* ------------------------------------------------------------------ 复用组件 */

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Spacer(Modifier.height(12.dp))
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 2.dp),
    )
}

@Composable
private fun NumberField(value: String, onChange: (Int) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { c -> c.isDigit() }.toIntOrNull() ?: 0) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ChipRow(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    FieldLabel(label)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { opt ->
            FilterChip(
                selected = selected == opt,
                onClick = { onSelect(opt) },
                label = { Text(if (opt.isEmpty()) "未设置" else opt) },
            )
        }
    }
    Spacer(Modifier.height(6.dp))
}
