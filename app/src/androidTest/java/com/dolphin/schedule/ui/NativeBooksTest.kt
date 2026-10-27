package com.dolphin.schedule.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.exifinterface.media.ExifInterface
import androidx.test.platform.app.InstrumentationRegistry
import com.dolphin.schedule.NativeActivity
import com.dolphin.schedule.JourneyState
import com.dolphin.schedule.data.*
import com.dolphin.schedule.domain.*
import com.dolphin.schedule.media.LocalImages
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.ExternalResource
import java.io.ByteArrayInputStream
import java.io.File
import java.time.LocalDate
import java.util.UUID

/** 图片、课表与教材全部为合成数据；执行前必须显式确认隔离模拟器。 */
class NativeBooksTest {
    @get:Rule(order=0) val emulatorOnly=object: ExternalResource() {
        override fun before() {
            check(InstrumentationRegistry.getArguments().getString("allowNativeDataTests")=="true")
            check(Build.HARDWARE in listOf("ranchu","goldfish") && Build.MODEL.startsWith("sdk_"))
        }
    }
    @get:Rule(order=1) val compose=createAndroidComposeRule<NativeActivity>()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var original: AppData
    private lateinit var repository: NativeRepository
    private lateinit var model: DolphinViewModel
    private val files=mutableListOf<File>()
    private val scopes=mutableListOf<CoroutineScope>()
    private val course=Course(id="book-course",name="大学英语（一）",teacher="合成教师",room="A101",day=1,start=1,end=2,weeks=(1..20).toList())
    private val idCourse=course.copy(id="native-id-book",name="设计实践",day=3)
    @Before fun prepare() {
        repository=NativeRepository.get(context)
        runBlocking {
            val initialized=repository.initialize() as RepositoryState.Ready;original=initialized.snapshot.data
            repository.update { AppData(onboarded=true,schedule=defaultSchedule().let { it.copy(term=it.term.copy(startDate=firstMonday(LocalDate.now().toString()).toString()),courses=listOf(course,course.copy(id="same-name",day=2),idCourse)) },books=mapOf(
                course.name to Book("大学英语教材","合成出版社","第 1 版"),idCourse.id to Book("设计教材"),"旧学期课程" to Book("历史教材"),
            ),settings=Preferences(mode="dark",dynamicColor=false)) }
        }
        compose.activityRule.scenario.onActivity { model=ViewModelProvider(it)[DolphinViewModel::class.java];model.select(MainTab.LIST) }
        compose.waitForIdle()
    }
    @After fun restore() {
        if(::model.isInitialized)compose.runOnUiThread { if(model.books.open && !model.books.busy)model.books.discardChanges() }
        scopes.forEach { scope -> runBlocking { scope.coroutineContext[Job]?.cancelAndJoin() } }
        files.forEach(File::delete)
        if(::original.isInitialized)runBlocking { repository.update { original } }
    }
    private fun current()=(repository.state.value as RepositoryState.Ready).snapshot.data
    private fun openEditor(c: Course=course) {
        compose.runOnUiThread { model.show(c) }
        compose.onNodeWithText(if(courseBook(current(),c)!=null)"编辑教材" else "填写教材与封面").performScrollTo().performClick()
        compose.waitUntil(5_000) { model.books.draft!=null && !model.books.busy }
    }
    private fun imageFile(width: Int=1600,height: Int=1200): File {
        val file=File(context.cacheDir,"captures/test-${UUID.randomUUID()}.png").also { it.parentFile!!.mkdirs();files+=it }
        val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawRect(width*.25f,height*.25f,width*.75f,height*.75f,android.graphics.Paint().apply { color=android.graphics.Color.CYAN })
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle();return file
    }

