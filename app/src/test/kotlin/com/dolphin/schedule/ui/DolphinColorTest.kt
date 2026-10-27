package com.dolphin.schedule.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.dolphin.schedule.ui.common.dolphinColorScheme
import org.junit.Assert.assertTrue
import org.junit.Test

class DolphinColorTest {
    @Test fun brandColorRolesKeepReadableTextInBothModes() {
        listOf(false,true).forEach { dark ->
            val colors=dolphinColorScheme(dark)
            listOf(
                "primary" to (colors.primary to colors.onPrimary),
                "primaryContainer" to (colors.primaryContainer to colors.onPrimaryContainer),
                "secondary" to (colors.secondary to colors.onSecondary),
                "secondaryContainer" to (colors.secondaryContainer to colors.onSecondaryContainer),
                "tertiary" to (colors.tertiary to colors.onTertiary),
                "tertiaryContainer" to (colors.tertiaryContainer to colors.onTertiaryContainer),
                "background" to (colors.background to colors.onBackground),
                "surface" to (colors.surface to colors.onSurface),
                "surfaceVariant" to (colors.surfaceVariant to colors.onSurfaceVariant),
            ).forEach { (name,pair) ->
                assertTrue("$name / dark=$dark 对比度不足",contrast(pair.first,pair.second)>=4.5)
            }
        }
    }
    private fun contrast(first: Color,second: Color): Double {
        val a=first.luminance().toDouble();val b=second.luminance().toDouble()
        return (maxOf(a,b)+.05)/(minOf(a,b)+.05)
    }
}
