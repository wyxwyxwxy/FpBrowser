package com.fpbrowser.app.data

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {

    @TypeConverter
    fun fingerprintToJson(value: FingerprintConfig): String = Gson().toJson(value)

    @TypeConverter
    fun jsonToFingerprint(value: String): FingerprintConfig =
        Gson().fromJson(value, FingerprintConfig::class.java)

    @TypeConverter
    fun proxyToJson(value: ProxyConfig): String = Gson().toJson(value)

    @TypeConverter
    fun jsonToProxy(value: String): ProxyConfig =
        Gson().fromJson(value, ProxyConfig::class.java)

    @TypeConverter
    fun tagsToJson(value: List<String>): String = Gson().toJson(value)

    @TypeConverter
    fun jsonToTags(value: String): List<String> =
        Gson().fromJson(value, object : TypeToken<List<String>>() {}.type)
}