    @Test fun editingNameBoundBookSurvivesRecreationAndSharesAcrossCourses() {
        compose.runOnUiThread { model.show(course) }
        compose.onNodeWithText("大学英语教材").assertIsDisplayed()
        compose.onNodeWithText("编辑教材").performScrollTo().performClick()
        compose.waitUntil(5_000) { model.books.draft!=null && !model.books.busy }
        compose.onNodeWithTag("book-title").performTextReplacement("原生英语教材")
        compose.onNodeWithTag("book-edition").performTextReplacement("第 2 版")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("book-title").assertTextContains("原生英语教材")
        compose.runOnIdle { assertFalse(androidx.core.view.WindowCompat.getInsetsController(compose.activity.window,compose.activity.window.decorView).isAppearanceLightStatusBars) }
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("放弃教材修改？").assertIsDisplayed()
        compose.onNodeWithText("继续编辑").performClick()
        saveScreen("native-book-editor.png")
        compose.onNodeWithTag("book-save").performClick()
        compose.waitUntil(10_000) { !model.books.open && !model.books.busy }
        assertEquals("原生英语教材",courseBook(current(),course)?.title)
        assertEquals("原生英语教材",courseBook(current(),current().schedule.courses[1])?.title)
        assertEquals("第 2 版",courseBook(current(),course)?.edition)
        assertEquals("原生英语教材",JSONObject(JourneyState.preferences(context).getString("bookTitles","{}")!!).getString(course.name))
        val loaded=runBlocking { repository.initialize() } as RepositoryState.Ready
        assertEquals(current().books,loaded.snapshot.data.books)
    }

    @Test fun albumCoverIsCompressedLocallyAndDiscardNeverWritesIt() {
        openEditor()
        val before=current();val image=imageFile();val uri=FileProvider.getUriForFile(context,"${context.packageName}.files",image)
        compose.runOnUiThread { assertTrue(model.books.prepareAlbum());model.books.albumResult(uri) }
        compose.waitUntil(10_000) { !model.books.busy && model.books.draft?.value?.cover!=null }
        val cover=model.books.draft!!.value.cover!!
        val decoded=LocalImages.bitmap(cover,1200)!!
        assertEquals(1200,decoded.width);assertEquals(900,decoded.height)
        assertTrue(android.graphics.Color.red(decoded.getPixel(0,0))>245);decoded.recycle()
        assertEquals(before,current())
        compose.onNodeWithText("取消").performClick();compose.onNodeWithText("放弃修改").performClick()
        assertEquals(before,current())
        openEditor()
        compose.runOnUiThread { assertTrue(model.books.prepareAlbum());model.books.albumResult(uri) }
        compose.waitUntil(10_000) { !model.books.busy && model.books.draft?.value?.cover!=null }
        compose.onNodeWithTag("book-save").performClick()
        compose.waitUntil(10_000) { !model.books.open && !model.books.busy }
        assertEquals("album",current().books[course.name]?.source)
        assertNotNull(current().books[course.name]?.cover)
        // 行程只镜像教材标题；封面保存在教材本体，不再复制到通知偏好。
        assertFalse(JourneyState.preferences(context).contains("bookCovers"))
        val backup=AppDataCodec.decode(AppDataCodec.encode(current()));assertEquals(current().books,backup.books)
    }

