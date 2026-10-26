package com.fpbrowser.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.fpbrowser.app.data.ProfileEntity
import com.fpbrowser.app.data.ProfileRepository
import com.fpbrowser.app.data.sanitized
import com.fpbrowser.app.fp.SlotManager
import com.fpbrowser.app.ui.theme.FpBrowserTheme
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

/**
 * 环境管理主界面：预置环境列表、启动环境、编辑 / 复制 / 删除、备份导入导出。
 */
class MainActivity : ComponentActivity() {

    private val vm: ProfileViewModel by viewModels()

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        if (uri != null) exportProfiles(uri)
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) importProfiles(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FpBrowserTheme {
                ProfileListScreen(
                    vm = vm,
                    onLaunch = { profile ->
                        vm.touch(profile)
                        SlotManager.launch(this, profile.id)
                    },
                    onEdit = { profile ->
                        startActivity(
                            Intent(this, ProfileEditActivity::class.java)
                                .putExtra(ProfileEditActivity.EXTRA_PROFILE_ID, profile.id),
                        )
                    },
                    onCreate = {
                        startActivity(Intent(this, ProfileEditActivity::class.java))
                    },
                    onExportAll = { exportLauncher.launch("fp-browser-profiles.json") },
                    onImportAll = { importLauncher.launch(arrayOf("application/json")) },
                )
            }
        }
    }

    private fun exportProfiles(uri: Uri) {
        runCatching {
            val json = Gson().toJson(vm.profiles.value)
            contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
        }.onSuccess {
            Toast.makeText(this, "已导出 ${vm.profiles.value.size} 个环境", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(this, "导出失败：${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun importProfiles(uri: Uri) {
        runCatching {
            val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
            val type = object : TypeToken<List<ProfileEntity>>() {}.type
            val list: List<ProfileEntity> = Gson().fromJson(text, type)
            list.map { it.sanitized() }
        }.onSuccess { list ->
            if (list.isEmpty()) {
                Toast.makeText(this, "文件中没有可导入的环境", Toast.LENGTH_SHORT).show()
            } else {
                list.forEach { vm.save(it) }
                Toast.makeText(this, "已导入 ${list.size} 个环境", Toast.LENGTH_SHORT).show()
            }
        }.onFailure {
            Toast.makeText(this, "导入失败：${it.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileListScreen(
    vm: ProfileViewModel,
    onLaunch: (ProfileEntity) -> Unit,
    onEdit: (ProfileEntity) -> Unit,
    onCreate: () -> Unit,
    onExportAll: () -> Unit,
    onImportAll: () -> Unit,
) {
    val context = LocalContext.current
    val profiles by vm.profiles.collectAsState()
    var query by remember { mutableStateOf("") }
    var aboutVisible by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ProfileEntity?>(null) }

    val filtered = remember(profiles, query) {
        if (query.isBlank()) profiles
        else profiles.filter {
            it.name.contains(query, true) ||
                it.remark.contains(query, true) ||
                it.tags.any { t -> t.contains(query, true) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("指纹浏览器") },
                actions = {
                    IconButton(onClick = onImportAll) {
                        Icon(Icons.Default.FileUpload, contentDescription = "导入环境")
                    }
                    IconButton(onClick = onExportAll) {
                        Icon(Icons.Default.FileDownload, contentDescription = "导出环境")
                    }
                    IconButton(onClick = { aboutVisible = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "关于")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreate) {
                Icon(Icons.Default.Add, contentDescription = "新建环境")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("搜索环境名称 / 备注 / 标签") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (profiles.isEmpty()) "还没有环境，点击右下角 + 新建" else "没有匹配的环境",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(300.dp),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(filtered, key = { it.id }) { profile ->
                        ProfileCard(
                            profile = profile,
                            onLaunch = { onLaunch(profile) },
                            onEdit = { onEdit(profile) },
                            onDuplicate = {
                                vm.save(
                                    profile.copy(
                                        id = UUID.randomUUID().toString(),
                                        name = "${profile.name} 副本",
                                        createdAt = System.currentTimeMillis(),
                                        updatedAt = System.currentTimeMillis(),
                                        lastUsedAt = 0L,
                                    ),
                                )
                            },
                            onDelete = { pendingDelete = profile },
                        )
                    }
                }
            }
        }
    }

    if (aboutVisible) {
        AlertDialog(
            onDismissRequest = { aboutVisible = false },
            title = { Text("关于 FpBrowser") },
            text = {
                Text(
                    "安卓端指纹浏览器 v1.0\n\n" +
                        "· 每个环境独立进程 + 独立 WebView 数据目录（Cookie / 存储隔离）\n" +
                        "· 每环境独立代理（HTTP / SOCKS5），随时更改出口 IP\n" +
                        "· document-start 注入指纹伪装（UA / 时区 / 屏幕 / WebGL / Canvas / Audio / WebRTC）\n" +
                        "· 首次启动已预置 6 套环境，开箱即用\n\n" +
                        "详见仓库 README.md 中的架构说明与平台限制。",
                )
            },
            confirmButton = {
                TextButton(onClick = { aboutVisible = false }) { Text("知道了") }
            },
        )
    }

    pendingDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除环境") },
            text = { Text("确定删除「${profile.name}」吗？该环境的 Cookie 与本地数据将一并清除。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(profile)
                    pendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ProfileCard(
    profile: ProfileEntity,
    onLaunch: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val avatarColor = remember(profile.colorHex) {
        runCatching { Color(android.graphics.Color.parseColor(profile.colorHex)) }
            .getOrDefault(Color(0xFF5B8DEF))
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(avatarColor, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = profile.name.take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                    )
                    Text(
                        text = profile.proxy.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (profile.proxy.isUsable) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("复制环境") },
                            onClick = { menuExpanded = false; onDuplicate() },
                        )
                        DropdownMenuItem(
                            text = { Text("删除") },
                            leadingIcon = { Icon(Icons.Default.Delete, null) },
                            onClick = { menuExpanded = false; onDelete() },
                        )
                    }
                }
            }

            if (profile.tags.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = profile.tags.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (profile.remark.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = profile.remark,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = buildString {
                    append(profile.fingerprint.platform)
                    append(" · ")
                    append(profile.fingerprint.timezone)
                    append(" · ")
                    append("${profile.fingerprint.screenWidth}×${profile.fingerprint.screenHeight}")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onLaunch, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("启动")
                }
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("编辑")
                }
            }
        }
    }
}
