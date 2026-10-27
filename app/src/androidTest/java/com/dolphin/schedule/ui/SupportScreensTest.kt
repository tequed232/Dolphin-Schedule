package com.dolphin.schedule.ui

import android.os.Build
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.dolphin.schedule.domain.Preferences
import com.dolphin.schedule.domain.RELEASES_URL
import com.dolphin.schedule.domain.ReleaseInfo
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.ExternalResource
import java.io.File

/** 不读取真实账号或课表；只有显式授权的隔离模拟器可以运行。 */
class SupportScreensTest {
    @get:Rule(order=0) val emulatorOnly=object: ExternalResource() {
        override fun before() {
            check(InstrumentationRegistry.getArguments().getString("allowNativeDataTests")=="true")
            check(Build.HARDWARE in listOf("ranchu","goldfish") && Build.MODEL.startsWith("sdk_"))
        }
    }
    @get:Rule(order=1) val compose=createComposeRule()
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    @After fun close() { scope.cancel() }

    @Test fun briefGuideOnlyOffersImportManualOrEmptyStart() {
        var imported=0;var manual=0;var continued=0
        compose.setContent { SupportHost { NativeWelcomeScreen(false,{imported++},{manual++},{continued++}) } }
        compose.onNodeWithText("课表留在本机").assertIsDisplayed()
        compose.onNodeWithTag("welcome-import").performScrollTo().performClick()
        compose.onNodeWithTag("welcome-manual").performScrollTo().performClick()
        compose.onNodeWithTag("welcome-continue").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1,imported);assertEquals(1,manual);assertEquals(1,continued) }
        compose.onNodeWithText("示例课表",substring=true).assertDoesNotExist()
    }

    @Test fun aboutHasOfflineFullLicensesAuthorAndGuideEntry() {
        var guide=0;var updates=0
        compose.setContent { SupportHost { NativeAboutScreen({guide++},{updates++}) } }
        compose.onNodeWithTag("about-author").assertTextContains("tequed232",substring=true)
        compose.onNodeWithTag("about-updates").performScrollTo().performClick()
        compose.onNodeWithTag("about-guide").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1,guide);assertEquals(1,updates) }
        compose.onNodeWithTag("about-license-MIT").performScrollTo().performClick()
        awaitLicenseBody("MIT")
        compose.onNodeWithTag("license-body").assertTextContains("Permission is hereby granted",substring=true)
        compose.onNodeWithTag("license-body").assertTextContains("SOFTWARE.",substring=true)
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithTag("about-license-THIRD_PARTY").performScrollTo().performClick()
        awaitLicenseBody("THIRD_PARTY")
        compose.onNodeWithTag("license-body").assertTextContains("第三方组件",substring=true)
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithTag("about-license-APACHE").performScrollTo().performClick()
        awaitLicenseBody("APACHE")
        compose.onNodeWithTag("license-body").assertTextContains("END OF TERMS AND CONDITIONS",substring=true)
    }

    private fun awaitLicenseBody(name: String) {
        try { compose.waitUntil(5_000) { compose.onAllNodesWithTag("license-body").fetchSemanticsNodes().isNotEmpty() } }
        catch(failure: Throwable) {
            compose.onAllNodes(isRoot()).fetchSemanticsNodes().indices.forEach { index -> println(compose.onAllNodes(isRoot())[index].printToString()) }
            val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"support-license-$name-failure.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
            throw failure
        }
    }

    @Test fun updatePageAutomaticallyChecksOnceAndSupportsExplicitRetry() {
        var calls=0
        val controller=SupportController(scope,"1.5.5") { calls++;ReleaseInfo("v1.6.0","原生版本","合成更新说明","$RELEASES_URL/tag/v1.6.0","") }
        compose.setContent { SupportHost { NativeUpdateScreen(controller) } }
        compose.waitUntil(5_000) { controller.checked && !controller.busy }
        compose.onNodeWithTag("update-status").assertTextEquals("发现新版本 v1.6.0")
        compose.onNodeWithTag("update-download").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("update-notes").performScrollTo().assertTextEquals("合成更新说明")
        compose.onNodeWithTag("update-check").performScrollTo().performClick()
        compose.waitUntil(5_000) { calls==2 && !controller.busy }
        compose.runOnIdle { assertEquals(2,calls) }
    }

    /** 独立组件也使用正式入口提供的系统安全区，避免测试把点击落到系统导航栏。 */
    @Composable private fun SupportHost(content: @Composable ()->Unit) {
        DolphinTheme(Preferences(dynamicColor=false)) { Box(Modifier.fillMaxSize().safeDrawingPadding()) { content() } }
    }
}
