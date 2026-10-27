package com.dolphin.schedule.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.dolphin.schedule.domain.Preferences
import com.dolphin.schedule.ui.common.DolphinTheme
import com.dolphin.schedule.ui.navigation.CapsuleNavigation
import com.dolphin.schedule.ui.MainTab
import com.dolphin.schedule.ui.navigation.rememberPageMotion
import java.io.File
import org.junit.Rule
import org.junit.Test

class CapsuleNavigationTest {
    @get:Rule val compose=createComposeRule()
    @Test fun fourAccessibleTargetsStayAtLeast48DpAndOnlySelectedCaptionExpands() {
        compose.setContent {
            var selected by remember { mutableStateOf(MainTab.LIST) }
            DolphinTheme(Preferences(dynamicColor=false)) { CapsuleNavigation(selected,{selected=it}) }
        }
        MainTab.entries.forEach { tab -> compose.onNodeWithContentDescription(tab.title).assertHasClickAction().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp) }
        compose.onNodeWithContentDescription("列表").assertIsSelected()
        compose.onNodeWithText("列表").assertIsDisplayed()
        compose.onNodeWithText("平铺").assertDoesNotExist()
        compose.onNodeWithContentDescription("搜索").performClick().assertIsSelected()
        compose.onNodeWithText("搜索").assertIsDisplayed()
        compose.onNodeWithText("列表").assertDoesNotExist()
        saveCapsule("capsule-light.png")
    }
    @Test fun darkCapsuleStaysCompactAndOperableWithLargeText() {
        compose.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.35f)) {
                var selected by remember { mutableStateOf(MainTab.GRID) }
                DolphinTheme(Preferences(mode="dark",dynamicColor=false)) { Box(Modifier.width(288.dp)) { CapsuleNavigation(selected,{selected=it}) } }
            }
        }
        MainTab.entries.forEach { tab -> compose.onNodeWithContentDescription(tab.title).assertWidthIsAtLeast(48.dp).performClick().assertIsSelected() }
        compose.onNodeWithText("设置").assertIsDisplayed()
        saveCapsule("capsule-dark.png")
    }
    @Test fun rapidSelectionsInterruptMotionAndKeepLatestPageAndTouchTargets() {
        compose.setContent {
            var selected by remember { mutableStateOf(MainTab.LIST) }
            DolphinTheme(Preferences(dynamicColor=false)) {
                Column {
                    val motion=rememberPageMotion(selected,null,0,reduced=false)
                    Box(Modifier.then(motion)) { Text("当前页面：${selected.title}") }
                    CapsuleNavigation(selected,{selected=it})
                }
            }
        }
        compose.mainClock.autoAdvance=false
        try {
            listOf(MainTab.GRID,MainTab.SEARCH,MainTab.LIST,MainTab.SETTINGS).forEach { tab ->
                compose.onNodeWithContentDescription(tab.title).performClick()
                compose.mainClock.advanceTimeBy(32)
                compose.onNodeWithContentDescription(tab.title).assertIsSelected()
                MainTab.entries.forEach { target -> compose.onNodeWithContentDescription(target.title).assertWidthIsAtLeast(48.dp).assertHasClickAction() }
                compose.onAllNodesWithText("当前页面：${tab.title}").assertCountEquals(1)
            }
            compose.mainClock.advanceTimeBy(400)
            compose.onNodeWithText("当前页面：设置").assertIsDisplayed()
            compose.onNodeWithText("当前页面：列表").assertDoesNotExist()
        } finally { compose.mainClock.autoAdvance=true }
    }
    private fun saveCapsule(name: String) {
        compose.mainClock.advanceTimeBy(800)
        compose.waitForIdle()
        // Android 原生波纹由 RenderThread 驱动，不随 Compose 测试时钟前进。
        android.os.SystemClock.sleep(600)
        val bitmap=compose.onNodeWithTag("capsule-navigation").captureToImage().asAndroidBitmap()
        File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
    }
}
