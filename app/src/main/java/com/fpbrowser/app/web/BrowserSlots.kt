package com.fpbrowser.app.web

import com.fpbrowser.app.web.BrowserActivity

/**
 * 8 个浏览器槽位 Activity。
 *
 * 每个子类在 Manifest 中声明独立的 android:process（:web1 ~ :web8），
 * 从而在独立进程中持有独立的 WebView 数据目录（Cookie / Storage 隔离）。
 * 同一时刻一个槽位只承载一个"环境"（见 fp/SlotManager）。
 */
class BrowserSlot1Activity : BrowserActivity() { override val slotIndex = 1 }
class BrowserSlot2Activity : BrowserActivity() { override val slotIndex = 2 }
class BrowserSlot3Activity : BrowserActivity() { override val slotIndex = 3 }
class BrowserSlot4Activity : BrowserActivity() { override val slotIndex = 4 }
class BrowserSlot5Activity : BrowserActivity() { override val slotIndex = 5 }
class BrowserSlot6Activity : BrowserActivity() { override val slotIndex = 6 }
class BrowserSlot7Activity : BrowserActivity() { override val slotIndex = 7 }
class BrowserSlot8Activity : BrowserActivity() { override val slotIndex = 8 }
