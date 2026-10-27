package com.dolphin.schedule.ui.common

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.dolphin.schedule.domain.Preferences

private val LightColors=lightColorScheme(
    primary=Color(0xFF007F89),onPrimary=Color.White,primaryContainer=Color(0xFFE3F2F2),onPrimaryContainer=Color(0xFF007F89),
    background=Color(0xFFF5F6F7),onBackground=Color(0xFF182C2D),surface=Color.White,onSurface=Color(0xFF182C2D),
    surfaceVariant=Color(0xFFEDF1F2),onSurfaceVariant=Color(0xFF657477),outline=Color(0xFFB2BEC2),outlineVariant=Color(0xFFE4E9EB),
    error=Color(0xFFBD3A39),
    secondary=Color(0xFF007F89),onSecondary=Color.White,secondaryContainer=Color(0xFFE3F2F2),onSecondaryContainer=Color(0xFF007F89),
    surfaceDim=Color(0xFFEDF1F2),surfaceBright=Color.White,surfaceContainerLowest=Color.White,
    surfaceContainerLow=Color(0xFFF5F6F7),surfaceContainer=Color(0xFFF1F3F5),surfaceContainerHigh=Color(0xFFEDF1F2),surfaceContainerHighest=Color(0xFFE4E9EB),
    inverseSurface=Color(0xFF202326),inverseOnSurface=Color(0xFFEEF0F1),
)
private val DarkColors=darkColorScheme(
    primary=Color(0xFF79D7DA),onPrimary=Color(0xFF073338),primaryContainer=Color(0xFF233E42),onPrimaryContainer=Color(0xFF79D7DA),
    background=Color(0xFF121416),onBackground=Color(0xFFEEF0F1),surface=Color(0xFF202326),onSurface=Color(0xFFEEF0F1),
    surfaceVariant=Color(0xFF2B3034),onSurfaceVariant=Color(0xFFA8AFB5),outline=Color(0xFF69727A),outlineVariant=Color(0xFF343A40),
    error=Color(0xFFFFA6A3),
    secondary=Color(0xFF79D7DA),onSecondary=Color(0xFF073338),secondaryContainer=Color(0xFF233E42),onSecondaryContainer=Color(0xFF79D7DA),
    surfaceDim=Color(0xFF121416),surfaceBright=Color(0xFF292D31),surfaceContainerLowest=Color(0xFF121416),
    surfaceContainerLow=Color(0xFF202326),surfaceContainer=Color(0xFF202326),surfaceContainerHigh=Color(0xFF292D31),surfaceContainerHighest=Color(0xFF2B3034),
    inverseSurface=Color(0xFFEEF0F1),inverseOnSurface=Color(0xFF182C2D),
)

@Composable
fun DolphinTheme(preferences: Preferences=Preferences(),content: @Composable ()->Unit) {
    val dark=when(preferences.mode) { "dark"->true;"light"->false;else->isSystemInDarkTheme() }
    val context=LocalContext.current
    val base=if(dark) DarkColors else LightColors
    // 原版只用系统强调色替换 accent，卡片与课程配色仍沿用 Dolphin。
    val colors=if(preferences.dynamicColor && Build.VERSION.SDK_INT>=31) {
        val dynamic=if(dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        base.copy(primary=dynamic.primary)
    } else base
    MaterialTheme(colorScheme=colors,content=content)
}
