package com.dolphin.schedule.ui

import android.os.Build
import android.view.inspector.WindowInspector
import androidx.activity.BackEventCompat
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.dolphin.schedule.NativeActivity
import com.dolphin.schedule.data.NativeRepository
import com.dolphin.schedule.data.RepositoryState
import com.dolphin.schedule.domain.AppData
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.ExternalResource
import java.io.File

class NativePredictiveBackTest {
    @get:Rule(order=0) val emulatorOnly=object: ExternalResource() {
        override fun before() {
            check(InstrumentationRegistry.getArguments().getString("allowNativeDataTests")=="true")
            check(Build.HARDWARE in listOf("ranchu","goldfish") && Build.MODEL.startsWith("sdk_"))
        }
    }
    @get:Rule(order=1) val compose=createAndroidComposeRule<NativeActivity>()
    private lateinit var model: DolphinViewModel
    private lateinit var repository: NativeRepository
    private lateinit var original: AppData
    @Before fun prepare() {
        repository=NativeRepository.get(compose.activity)
        runBlocking {
            original=(repository.initialize() as RepositoryState.Ready).snapshot.data
            repository.update { it.copy(onboarded=true,settings=it.settings.copy(performance="high")) }
        }
        compose.activityRule.scenario.onActivity { model=ViewModelProvider(it)[DolphinViewModel::class.java] }
        compose.waitForIdle()
    }
    @After fun restore() {
        if(::original.isInitialized)runBlocking { repository.update { original } }
    }
    private fun dispatcher()=WindowInspector.getGlobalWindowViews().lastOrNull { it.hasWindowFocus() }
        ?.findViewTreeOnBackPressedDispatcherOwner()?.onBackPressedDispatcher ?: compose.activity.onBackPressedDispatcher
    private fun startGesture(progress: Float,edge: Int=BackEventCompat.EDGE_LEFT) {
        compose.runOnUiThread {
            val dispatcher=dispatcher()
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f,600f,0f,edge))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(180f,600f,progress,edge))
        }
        compose.waitForIdle()
    }
    @Test fun cancellationRestoresPageAndCompletionPopsExactlyOneLevel() {
        compose.runOnUiThread { model.open(NativePage.APPEARANCE);model.open(NativePage.SAFE_AREA) }
        compose.waitForIdle()
        val baseline=(repository.state.value as RepositoryState.Ready).snapshot.data
        val width=compose.onNodeWithTag("page-content").fetchSemanticsNode().boundsInRoot.width
        startGesture(.65f)
        assertTrue(compose.onNodeWithTag("page-content").fetchSemanticsNode().boundsInRoot.width<width)
        val image=requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        File(compose.activity.cacheDir,"predictive-back-progress.png").outputStream().use {
            image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
        image.recycle()
        assertEquals(2,model.pages.size)
        compose.runOnUiThread { dispatcher().dispatchOnBackCancelled() }
        compose.waitForIdle()
        assertEquals(NativePage.SAFE_AREA,model.pages.last())
        assertEquals(width,compose.onNodeWithTag("page-content").fetchSemanticsNode().boundsInRoot.width,1f)
        startGesture(.9f,BackEventCompat.EDGE_RIGHT)
        compose.runOnUiThread { dispatcher().onBackPressed() }
        compose.waitUntil(5_000) { model.pages.size==1 }
        assertEquals(NativePage.APPEARANCE,model.pages.last())
        assertEquals(baseline,(repository.state.value as RepositoryState.Ready).snapshot.data)
    }
    @Test fun dirtyCourseEditorKeepsItsDraftOnCancelledOrCompletedGesture() {
        val baseline=(repository.state.value as RepositoryState.Ready).snapshot.data
        compose.runOnUiThread { model.startEdit(baseline) }
        compose.onNodeWithText("课程名称").performTextInput("预测返回草稿")
        startGesture(.55f)
        compose.runOnUiThread { dispatcher().dispatchOnBackCancelled() }
        compose.waitForIdle()
        compose.onNodeWithText("放弃这次修改？").assertDoesNotExist()
        compose.onNodeWithText("预测返回草稿").assertExists()
        startGesture(.85f)
        compose.runOnUiThread { dispatcher().onBackPressed() }
        compose.onNodeWithText("放弃这次修改？").assertIsDisplayed()
        compose.onNodeWithText("继续编辑").performClick()
        compose.onNodeWithText("预测返回草稿").assertExists()
        assertEquals(baseline,(repository.state.value as RepositoryState.Ready).snapshot.data)
    }
}
