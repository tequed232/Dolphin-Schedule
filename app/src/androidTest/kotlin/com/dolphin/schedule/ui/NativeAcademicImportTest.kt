package com.dolphin.schedule.ui

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.dolphin.schedule.NativeActivity
import com.dolphin.schedule.data.NativeRepository
import com.dolphin.schedule.data.RepositoryState
import com.dolphin.schedule.domain.*
import com.dolphin.schedule.ui.MainTab
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.ExternalResource

/** 使用合成的网页提取结果验证原生流程，不冒充学校实际联网登录测试。 */
class NativeAcademicImportTest {
    @get:Rule(order=0) val emulatorOnly=object: ExternalResource() {
        override fun before() {
            check(InstrumentationRegistry.getArguments().getString("allowNativeDataTests")=="true")
            check(Build.HARDWARE in listOf("ranchu","goldfish") && Build.MODEL.startsWith("sdk_")) { "禁止在用户真机写入合成课表" }
        }
    }
    @get:Rule(order=1) val compose=createAndroidComposeRule<NativeActivity>()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var repository: NativeRepository
    private lateinit var model: DolphinViewModel
    private lateinit var original: AppData
    private lateinit var baseline: AppData

    @Before fun prepare() {
        repository=NativeRepository.get(context)
        runBlocking {
            original=(repository.initialize() as RepositoryState.Ready).snapshot.data
            val schedule=defaultSchedule().let { it.copy(term=it.term.copy(startDate="2026-09-07"),courses=listOf(Course(id="before-academic",name="原有手动课程",day=1,start=1,end=2,weeks=listOf(1,2)))) }
            baseline=AppData(schedule=schedule,books=mapOf("before-academic" to Book(title="保留的教材",cover="data:image/png;base64,AA==")),settings=Preferences(mode="light",dynamicColor=false,school="合成学校"),onboarded=true)
            repository.update { baseline }
        }
        compose.activityRule.scenario.onActivity { model=ViewModelProvider(it)[DolphinViewModel::class.java];model.open(NativePage.IMPORT) }
        compose.waitUntil(5_000) { !model.busy && !model.academicBusy }
    }
    @After fun restoreEmulator() {
        if(::model.isInitialized)compose.runOnUiThread { model.confirmDiscardAcademic() }
        if(::original.isInitialized)runBlocking { repository.update { original } }
    }
    private fun snapshot(date: Boolean=false): String {
        val periods=defaultSchedule().periods.toMutableList().apply { add(4,Period("11:50","12:35","中午1"));add(5,Period("12:45","13:30","中午2")) }
        var ordinary=0
        val rows=JSONArray(periods.mapIndexed { index,p -> JSONObject().put("label",p.label ?: (++ordinary).toString()).put("start",p.start).put("end",p.end)
            .put("cells",JSONArray((0..6).map { day -> JSONArray(if(day==0 && index in 0..1)listOf(JSONObject().put("name","教务合成课程").put("teacher","合成教师").put("room","A101").put("weeks","1-21")) else if(day==1 && index==4)listOf(JSONObject().put("name","午间实践").put("teacher","合成教师").put("room","--").put("weeks","1-21双")) else emptyList<JSONObject>()) })) })
        return JSONObject().put("schema",1).put("school","gdcmxy").put("term","2026-2027-1").put("startWeek",1).put("endWeek",21).put("rows",rows).also { if(date)it.put("termStartDate","2026-09-01") }.toString()
    }
    private fun deliver(raw: String): String {
        var id=""
        compose.runOnUiThread { id=requireNotNull(model.prepareAcademicRequest()) }
        File(context.cacheDir,"academic-$id.json").writeText(raw)
        compose.runOnUiThread { model.academicResult(Activity.RESULT_OK,Intent().putExtra("requestId",id)) }
        compose.waitUntil(10_000) { !model.academicBusy }
        return id
    }
    private fun scrollTo(text: String) { compose.onNodeWithTag("academic-import-list").performScrollToNode(hasText(text,substring=false)) }
    private fun data()=(repository.state.value as RepositoryState.Ready).snapshot.data

