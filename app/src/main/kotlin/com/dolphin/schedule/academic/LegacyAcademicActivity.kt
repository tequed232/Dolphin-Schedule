package com.dolphin.schedule.academic

import android.os.Build
import android.webkit.WebView

/** 旧 WebView 不支持多 Profile 时，使用专用进程与独立磁盘目录。 */
class LegacyAcademicActivity: AcademicActivity() {
    override val legacyIsolation=true
    override fun prepareStorage() {
        if(Build.VERSION.SDK_INT<28)error("独立教务数据目录需要 Android 9.0")
        if(Build.VERSION.SDK_INT>=28 && !prepared) {
            // 必须早于本进程中任何 WebView / CookieManager / AndroidX WebKit 调用。
            WebView.setDataDirectorySuffix(DATA_SUFFIX)
            prepared=true
        }
    }
    companion object {
        const val DATA_SUFFIX="dolphin-academic-legacy"
        private var prepared=false
    }
}
