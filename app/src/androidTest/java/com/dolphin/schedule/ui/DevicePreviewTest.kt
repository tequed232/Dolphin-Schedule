package com.dolphin.schedule.ui

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.InputDevice
import android.view.accessibility.AccessibilityNodeInfo
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.dolphin.schedule.NativeActivity
import com.dolphin.schedule.data.AppDataCodec
import com.dolphin.schedule.data.NativeRepository
import com.dolphin.schedule.data.RepositoryState
import com.dolphin.schedule.domain.displayTimetableRows
import com.dolphin.schedule.domain.timetableTimeOffset
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** 独立验收包读取真实课表副本；使用公开触摸 API，避免旧 Espresso 对新系统隐藏 API 的依赖。 */
class DevicePreviewTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private fun await(message: String,condition: ()->Boolean) {
        val deadline=SystemClock.elapsedRealtime()+15_000
        while(!condition()) { check(SystemClock.elapsedRealtime()<deadline) { message };SystemClock.sleep(100) }
    }
    private fun find(label: String,prefix: Boolean=false): AccessibilityNodeInfo? {
        fun visit(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if(node==null)return null
            val description=node.contentDescription?.toString()
            if(node.isVisibleToUser && ((if(prefix)description?.startsWith(label)==true else description==label) || node.text?.toString()==label))return node
            for(index in 0 until node.childCount)visit(node.getChild(index))?.let { return it }
            return null
        }
        return visit(instrumentation.uiAutomation.rootInActiveWindow)
    }
    private fun touch(x1: Float,y1: Float,x2: Float=x1,y2: Float=y1,duration: Int=80) {
        await("系统窗口遮挡验收包，暂停触摸注入") { instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString()==context.packageName }
        val start=SystemClock.uptimeMillis()
        fun event(action: Int,x: Float,y: Float) {
            val motion=MotionEvent.obtain(start,SystemClock.uptimeMillis(),action,x,y,0)
            motion.source=InputDevice.SOURCE_TOUCHSCREEN
            check(instrumentation.uiAutomation.injectInputEvent(motion,true)) { "系统拒绝触摸注入" };motion.recycle()
        }
        event(MotionEvent.ACTION_DOWN,x1,y1)
        val steps=maxOf(1,duration/20)
        for(step in 1..steps) { SystemClock.sleep((duration/steps).toLong());event(MotionEvent.ACTION_MOVE,x1+(x2-x1)*step/steps,y1+(y2-y1)*step/steps) }
        event(MotionEvent.ACTION_UP,x2,y2);SystemClock.sleep(500)
    }
    private fun click(label: String) {
        await("界面入口未出现：$label") { find(label)!=null }
        val bounds=Rect();find(label)!!.getBoundsInScreen(bounds)
        touch(bounds.exactCenterX(),bounds.exactCenterY())
    }
    private fun capture(name: String) {
        SystemClock.sleep(400)
        val bitmap=instrumentation.uiAutomation.takeScreenshot()
        File(context.cacheDir,name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
    }
    @Test fun dockSwitchFrameTiming() {
        check(context.packageName=="com.dolphin.schedule.devicepreview.debug")
        check(InstrumentationRegistry.getArguments().getString("allowDevicePreview")=="true")
        val durations=java.util.Collections.synchronizedList(mutableListOf<Long>())
        val worker=android.os.HandlerThread("preview-frame-metrics").apply { start() }
        val listener=android.view.Window.OnFrameMetricsAvailableListener { _,metrics,_ ->
            durations+=metrics.getMetric(android.view.FrameMetrics.TOTAL_DURATION)
        }
        try { ActivityScenario.launch(NativeActivity::class.java).use { scenario ->
            lateinit var model: DolphinViewModel
            scenario.onActivity { activity -> model=ViewModelProvider(activity)[DolphinViewModel::class.java];model.pages.clear();activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);activity.window.addOnFrameMetricsAvailableListener(listener,android.os.Handler(worker.looper)) }
            await("课表尚未准备好") { model.state.value is RepositoryState.Ready }
            SystemClock.sleep(600)
            durations.clear()
            repeat(3) { listOf("列表","平铺","搜索","设置").forEach(::click) }
            SystemClock.sleep(600)
            scenario.onActivity { it.window.removeOnFrameMetricsAvailableListener(listener) }
            val samples=synchronized(durations) { durations.sorted() }
            check(samples.size>=8) { "帧数据不足，不能评估切换" }
            val report=org.json.JSONObject().put("samples",samples.size)
                .put("p50Ms",samples[samples.size/2]/1_000_000.0)
                .put("p95Ms",samples[(samples.size*.95).toInt().coerceAtMost(samples.lastIndex)]/1_000_000.0)
                .put("maxMs",samples.last()/1_000_000.0).put("over32ms",samples.count { it>32_000_000 })
            File(context.cacheDir,"device-preview-frame-metrics.json").writeText(report.toString(2))
        } } finally { worker.quitSafely() }
    }
    @Test fun realLongPressShowsHandlesAndCancelsWithoutWriting() {
        check(context.packageName=="com.dolphin.schedule.devicepreview.debug")
        check(InstrumentationRegistry.getArguments().getString("allowDevicePreview")=="true")
        ActivityScenario.launch(NativeActivity::class.java).use { scenario ->
            lateinit var model: DolphinViewModel
            scenario.onActivity { activity -> model=ViewModelProvider(activity)[DolphinViewModel::class.java];activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            await("课表尚未准备好") { model.state.value is RepositoryState.Ready }
            val before=(model.state.value as RepositoryState.Ready).snapshot.data
            val course=before.schedule.courses.filter { it.specificDate==null && it.day<=4 && it.start<=4 && it.weeks.isNotEmpty() }.minByOrNull { it.start }
                ?: error("没有适合不保存手势检查的可见课程")
            val originalDate=model.selectedDate
            val date=com.dolphin.schedule.domain.firstMonday(before.schedule.term.startDate).plusWeeks((course.weeks.first()-1).toLong()).plusDays((course.day-1).toLong())
            scenario.onActivity { model.pages.clear();model.selectDate(date);model.select(MainTab.GRID) }
            val display=context.resources.displayMetrics
            repeat(6) { touch(display.widthPixels*.7f,display.heightPixels*.43f,display.widthPixels*.7f,display.heightPixels*.83f,250) }
            await("待检查课程未显示") { find(course.name)!=null }
            val bounds=Rect();find(course.name)!!.getBoundsInScreen(bounds)
            touch(bounds.exactCenterX(),bounds.exactCenterY(),duration=800)
            await("长按未显示开始把手") { find(course.name+"开始节次拖动")!=null }
            await("长按未显示结束把手") { find(course.name+"结束节次拖动")!=null }
            assertNull("长按不应误开课程详情",model.detailId)
            assertNull("只长按不移动不应保留待保存位置",model.layout.draft)
            capture("real-native-layout-handles.png")
            click("完成")
            assertNull(model.layout.selectedId)
            val after=(model.state.value as RepositoryState.Ready).snapshot.data
            assertEquals("手势检查不应修改真实课表",before.schedule,after.schedule)
            assertEquals(before.books,after.books)
            scenario.onActivity { model.selectDate(originalDate) }
            File(context.cacheDir,"real-layout-results.json").writeText(org.json.JSONObject().put("longPressShowsHandles",true).put("detailStayedClosed",true).put("cancelled",true).put("courseDataUnchanged",true).toString(2))
        }
    }
    @Test fun realTimeAxisOpensEditorWithoutChangingCourses() {
        check(context.packageName=="com.dolphin.schedule.devicepreview.debug")
        check(InstrumentationRegistry.getArguments().getString("allowDevicePreview")=="true")
        ActivityScenario.launch(NativeActivity::class.java).use { scenario ->
            lateinit var model: DolphinViewModel
            scenario.onActivity { activity -> model=ViewModelProvider(activity)[DolphinViewModel::class.java];model.pages.clear();model.selectDate(java.time.LocalDate.now());model.select(MainTab.GRID);activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            await("课表尚未准备好") { model.state.value is RepositoryState.Ready }
            val before=(model.state.value as RepositoryState.Ready).snapshot.data
            val first=before.schedule.periods.first()
            val label="编辑第${first.label ?: "1"}节时间"
            click(label)
            await("时间编辑未打开") { find("编辑上课时间")!=null }
            click("取消")
            check(model.periodEdit==null)
            val now=java.time.LocalDateTime.now()
            val expected=timetableTimeOffset(before.schedule.periods,displayTimetableRows(before.schedule),now.toLocalTime())
            var clockFound=false
            if(expected!=null && before.settings.showCurrentTimeLine) {
                val display=context.resources.displayMetrics
                // 前一个用例可能留下阅读位置，先回到时间轴顶部，再寻找当前时刻。
                repeat(6) { touch(display.widthPixels*.7f,display.heightPixels*.43f,display.widthPixels*.7f,display.heightPixels*.83f,300) }
                repeat(8) {
                    if(!clockFound) {
                        clockFound=find("当前时间 ",prefix=true)!=null
                        if(!clockFound)touch(display.widthPixels*.7f,display.heightPixels*.82f,display.widthPixels*.7f,display.heightPixels*.42f,400)
                    }
                }
                check(clockFound) { "当天课表的当前时间线未出现在滚动区域" }
            }
            capture("real-native-clock-and-floating-dock.png")
            val after=(model.state.value as RepositoryState.Ready).snapshot.data
            assertTrue("取消时间编辑不应改变课程或作息",before.schedule==after.schedule)
            File(context.cacheDir,"real-time-axis-results.json").writeText(org.json.JSONObject().put("editorCancelled",true).put("courseDataUnchanged",true).put("clockApplicable",expected!=null).put("clockVisible",clockFound).toString(2))
        }
    }
    @Test fun realCourseCopyMigratesAndNativePagesRemainUsable() {
        check(context.packageName=="com.dolphin.schedule.devicepreview.debug") { "只允许独立的真机验收包" }
        check(InstrumentationRegistry.getArguments().getString("allowDevicePreview")=="true") { "需显式启用副本验收" }
        ActivityScenario.launch(NativeActivity::class.java).use { scenario ->
            val repository=NativeRepository.get(context)
            await("真实课表副本读取超时") { repository.state.value !is RepositoryState.Loading }
            check(repository.state.value is RepositoryState.Ready) { "真实课表副本读取失败" }
            val snapshot=(repository.state.value as RepositoryState.Ready).snapshot
            val initial=snapshot.data
            val backup=File(context.filesDir,"native-migration-backup.json")
            check(backup.isFile) { "缺少迁移原文，不能验收空白或伪造课表" }
            val legacy=AppDataCodec.decode(backup.readText())
            assertTrue("必须存在真实课程副本",initial.schedule.courses.isNotEmpty())
            val migrationEqual=legacy==initial
            if(snapshot.revision==1L)assertTrue("首次迁移应保留完整数据",migrationEqual)
            assertTrue("数据格式应完整往返保存",AppDataCodec.decode(AppDataCodec.encode(initial))==initial)
            lateinit var model: DolphinViewModel
            fun main(action: ()->Unit)=scenario.onActivity { action() }
            scenario.onActivity { model=ViewModelProvider(it)[DolphinViewModel::class.java];model.select(MainTab.LIST);it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            await("主页未出现") { find("课表管理")!=null }
            capture("real-native-home.png")
            click("平铺")
            val date=model.selectedDate
            val display=context.resources.displayMetrics
            repeat(5) { touch(display.widthPixels*.88f,display.heightPixels*.43f,display.widthPixels*.18f,display.heightPixels*.43f,500) }
            await("平铺边缘未翻周") { model.selectedDate.isAfter(date) }
            assertNull("横滑不应误开编辑器",model.edit)
            capture("real-native-grid.png")
            click("搜索")
            await("原生搜索未出现") { find("课程、教师或教室")!=null }
            main { model.show(initial.schedule.courses.first()) }
            await("课程详情未出现") { find("编辑课程")!=null }
            main { model.detailId=null;model.select(MainTab.SETTINGS);model.open(NativePage.APPEARANCE) }
            await("外观页未出现") { find("深浅模式")!=null }
            capture("real-native-appearance.png")
            click("背景图片")
            await("背景页未出现") { find("背景预览")!=null }
            click(if(initial.background==null)"选择背景图片" else "更换背景图片")
            await("系统选图未打开") { instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString()?.contains("documentsui")==true }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            await("取消选图未返回") { !model.background.busy && instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString()==context.packageName }
            assertNull("取消选图应正常返回",model.background.error)
            capture("real-native-background.png")
            main { model.back();model.open(NativePage.SAFE_AREA) }
            await("安全区设置未出现") { find("上端自动留白")!=null }
            capture("real-native-safe-area.png")
            main { model.back();model.back();model.open(NativePage.BOOKS) }
            await("教材页未出现") { find("教材与封面保存在本机，同名课程共用教材。")!=null }
            main { model.back();model.open(NativePage.IMPORT) }
            await("教务导入入口未出现") { find("打开学校教务")!=null }
            scenario.recreate()
            await("页面重建后导入入口未恢复") { find("打开学校教务")!=null }
            val after=(repository.state.value as RepositoryState.Ready).snapshot.data
            assertTrue("课程应保持不变",initial.schedule==after.schedule)
            assertTrue("教材应保持不变",initial.books==after.books)
            assertTrue("取消选图应保留背景",initial.background==after.background)
            val report=org.json.JSONObject().put("status","passed").put("courses",initial.schedule.courses.size)
                .put("periods",initial.schedule.periods.size).put("weeks",initial.schedule.term.weeks)
                .put("migrationEqual",migrationEqual).put("updatedAfterMigration",snapshot.revision>1)
                .put("migrationBackupPresent",true).put("codecRoundTrip",true)
                .put("courseDataUnchanged",true).put("pickerCancelled",true)
            File(context.cacheDir,"real-device-preview-results.json").writeText(report.toString(2))
        }
    }
}
