package com.dolphin.schedule.ui

import android.app.ActivityManager
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.dolphin.schedule.NativeActivity
import com.dolphin.schedule.academic.LegacyAcademicActivity
import com.dolphin.schedule.data.NativeRepository
import com.dolphin.schedule.data.RepositoryState
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** 只验证隔离路径在模拟器可启动，不代替 WebView 114 上的真实学校登录验收。 */
class AcademicLegacyBrowserTest {
    @Test fun dedicatedBrowserProcessCreatesSeparateStorageAndLeavesScheduleUntouched() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        check(InstrumentationRegistry.getArguments().getString("allowNativeDataTests")=="true")
        check(Build.HARDWARE in listOf("ranchu","goldfish") && Build.MODEL.startsWith("sdk_"))
        val repository=NativeRepository.get(context)
        val before=runBlocking { (repository.initialize() as RepositoryState.Ready).snapshot.data }
        ActivityScenario.launch(NativeActivity::class.java).use {
            context.startActivity(Intent(context,LegacyAcademicActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("requestId",UUID.randomUUID().toString()))
            try {
                waitFor {
                    instrumentation.uiAutomation.rootInActiveWindow
                        ?.findAccessibilityNodeInfosByText("识别课表")?.isNotEmpty()==true
                }
                val manager=context.getSystemService(ActivityManager::class.java)
                assertTrue(manager.runningAppProcesses.any {
                    it.processName=="${context.packageName}:academic_legacy" && it.pid!=android.os.Process.myPid()
                })
                waitFor { File(context.applicationInfo.dataDir,"app_webview_${LegacyAcademicActivity.DATA_SUFFIX}").isDirectory }
                assertEquals(before,(repository.state.value as RepositoryState.Ready).snapshot.data)
            } finally {
                instrumentation.uiAutomation.rootInActiveWindow
                    ?.findAccessibilityNodeInfosByText("返回")?.firstOrNull()
                    ?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                waitFor {
                    instrumentation.uiAutomation.rootInActiveWindow
                        ?.findAccessibilityNodeInfosByText("识别课表")?.isEmpty()!=false
                }
            }
        }
    }
    private fun waitFor(condition: ()->Boolean) {
        val deadline=SystemClock.uptimeMillis()+15_000
        while(!condition()) {
            check(SystemClock.uptimeMillis()<deadline) { "专用教务浏览进程未达到预期状态" }
            SystemClock.sleep(100)
        }
    }
}
