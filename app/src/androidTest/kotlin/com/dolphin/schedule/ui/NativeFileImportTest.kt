package com.dolphin.schedule.ui

import android.app.Application
import android.os.Build
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.dolphin.schedule.NativeActivity
import com.dolphin.schedule.data.NativeRepository
import com.dolphin.schedule.data.RepositoryState
import com.dolphin.schedule.domain.*
import com.dolphin.schedule.importing.*
import java.io.File
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.ExternalResource

class NativeFileImportTest {
    @get:Rule(order=0) val emulatorOnly=object: ExternalResource() { override fun before() {
        check(InstrumentationRegistry.getArguments().getString("allowNativeDataTests")=="true")
        check(Build.HARDWARE in listOf("ranchu","goldfish") && Build.MODEL.startsWith("sdk_")) { "禁止在用户真机写入合成课表" }
    } }
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
            baseline=AppData(schedule=defaultSchedule().copy(courses=listOf(Course(id="original-id",name="原有课程",day=1,start=1,end=2,weeks=listOf(1,3,7)))),books=mapOf("original-id" to Book(title="保留教材")),onboarded=true,settings=Preferences(dynamicColor=false))
            repository.update { baseline }
        }
        compose.activityRule.scenario.onActivity { model=ViewModelProvider(it)[DolphinViewModel::class.java];model.open(NativePage.FILE_IMPORT) }
        compose.waitUntil(5_000) { !model.files.busy }
    }
    @After fun restore() { if(::model.isInitialized)compose.runOnUiThread { model.files.confirmDiscard() };if(::original.isInitialized)runBlocking { repository.update { original } } }
    private fun data()=(repository.state.value as RepositoryState.Ready).snapshot.data
    private fun waitForRead() { compose.waitUntil(20_000) { !model.files.busy };assertNull(model.files.error) }
    private fun load(name: String,bytes: ByteArray) { compose.runOnUiThread { model.files.selectBytes(name,bytes) };waitForRead() }
    private fun scrollTo(text: String) { compose.onNodeWithTag("file-import-list").performScrollToNode(hasText(text)) }
    private fun fixture(extension: String)=InstrumentationRegistry.getInstrumentation().context.assets.open("file-import/synthetic.$extension").use { it.readBytes() }

    @Test fun xlsAndXlsxDecodeMergedUnicodeCellsThenKotlinParsesCoursesAndErrors()=runBlocking {
        for(extension in listOf("xls","xlsx")) {
            val sheets=SpreadsheetDecoder.decode(context,fixture(extension))
            assertEquals(listOf("课程明细","合并网格","作息","分块说明"),sheets.map { it.name })
            assertEquals(500,sheets.last().rows.size)
            val rows=TableImport.parseSheet(sheets.first(),baseline.schedule)
            assertEquals(1,rows.errors.size);assertEquals("合成数学",rows.schedule.courses.single().name)
            val grid=TableImport.parseSheet(sheets[1],baseline.schedule)
            assertEquals(2,grid.schedule.courses.size);assertEquals(2,grid.schedule.courses.first { it.name=="设计实践" }.end)
            assertEquals("合成导师",grid.schedule.courses.first { it.name=="设计实践" }.teacher)
        }
    }
    @Test fun nativeCsvPreviewNeedsConfirmationAndKeepsBooksAfterSave() {
        load("课程.csv","课程名,星期,节次,周次\n原生导入课,周三,3-4,1-16单".toByteArray())
        assertEquals(baseline,data())
        scrollTo("确认导入");compose.onNodeWithText("确认导入").assertIsNotEnabled()
        val ack="已核对日期和内容，确认保存。教材、背景和偏好保留。"
        scrollTo(ack);compose.onNodeWithText(ack).performClick()
        scrollTo("确认导入");compose.onNodeWithText("确认导入").performClick()
        compose.waitUntil(10_000) { !model.files.saving && data().schedule.courses.singleOrNull()?.name=="原生导入课" }
        assertEquals(baseline.books,data().books);assertEquals((1..16).filter { it%2==1 },data().schedule.courses.single().weeks)
    }
    @Test fun workbookSelectionAndManualMappingSurviveStaleRepreviewAndFreshControllerRestore() {
        load("工作簿.xlsx",fixture("xlsx"))
        assertEquals(4,model.files.source!!.sheets.size)
        compose.runOnUiThread { model.files.selectSheet(1) };waitForRead()
        val mapping=SheetMapping(1,0,2,4,1,2)
        compose.runOnUiThread { model.files.analyzeMapping(mapping) };waitForRead()
        compose.runOnUiThread { model.files.showPreview();model.files.acknowledge(true) }
        runBlocking { repository.update { it.copy(schedule=it.schedule.copy(term=it.schedule.term.copy(name="其他窗口更新"))) } }
        val changed=data()
        compose.runOnUiThread { model.files.commit() }
        compose.waitUntil(5_000) { !model.files.saving }
        assertEquals(changed,data());assertNotNull(model.files.error)
        compose.runOnUiThread { model.files.repreview() };waitForRead()
        assertEquals(1,model.files.index);assertEquals(mapping,model.files.appliedMapping);assertEquals(changed.schedule,model.files.baseline);assertFalse(model.files.acknowledged)
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        val fresh=FileImportController(context,repository,scope,SavedStateHandle(mapOf("fileDraft" to model.files.id)),{},{},{})
        try { compose.waitUntil(20_000) { !fresh.busy };assertNull(fresh.error);assertEquals(1,fresh.index);assertEquals(mapping,fresh.appliedMapping);assertEquals(changed.schedule,fresh.baseline);assertEquals(model.files.preview!!.schedule.courses.map { it.name },fresh.preview!!.schedule.courses.map { it.name });assertFalse(fresh.acknowledged) } finally { scope.cancel() }
        compose.runOnUiThread { model.files.showPreview() }
        compose.onNodeWithText("Excel · 合并网格").assertIsDisplayed()
        saveScreen("file-native-preview.png")
    }
    @Test fun timeOnlyWorkbookPreservesIdsAndBackDoesNotSilentlyDiscard() {
        load("作息.xlsx",fixture("xlsx"))
        compose.runOnUiThread { model.files.selectSheet(2) };waitForRead()
        assertTrue(model.files.preview!!.onlyConfiguration)
        assertEquals(baseline.schedule.courses,model.files.preview!!.schedule.courses)
        assertEquals(3,model.files.preview!!.schedule.periods.size)
        compose.runOnUiThread { model.back() }
        compose.onNodeWithText("放弃此次导入？").assertIsDisplayed()
        compose.onNodeWithText("继续核对").performClick();assertNotNull(model.files.preview)
        compose.runOnUiThread { model.files.showPreview();model.files.acknowledge(true);model.files.commit() }
        compose.waitUntil(10_000) { !model.files.saving && data().schedule.periods.size==3 }
        assertEquals(baseline.schedule.courses,data().schedule.courses)
    }
    @Test fun badExtensionRequiresFormatOverrideAndPartialRowsNeedExplicitAcceptance() {
        compose.runOnUiThread { model.files.selectBytes("课程.json","课程名,星期,节次\n有效课,周一,1\n错误课,周八,2".toByteArray()) }
        compose.waitUntil(5_000) { !model.files.busy }
        assertTrue(model.files.formatIssue);assertEquals(ImportFormat.CSV,model.files.suggested)
        compose.runOnUiThread { model.files.overrideFormat(ImportFormat.CSV) };waitForRead()
        assertEquals(1,model.files.preview!!.errors.size);assertEquals(baseline,data())
        compose.runOnUiThread { model.files.showPreview();model.files.commit() };assertEquals(baseline,data())
        val ack="已核对错误明细，同意跳过这些项目并保存其余内容。"
        scrollTo(ack);compose.onNodeWithText(ack).performClick();scrollTo("确认导入");compose.onNodeWithText("确认导入").performClick()
        compose.waitUntil(10_000) { !model.files.saving && data().schedule.courses.singleOrNull()?.name=="有效课" }
    }
    @Test fun androidContentUriSelectionCopiesTheSourceAndExplicitCancelKeepsBothOriginals() {
        val folder=File(context.cacheDir,"captures").apply { mkdirs() }
        val selected=File(folder,"native-file-import-test.csv").apply { writeText("课程名,星期,节次\n内容选择器课程,周一,1") }
        try {
            val uri=FileProvider.getUriForFile(context,"${context.packageName}.files",selected)
            compose.runOnUiThread { model.files.select(uri) };waitForRead()
            assertEquals(selected.name,model.files.name);assertEquals("内容选择器课程",model.files.preview!!.schedule.courses.single().name)
            assertEquals(baseline,data())
            compose.runOnUiThread { model.back() }
            compose.onNodeWithText("放弃导入").performClick()
            assertFalse(model.files.dirty);assertEquals(baseline,data());assertTrue(selected.isFile)
        } finally { selected.delete() }
    }
    private fun saveScreen(name: String) {
        compose.mainClock.advanceTimeBy(600);compose.waitForIdle();android.os.SystemClock.sleep(350)
        val bitmap=compose.onRoot().captureToImage().asAndroidBitmap();File(context.cacheDir,name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
    }
}
