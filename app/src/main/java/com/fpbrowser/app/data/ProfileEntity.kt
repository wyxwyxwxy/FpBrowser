package com.fpbrowser.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 一个"环境" = 一套独立指纹 + 一套独立代理 + 一套独立存储（独立进程的 WebView 数据目录）。
 */
@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val remark: String = "",
    val tags: List<String> = emptyList(),
    /** 列表头像颜色，如 #5B8DEF */
    val colorHex: String = "#5B8DEF",
    val fingerprint: FingerprintConfig,
    val proxy: ProxyConfig,
    val createdAt: Long,
    val updatedAt: Long,
    val lastUsedAt: Long = 0L,
)