    @Test fun processDraftUsesOnlySmallSavedStateAndStaleSaveKeepsUserText() {
        val handle=SavedStateHandle();val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate).also(scopes::add)
        lateinit var first: BookController
        compose.runOnUiThread { first=BookController(context,repository,scope,handle,{},{});first.start(current(),course) }
        compose.waitUntil(5_000) { first.draft!=null && !first.busy }
        val file=imageFile(300,400);val uri=FileProvider.getUriForFile(context,"${context.packageName}.files",file)
        compose.runOnUiThread { first.patch("title","待保存的新书名");first.prepareAlbum();first.albumResult(uri) }
        compose.waitUntil(5_000) { !first.busy && first.draft!!.value.cover!=null }
        compose.runOnUiThread { first.patch("text","封面之后继续输入的备注") }
        val stored=File(context.filesDir,"book-drafts/${first.draft!!.id}.json")
        assertTrue(stored.renameTo(File(stored.path+".bak"))) // 模拟旧版 AtomicFile 中断后的恢复路径。
        lateinit var restored: BookController
        compose.runOnUiThread {
            val values=handle.keys().associateWith { handle.get<Any?>(it) }
            assertTrue(values.toString().length<40_000);assertFalse(values.toString().contains("base64"))
            restored=BookController(context,repository,scope,SavedStateHandle(values),{}, {})
        }
        compose.waitUntil(5_000) { restored.draft!=null && !restored.busy }
        assertEquals(first.draft,restored.draft);assertTrue(restored.dirty)
        runBlocking { repository.update { it.copy(books=it.books+(course.name to it.books[course.name]!!.copy(edition="并发新版本"))) } }
        val before=current()
        compose.runOnUiThread { restored.save() }
        compose.waitUntil(5_000) { !restored.busy && restored.error!=null }
        assertEquals(before,current());assertEquals("待保存的新书名",restored.draft!!.value.title)
        compose.runOnUiThread { restored.discardChanges();first.discardChanges() }
    }

    @Test fun libraryKeepsOrphansAndIdFallbackCanBeEditedAndRemoved() {
        compose.runOnUiThread { model.open(NativePage.BOOKS) }
        compose.onNodeWithTag("book-library").performScrollToNode(hasText("历史教材"))
        compose.onNodeWithText("历史教材").performClick()
        compose.waitUntil(5_000) { model.books.draft!=null && !model.books.busy }
        compose.onNodeWithTag("book-title").performTextReplacement("历史教材修订")
        compose.onNodeWithTag("book-save").performClick()
        compose.waitUntil(5_000) { !model.books.open && !model.books.busy }
        compose.onNodeWithTag("book-library").performScrollToNode(hasText("历史教材修订"))
        compose.onNodeWithText("历史教材修订").assertIsDisplayed()
        openEditor(idCourse)
        assertEquals(idCourse.id,model.books.draft!!.key)
        compose.onNodeWithText("移除教材").performScrollTo().performClick()
        compose.onNodeWithText("确认移除").performClick()
        compose.waitUntil(5_000) { !model.books.open && !model.books.busy }
        assertFalse(current().books.containsKey(idCourse.id));assertNotNull(current().books["旧学期课程"])
        assertEquals(3,current().schedule.courses.size)
        assertFalse(JSONObject(JourneyState.preferences(context).getString("bookTitles","{}")!!).has(idCourse.name))
    }

    @Test fun cameraOutputContractIsScopedAndCancellationOrCorruptionKeepsBook() {
        openEditor();val before=current()
        lateinit var uri: android.net.Uri
        compose.runOnUiThread { uri=model.books.prepareCamera()!! }
        val intent=BookCameraContract().createIntent(context,uri)
        assertEquals(uri,intent.clipData!!.getItemAt(0).uri)
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,intent.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
        compose.runOnUiThread { model.books.cameraResult(false) };assertEquals(before,current());assertFalse(model.books.dirty)
        compose.runOnUiThread { uri=model.books.prepareCamera()!! }
        context.contentResolver.openOutputStream(uri)!!.use { it.write("invalid image".toByteArray()) }
        compose.runOnUiThread { model.books.cameraResult(true) }
        compose.waitUntil(5_000) { !model.books.busy && model.books.error!=null }
        assertEquals(before,current());assertNull(model.books.draft!!.value.cover)
        compose.runOnUiThread { uri=model.books.prepareCamera()!! }
        val image=imageFile(640,480)
        context.contentResolver.openOutputStream(uri)!!.use { output -> image.inputStream().use { it.copyTo(output) } }
        compose.runOnUiThread { model.books.cameraResult(true) }
        compose.waitUntil(5_000) { !model.books.busy && model.books.draft!!.value.cover!=null }
        assertEquals("camera",model.books.draft!!.value.source);assertEquals(before,current())
        assertThrows(IllegalArgumentException::class.java) { LocalImages.cover(ByteArrayInputStream(ByteArray(0))) }
        assertNull(LocalImages.bitmap("https://example.invalid/cover.jpg"))
        compose.runOnUiThread { model.books.removeCover() }
        compose.waitUntil(5_000) { !model.books.busy };assertNull(model.books.draft!!.value.cover)
        compose.runOnUiThread { uri=model.books.prepareCamera()!!;model.books.discardChanges() }
        compose.waitUntil(5_000) { !File(context.cacheDir,"captures/${uri.lastPathSegment}").exists() }
    }
    @Test fun cameraExifOrientationWorksInModernAndAndroid8Decoder() {
        val file=File(context.cacheDir,"captures/exif-${UUID.randomUUID()}.jpg").also { it.parentFile!!.mkdirs();files+=it }
        val raw=Bitmap.createBitmap(40,20,Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.RED) }
        file.outputStream().use { raw.compress(Bitmap.CompressFormat.JPEG,95,it) };raw.recycle()
        for(orientation in listOf(2,3,4,5,6,7,8)) {
            ExifInterface(file).apply { setAttribute(ExifInterface.TAG_ORIENTATION,orientation.toString());saveAttributes() }
            val bytes=file.readBytes()
            val legacy=LocalImages.legacyBitmap(bytes,1200)
            assertEquals(if(orientation>=5)20 else 40,legacy.width);assertEquals(if(orientation>=5)40 else 20,legacy.height);legacy.recycle()
            val encoded=file.inputStream().use(LocalImages::cover)
            val modern=LocalImages.bitmap(encoded,1200)!!
            assertEquals(if(orientation>=5)20 else 40,modern.width);assertEquals(if(orientation>=5)40 else 20,modern.height);modern.recycle()
        }
    }
    @Test fun actualSystemCameraCancellationReturnsToUnchangedDraft() {
        openEditor();val before=current()
        compose.onNodeWithText("拍摄封面").performScrollTo().performClick()
        val instrumentation=InstrumentationRegistry.getInstrumentation();val automation=instrumentation.uiAutomation
        compose.waitUntil(10_000) { automation.rootInActiveWindow?.packageName?.toString()=="com.android.camera2" }
        val tree=StringBuilder()
        fun inspect(node: android.view.accessibility.AccessibilityNodeInfo?,depth: Int=0) {
            if(node==null)return
            tree.append(" ".repeat(depth)).append(node.viewIdResourceName).append(" | ").append(node.text).append(" | ").append(node.contentDescription).append('\n')
            for(i in 0 until node.childCount)inspect(node.getChild(i),depth+1)
        }
        inspect(automation.rootInActiveWindow)
        File(context.cacheDir,"native-camera-ui.txt").writeText(tree.toString())
        automation.takeScreenshot()?.let { bitmap -> File(context.cacheDir,"native-system-camera.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle() }
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitUntil(10_000) { automation.rootInActiveWindow?.packageName?.toString()==context.packageName }
        compose.onNodeWithTag("book-editor").assertIsDisplayed()
        assertEquals(before,current());assertNull(model.books.draft!!.value.cover)
    }
    @Test fun actualSystemCameraPhotoReturnsAsUnsavedCoverThenSavesLocally() {
        openEditor();val before=current()
        val previousCaptures=File(context.cacheDir,"captures").listFiles().orEmpty().map { it.name }.toSet()
        compose.onNodeWithText("拍摄封面").performScrollTo().performClick()
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        fun find(node: android.view.accessibility.AccessibilityNodeInfo?,match: (android.view.accessibility.AccessibilityNodeInfo)->Boolean): android.view.accessibility.AccessibilityNodeInfo? {
            if(node==null)return null;if(match(node))return node
            for(i in 0 until node.childCount)find(node.getChild(i),match)?.let { return it };return null
        }
        compose.waitUntil(10_000) { find(automation.rootInActiveWindow) { it.viewIdResourceName=="com.android.camera2:id/shutter_button" && it.isEnabled }!=null }
        var nextAttempt=android.os.SystemClock.elapsedRealtime()+800
        compose.waitUntil(15_000) {
            if(find(automation.rootInActiveWindow) { it.contentDescription?.toString()=="Done" }!=null)true
            else {
                // 相机界面先于虚拟摄像头预览就绪；仅在仍显示快门时重试同一次受控拍摄。
                if(android.os.SystemClock.elapsedRealtime()>=nextAttempt) {
                    find(automation.rootInActiveWindow) { it.viewIdResourceName=="com.android.camera2:id/shutter_button" && it.isEnabled }?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                    nextAttempt=android.os.SystemClock.elapsedRealtime()+2000
                }
                false
            }
        }
        assertTrue(find(automation.rootInActiveWindow) { it.contentDescription?.toString()=="Done" }!!.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
        compose.waitUntil(15_000) { model.books.draft?.value?.cover!=null && !model.books.busy }
        assertEquals(before,current());assertEquals("camera",model.books.draft!!.value.source)
        compose.onNodeWithTag("book-cover-preview").performScrollTo()
        saveScreen("native-book-camera-cover.png")
        compose.onNodeWithTag("book-save").performClick()
        compose.waitUntil(10_000) { !model.books.open && !model.books.busy }
        assertNotNull(current().books[course.name]?.cover);assertEquals("camera",current().books[course.name]?.source)
        assertTrue(File(context.cacheDir,"captures").listFiles().orEmpty().none { it.name.startsWith("book-") && it.name !in previousCaptures })
    }
    private fun saveScreen(name: String) {
        compose.mainClock.advanceTimeBy(500);compose.waitForIdle();android.os.SystemClock.sleep(200)
        val bitmap=requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        File(context.cacheDir,name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
    }
}
