package com.dolphin.schedule.ui

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.dolphin.schedule.domain.Preferences
import com.dolphin.schedule.ui.common.DolphinTheme
import com.dolphin.schedule.ui.common.dolphinColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class NativeColorRolesTest {
    @get:Rule val compose=createComposeRule()
    @Test fun dynamicThemeUsesMatchingContentAndContainerRolesAcrossModeChanges() {
        assumeTrue(Build.VERSION.SDK_INT>=31)
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val dark=mutableStateOf(false)
        var observed: ColorScheme?=null
        compose.setContent {
            DolphinTheme(Preferences(mode=if(dark.value)"dark" else "light",dynamicColor=true)) {
                observed=MaterialTheme.colorScheme
            }
        }
        listOf(false,true).forEach { value ->
            compose.runOnIdle { dark.value=value };compose.waitForIdle()
            val expected=if(value)dynamicDarkColorScheme(context)else dynamicLightColorScheme(context)
            val actual=requireNotNull(observed)
            assertEquals(expected.primary,actual.primary)
            assertEquals(expected.onPrimary,actual.onPrimary)
            assertEquals(expected.primaryContainer,actual.primaryContainer)
            assertEquals(expected.onPrimaryContainer,actual.onPrimaryContainer)
            assertEquals(expected.secondaryContainer,actual.secondaryContainer)
            assertEquals(expected.onSecondaryContainer,actual.onSecondaryContainer)
            assertEquals(dolphinColorScheme(value).surfaceContainer,actual.surfaceContainer)
            assertEquals(dolphinColorScheme(value).onSurface,actual.onSurface)
        }
    }
}
