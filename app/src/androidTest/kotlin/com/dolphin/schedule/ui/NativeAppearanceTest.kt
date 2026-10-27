package com.dolphin.schedule.ui

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.KeyEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.dolphin.schedule.NativeActivity
import com.dolphin.schedule.data.*
import com.dolphin.schedule.domain.*
import com.dolphin.schedule.media.LocalImages
import com.dolphin.schedule.ui.appearance.BackgroundController
import com.dolphin.schedule.ui.appearance.decodeSavedBackground
import com.dolphin.schedule.ui.MainTab
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.ExternalResource

/** 外观与选图全部使用运行时合成图片；只允许显式指定的隔离模拟器。 */
class NativeAppearanceTest {
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
    private val media=mutableListOf<Uri>()
    @Before fun prepare() {
        repository=NativeRepository.get(context)
        runBlocking {
            original=(repository.initialize() as RepositoryState.Ready).snapshot.data
            val tomorrow=LocalDate.now().plusDays(1)
            repository.update { AppData(onboarded=true,settings=Preferences(mode="dark",dynamicColor=false),books=mapOf("背景验收课" to Book("合成教材")),schedule=defaultSchedule().let { s -> s.copy(term=s.term.copy(startDate=firstMonday(tomorrow.toString()).toString()),courses=listOf(Course(id="appearance-course",name="背景验收课",day=tomorrow.dayOfWeek.value,start=1,end=2,weeks=(1..20).toList(),room="合成教室"))) }) }
        }
        compose.activityRule.scenario.onActivity { model=ViewModelProvider(it)[DolphinViewModel::class.java];model.selectDate(LocalDate.now().plusDays(1));model.select(MainTab.LIST) }
        compose.waitForIdle()
    }
    @After fun restore() {
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        if(automation.rootInActiveWindow?.packageName?.toString()?.contains("documentsui")==true)InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        if(::model.isInitialized)compose.waitUntil(15_000) { !model.background.busy && !model.busy }
        scopes.forEach { scope -> runBlocking { scope.coroutineContext[Job]?.cancelAndJoin() } }
        files.forEach(File::delete)
        media.forEach { context.contentResolver.delete(it,null,null) }
        if(::original.isInitialized)runBlocking { repository.update { original } }
    }
    private fun data()=(repository.state.value as RepositoryState.Ready).snapshot.data
    private fun image(width: Int=1200,height: Int=2400): File {
        val file=File(context.cacheDir,"captures/background-${UUID.randomUUID()}.png").also { it.parentFile!!.mkdirs();files+=it }
        val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap);val paint=Paint()
        paint.color=android.graphics.Color.rgb(40,80,120);canvas.drawRect(0f,0f,width.toFloat(),height.toFloat(),paint)
        paint.color=android.graphics.Color.rgb(100,175,155);canvas.drawRect(width*.35f,0f,width*.65f,height.toFloat(),paint)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle();return file
    }
    private fun uri(file: File)=FileProvider.getUriForFile(context,"${context.packageName}.files",file)
    private fun choose(file: File) {
        var launched=false
        compose.runOnUiThread { model.background.preparePicker { launched=true } }
        compose.waitUntil(5_000) { launched }
        compose.runOnUiThread { model.background.selected(uri(file)) }
        compose.waitUntil(15_000) { !model.background.busy }
    }
    private fun openBackground() { compose.runOnUiThread { model.open(NativePage.BACKGROUND) };compose.onNodeWithTag("background-settings").assertIsDisplayed() }

    @Test fun backgroundUploadBlurPreviewAndThemeSwitchPersistWithoutTouchingCourses() {
        val before=data();openBackground();choose(image())
        assertNull(model.background.error)
        assertEquals(960,data().background?.width);assertEquals(1920,data().background?.height)
        assertEquals(1200,data().background?.sourceWidth);assertEquals(2400,data().background?.sourceHeight)
        assertEquals(before.schedule,data().schedule);assertEquals(before.books,data().books)
        val sharp=decodeSavedBackground(data().background!!.url,0)!!
        val softened=decodeSavedBackground(data().background!!.url,19)!!
        assertTrue(android.graphics.Color.red(softened.getPixel((softened.width*.35f).toInt()-2,softened.height/2))>android.graphics.Color.red(sharp.getPixel((sharp.width*.35f).toInt()-2,sharp.height/2))+5)
        sharp.recycle();softened.recycle()
        compose.onNodeWithTag("background-settings").performScrollToIndex(1)
        compose.onNodeWithTag("background-blur").performTouchInput { down(Offset(width*.25f,height*.5f));moveTo(Offset(width*.65f,height*.5f),200) }
        compose.waitUntil(5_000) { model.background.previewBlur!=null }
        assertEquals(0,data().settings.backgroundBlur)
        val selected=model.background.previewBlur!!
        compose.onNodeWithTag("background-blur").performTouchInput { up() }
        compose.waitUntil(5_000) { data().settings.backgroundBlur==selected && !model.background.busy }
        compose.onNodeWithTag("background-settings").performScrollToIndex(0)
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("background-preview-image").fetchSemanticsNodes().isNotEmpty() }
        saveScreen("native-background-dark.png")
        val saved=data().background
        compose.onNodeWithText("使用主题底色").performClick()
        compose.waitUntil(5_000) { !data().settings.backgroundEnabled && !model.background.busy }
        assertEquals(saved,data().background);compose.onNodeWithTag("saved-background").assertDoesNotExist()
        compose.onNodeWithText("启用这张图片").performClick()
        compose.waitUntil(5_000) { data().settings.backgroundEnabled && !model.background.busy }
        compose.activityRule.scenario.recreate()
        assertEquals(saved,data().background);assertEquals(selected,data().settings.backgroundBlur)
        assertEquals(data(),AppDataCodec.decode(AppDataCodec.encode(data())))
        compose.onNodeWithText("移除图片").performClick();compose.onNodeWithText("确认移除").performClick()
        compose.waitUntil(5_000) { data().background==null && !model.background.busy }
        assertEquals(before.schedule,data().schedule);assertEquals(before.books,data().books)
    }

    @Test fun systemPickerCancellationKeepsOriginalBackgroundAndPage() {
        openBackground();val before=data()
        compose.onNodeWithTag("background-picker").performClick()
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        compose.waitUntil(10_000) { automation.rootInActiveWindow?.packageName?.toString()?.contains("documentsui")==true }
        val tree=StringBuilder()
        fun inspect(node: android.view.accessibility.AccessibilityNodeInfo?,depth: Int=0) {
            if(node==null)return
            tree.append(" ".repeat(depth)).append(node.viewIdResourceName).append(" | ").append(node.text).append(" | ").append(node.contentDescription).append('\n')
            for(i in 0 until node.childCount)inspect(node.getChild(i),depth+1)
        }
        inspect(automation.rootInActiveWindow);File(context.cacheDir,"background-picker-ui.txt").writeText(tree.toString())
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(10_000) { !model.background.busy }
        assertEquals(before,data());compose.onNodeWithTag("background-settings").assertIsDisplayed()
    }

    @Test fun actualSystemPickerImportsOnlyChosenSyntheticPhoto() {
        val name="native-background-${UUID.randomUUID()}.png"
        val values=ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME,name);put(MediaStore.Images.Media.MIME_TYPE,"image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/DolphinNativeTests");put(MediaStore.Images.Media.IS_PENDING,1)
        }
        val content=requireNotNull(context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values));media+=content
        context.contentResolver.openOutputStream(content)!!.use { output -> image(400,800).inputStream().use { it.copyTo(output) } }
        context.contentResolver.update(content,ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING,0) },null,null)
        openBackground();val before=data()
        compose.onNodeWithTag("background-picker").performClick()
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        fun find(node: android.view.accessibility.AccessibilityNodeInfo?): android.view.accessibility.AccessibilityNodeInfo? {
            if(node==null)return null
            if(node.contentDescription?.toString()?.startsWith("$name,")==true)return node
            for(i in 0 until node.childCount)find(node.getChild(i))?.let { return it };return null
        }
        compose.waitUntil(15_000) { find(automation.rootInActiveWindow)!=null }
        assertTrue(find(automation.rootInActiveWindow)!!.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
        compose.waitUntil(15_000) { !model.background.busy && data().background?.name==name }
        assertNull(model.background.error);assertTrue(data().settings.backgroundEnabled)
        assertEquals(before.schedule,data().schedule);assertEquals(before.books,data().books)
        assertEquals(400,data().background?.sourceWidth);assertEquals(800,data().background?.sourceHeight)
    }

    @Test fun pendingPickerSurvivesSmallSavedStateAndRejectsNewerImage() {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate).also(scopes::add);val saved=SavedStateHandle()
        lateinit var first: BackgroundController;var launched=false
        compose.runOnUiThread { first=BackgroundController(context,repository,scope,saved,{});first.preparePicker { launched=true } }
        compose.waitUntil(5_000) { launched }
        val replacement=image(400,600).inputStream().use { LocalImages.background(it,"较新的背景") }
        runBlocking { repository.update { it.copy(background=replacement,settings=it.settings.copy(backgroundEnabled=true,school="并发学校")) } }
        lateinit var restored: BackgroundController
        compose.runOnUiThread {
            val values=saved.keys().associateWith { saved.get<Any?>(it) };assertTrue(values.toString().length<200);assertFalse(values.toString().contains("base64"))
            restored=BackgroundController(context,repository,scope,SavedStateHandle(values),{})
            restored.selected(uri(image(300,500)))
        }
        compose.waitUntil(15_000) { !restored.busy }
        assertNotNull(restored.error);assertEquals(replacement,data().background);assertEquals("并发学校",data().settings.school)
        compose.runOnUiThread { first.selected(null) }
    }

    @Test fun invalidImagesAndBoundsDoNotReplaceSavedBackground() {
        openBackground();choose(image(200,400));val before=data()
        val invalid=File(context.cacheDir,"captures/invalid-${UUID.randomUUID()}.png").also { it.writeText("broken image");files+=it }
        choose(invalid);assertNotNull(model.background.error);assertEquals(before,data())
        assertThrows(IllegalArgumentException::class.java) { LocalImages.background(ByteArrayInputStream(ByteArray(0)),"空文件") }
        val tooLarge=object: InputStream() {
            var remaining=LocalImages.MAX_BACKGROUND_BYTES+1
            override fun read(): Int=if(remaining-->0)0 else -1
            override fun read(buffer: ByteArray,off: Int,len: Int): Int { if(remaining<=0)return -1;val n=minOf(remaining,len);buffer.fill(0,off,off+n);remaining-=n;return n }
        }
        assertThrows(IllegalArgumentException::class.java) { LocalImages.background(tooLarge,"过大图片") }
        assertNull(decodeSavedBackground("https://example.invalid/background.jpg",8))
    }

    @Test fun manualInsetsAndKeyboardKeepHeaderAndEditorVisible() {
        compose.onNodeWithTag("home-header").assertIsDisplayed()
        val before=compose.onNodeWithTag("home-header").fetchSemanticsNode().boundsInRoot
        val density=context.resources.displayMetrics.density
        val systemTop=ViewCompat.getRootWindowInsets(compose.activity.window.decorView)!!.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()).top
        compose.runOnUiThread { model.open(NativePage.SAFE_AREA) }
        compose.onNodeWithContentDescription("上端自动留白").performClick()
        compose.waitUntil(5_000) { !data().settings.topAuto && !model.busy }
        compose.onNodeWithTag("safe-top").performSemanticsAction(SemanticsActions.SetProgress) { it(64f) }
        compose.waitUntil(5_000) { data().settings.topInset==64 && !model.busy }
        compose.onNodeWithContentDescription("下端自动留白").performScrollTo().performClick()
        compose.waitUntil(5_000) { !data().settings.bottomAuto && !model.busy }
        compose.onNodeWithTag("safe-bottom").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(48f) }
        compose.waitUntil(5_000) { data().settings.bottomInset==48 && !model.busy }
        saveScreen("native-safe-area.png")
        compose.runOnUiThread { model.back() }
        val after=compose.onNodeWithTag("home-header").fetchSemanticsNode().boundsInRoot
        assertEquals(64f-systemTop/density,(after.top-before.top)/density,2f)
        val root=compose.onRoot().fetchSemanticsNode().boundsInRoot
        val dock=compose.onNodeWithTag("capsule-navigation").fetchSemanticsNode().boundsInRoot
        assertEquals(56f,(root.bottom-dock.bottom)/density,2f)
        compose.onNodeWithContentDescription("添加课程").performClick()
        compose.onNodeWithText("课程名称").performTextInput("键盘验收课程")
        compose.onNodeWithText("保存",useUnmergedTree=true).assertIsDisplayed()
        compose.onNodeWithText("取消").performClick();compose.onNodeWithText("放弃修改").performClick()
    }

    @Test fun appearancePreferencesPersistAcrossRecreationAndLargeType() {
        compose.runOnUiThread { model.open(NativePage.APPEARANCE) }
        compose.onNodeWithText("浅色 · 晴日").performClick()
        compose.waitUntil(5_000) { data().settings.mode=="light" && !model.busy }
        compose.onNodeWithTag("appearance-settings").performScrollToNode(hasTestTag("appearance-scale"))
        compose.onNodeWithTag("appearance-scale").performSemanticsAction(SemanticsActions.SetProgress) { it(1.3f) }
        compose.waitUntil(5_000) { kotlin.math.abs(data().settings.scale-1.3f)<.01f && !model.busy }
        compose.onNodeWithTag("appearance-settings").performScrollToNode(hasText("完整效果"))
        compose.onNodeWithText("完整效果").performClick()
        compose.waitUntil(5_000) { data().settings.performance=="high" && !model.busy }
        compose.activityRule.scenario.recreate()
        assertEquals("light",data().settings.mode);assertEquals(1.3f,data().settings.scale,.01f);assertEquals("high",data().settings.performance)
        compose.onNodeWithTag("appearance-settings").performScrollToIndex(0)
        saveScreen("native-appearance-light-large.png")
    }
    private fun saveScreen(name: String) {
        compose.waitForIdle();android.os.SystemClock.sleep(250);compose.mainClock.advanceTimeBy(500);compose.waitForIdle()
        val bitmap=requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        File(context.cacheDir,name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
    }
}