    @Test fun missingDateComesFirstThenAcknowledgedImportAndUndoKeepBooksAndPreferences() {
        val id=deliver(snapshot())
        compose.onNodeWithText("先设置开学日期").assertIsDisplayed()
        compose.onNodeWithText("确认导入").assertDoesNotExist()
        assertEquals(baseline,data())
        compose.onNodeWithText("确认日期并预览").performClick()
        compose.waitUntil(5_000) { model.academicPreview!=null && !model.academicBusy }
        assertEquals(14,model.academicPreview!!.periods.size)
        assertEquals(listOf("中午1","中午2","5"),model.academicPreview!!.periods.subList(4,7).map { it.label })
        assertEquals("2026-09-07",model.academicPreview!!.term.startDate)

        val fresh=DolphinViewModel(context.applicationContext as Application,SavedStateHandle(mapOf("academicDraft" to id,"pages" to arrayListOf("IMPORT"))))
        val store=ViewModelStore().apply { put("restored",fresh) }
        try { compose.waitUntil(5_000) { !fresh.academicBusy };assertEquals(model.academicDraft,fresh.academicDraft);assertEquals(model.academicPreview,fresh.academicPreview);assertFalse(fresh.academicAcknowledged) } finally { store.clear() }

        scrollTo("确认导入");compose.onNodeWithText("确认导入").assertIsNotEnabled()
        compose.runOnUiThread { model.applyAcademicImport() }
        assertEquals(baseline,data())
        val acknowledgment="我已核对日期、课程与作息，同意替换当前课表。教材和外观保留。"
        scrollTo(acknowledgment);compose.onNodeWithText(acknowledgment).performClick()
        assertTrue(model.academicAcknowledged)
        saveScreen("academic-native-preview.png")
        scrollTo("确认导入");compose.onNodeWithText("确认导入").performClick()
        compose.waitUntil(10_000) { data().academicImport!=null && !model.busy }
        assertEquals(2,data().schedule.courses.size);assertEquals(14,data().schedule.periods.size)
        assertEquals(baseline.books,data().books);assertEquals(baseline.settings,data().settings)
        assertEquals(baseline.schedule,data().academicImport!!.before)
        assertNull(model.academicDraft)
        compose.onNodeWithContentDescription("设置").performClick()
        assertEquals(MainTab.SETTINGS,model.tab)
        compose.onNodeWithText("导入课表").performClick()
        scrollTo("撤销上次教务导入");compose.onNodeWithText("撤销上次教务导入").performClick()
        compose.onNodeWithText("确认恢复课表").performClick()
        compose.waitUntil(10_000) { data().academicImport==null && !model.busy }
        assertEquals(baseline.schedule,data().schedule);assertEquals(baseline.books,data().books);assertEquals(baseline.settings,data().settings)
    }
    @Test fun changedScheduleBlocksSaveAndBackKeepsRecognizedDraftUntilExplicitDiscard() {
        deliver(snapshot(date=true))
        assertNotNull(model.academicPreview)
        compose.runOnUiThread { model.acknowledgeAcademic(true);model.updateAcademicDate("2026-09-07") }
        compose.waitUntil(5_000) { !model.academicBusy }
        assertFalse(model.academicAcknowledged)
        assertEquals("2026-09-07",model.academicPreview!!.term.startDate)
        runBlocking { repository.update { it.copy(schedule=it.schedule.copy(term=it.schedule.term.copy(name="其他窗口已更新"))) } }
        val changed=data()
        compose.runOnUiThread { model.acknowledgeAcademic(true);model.applyAcademicImport() }
        compose.waitUntil(5_000) { !model.busy }
        assertEquals(changed,data());assertNotNull(model.academicDraft)
        assertNotNull(model.error)
        compose.onNodeWithText("知道了").performClick()
        compose.runOnUiThread { model.back() }
        compose.onNodeWithText("放弃这次识别？").assertIsDisplayed()
        compose.onNodeWithText("继续核对").performClick()
        assertNotNull(model.academicDraft)
        compose.runOnUiThread { model.back() }
        compose.onNodeWithText("放弃识别").performClick()
        assertNull(model.academicDraft);assertEquals(changed,data())
    }
    @Test fun invalidOrEmptyResultAndUnmatchedRequestsNeverTouchSavedSchedule() {
        val raw=JSONObject(snapshot()).put("rows",JSONArray()).toString()
        deliver(raw)
        assertEquals(baseline,data());assertNull(model.academicDraft);assertNotNull(model.academicError)
        compose.runOnUiThread {
            val id=requireNotNull(model.prepareAcademicRequest())
            model.academicResult(Activity.RESULT_OK,Intent().putExtra("requestId",id.reversed()))
        }
        compose.waitUntil(5_000) { !model.academicBusy }
        assertEquals(baseline,data());assertNull(model.academicDraft)
    }
    @Test fun sessionClearCallbackDoesNotCreateAnEmptyCourseImport() {
        compose.runOnUiThread {
            val id=requireNotNull(model.prepareAcademicRequest(clear=true))
            model.academicResult(Activity.RESULT_OK,Intent().putExtra("requestId",id).putExtra("cleared",true))
        }
        compose.waitUntil(5_000) { !model.academicBusy }
        assertEquals(baseline,data());assertNull(model.academicDraft);assertNull(model.academicPending);assertNull(model.academicError)
    }
    private fun saveScreen(name: String) {
        compose.mainClock.advanceTimeBy(600);compose.waitForIdle();android.os.SystemClock.sleep(350)
        val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
        File(context.cacheDir,name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
    }
}
