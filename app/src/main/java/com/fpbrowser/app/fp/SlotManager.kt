package com.fpbrowser.app.fp

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.fpbrowser.app.web.BrowserSlot1Activity
import com.fpbrowser.app.web.BrowserSlot2Activity
import com.fpbrowser.app.web.BrowserSlot3Activity
import com.fpbrowser.app.web.BrowserSlot4Activity
import com.fpbrowser.app.web.BrowserSlot5Activity
import com.fpbrowser.app.web.BrowserSlot6Activity
import com.fpbrowser.app.web.BrowserSlot7Activity
import com.fpbrowser.app.web.BrowserSlot8Activity
import org.json.JSONObject
import java.io.File

/**
 * 浏览器"槽位"管理。
 *
 * 每个环境（profile）在独立进程中运行 WebView，通过 WebView 数据目录后缀
 * （fp_profile_<id>）实现 Cookie / LocalStorage / IndexedDB 完全隔离。
 * 由于一个进程同一时刻只能持有一个数据目录，采用固定槽位池（默认 8 个）：
 *   - 同一环境重复启动 → 直接切回已有任务；
 *   - 槽位被其他环境占用 → 结束旧进程后复用；
 *   - 槽位文件（filesDir/slots/slot_N.json）记录每个槽位当前绑定的环境，
 *     浏览器进程启动时读取它来设置数据目录后缀与代理。
 */
object SlotManager {

    private const val TAG = "SlotManager"
    const val SLOT_COUNT = 8

    private val slotClasses = listOf(
        BrowserSlot1Activity::class.java,
        BrowserSlot2Activity::class.java,
        BrowserSlot3Activity::class.java,
        BrowserSlot4Activity::class.java,
        BrowserSlot5Activity::class.java,
        BrowserSlot6Activity::class.java,
        BrowserSlot7Activity::class.java,
        BrowserSlot8Activity::class.java,
    )

    fun processName(context: Context, slot: Int): String = "${context.packageName}:web$slot"

    fun slotFile(context: Context, slot: Int): File {
        val dir = File(context.filesDir, "slots")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "slot_$slot.json")
    }

    fun writeSlotFile(context: Context, slot: Int, profileId: String) {
        val json = JSONObject().apply {
            put("slot", slot)
            put("profileId", profileId)
            put("updatedAt", System.currentTimeMillis())
        }.toString()
        slotFile(context, slot).writeText(json)
    }

    fun readSlotProfile(context: Context, slot: Int): String? = try {
        val f = slotFile(context, slot)
        if (!f.exists()) null else JSONObject(f.readText()).optString("profileId").ifBlank { null }
    } catch (t: Throwable) {
        Log.w(TAG, "读取槽位文件失败 slot=$slot", t)
        null
    }

    /** 启动（或切回）某个环境。可在任意线程调用。 */
    fun launch(context: Context, profileId: String) {
        val appContext = context.applicationContext
        Thread {
            val slot = pickSlot(appContext, profileId)
            // 确保旧进程已退出，避免新环境跑在旧进程的错误数据目录里
            waitProcessGone(appContext, processName(appContext, slot))
            writeSlotFile(appContext, slot, profileId)
            Handler(Looper.getMainLooper()).post {
                try {
                    val intent = Intent(appContext, slotClasses[slot - 1]).apply {
                        putExtra("profile_id", profileId)
                        putExtra("slot", slot)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    appContext.startActivity(intent)
                } catch (t: Throwable) {
                    Log.e(TAG, "启动浏览器环境失败", t)
                }
            }
        }.start()
    }

    private fun pickSlot(context: Context, profileId: String): Int {
        val pkg = context.packageName
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val running = try {
            am.runningAppProcesses?.filter { it.processName.startsWith("$pkg:web") } ?: emptyList()
        } catch (t: Throwable) { emptyList() }
        val runningByName = running.associateBy { it.processName }

        val slotProfiles = (1..SLOT_COUNT)
            .mapNotNull { n -> readSlotProfile(context, n)?.let { n to it } }
            .toMap()

        // 1) 同一环境已在运行 → 复用其槽位（singleTask 会直接切回）
        slotProfiles.entries.firstOrNull { entry ->
            entry.value == profileId && runningByName.containsKey("$pkg:web${entry.key}")
        }?.let { return it.key }

        // 2) 优先空闲槽位
        (1..SLOT_COUNT).firstOrNull { n -> !runningByName.containsKey("$pkg:web$n") }
            ?.let { return it }

        // 3) 全部被占用 → 抢占第一个运行着其他环境的槽位（先杀进程）
        slotProfiles.entries.firstOrNull { it.value != profileId }?.let { entry ->
            runningByName["$pkg:web${entry.key}"]?.let {
                Log.i(TAG, "抢占槽位 ${entry.key}（结束 pid=${it.pid}）")
                android.os.Process.killProcess(it.pid)
            }
            return entry.key
        }
        return 1
    }

    private fun waitProcessGone(context: Context, processName: String, timeoutMs: Long = 4000L): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val alive = try {
                am.runningAppProcesses?.any { it.processName == processName } == true
            } catch (t: Throwable) { false }
            if (!alive) return true
            try { Thread.sleep(50) } catch (_: InterruptedException) { return true }
        }
        return false
    }
}
