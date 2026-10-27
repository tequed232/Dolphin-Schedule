package com.dolphin.schedule.ui.common

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.dolphin.schedule.domain.Preferences

private val LightColors=lightColorScheme(
    primary=Color(0xFF007780),onPrimary=Color.White,primaryContainer=Color(0xFFD1F1F1),onPrimaryContainer=Color(0xFF004F55),
    background=Color(0xFFF5F6F7),onBackground=Color(0xFF182C2D),surface=Color.White,onSurface=Color(0xFF182C2D),
    surfaceVariant=Color(0xFFEDF1F2),onSurfaceVariant=Color(0xFF54676A),outline=Color(0xFFB2BEC2),outlineVariant=Color(0xFFE4E9EB),
    error=Color(0xFFBD3A39),
    secondary=Color(0xFF4B6365),onSecondary=Color.White,secondaryContainer=Color(0xFFCDE7E9),onSecondaryContainer=Color(0xFF334B4D),
    tertiary=Color(0xFF506078),onTertiary=Color.White,tertiaryContainer=Color(0xFFD8E3FD),onTertiaryContainer=Color(0xFF384860),
    surfaceDim=Color(0xFFEDF1F2),surfaceBright=Color.White,surfaceContainerLowest=Color.White,
    surfaceContainerLow=Color(0xFFF5F6F7),surfaceContainer=Color(0xFFF1F3F5),surfaceContainerHigh=Color(0xFFEDF1F2),surfaceContainerHighest=Color(0xFFE4E9EB),
    inverseSurface=Color(0xFF202326),inverseOnSurface=Color(0xFFEEF0F1),
)
private val DarkColors=darkColorScheme(
    primary=Color(0xFF79D7DA),onPrimary=Color(0xFF073338),primaryContainer=Color(0xFF233E42),onPrimaryContainer=Color(0xFF79D7DA),
    background=Color(0xFF121416),onBackground=Color(0xFFEEF0F1),surface=Color(0xFF202326),onSurface=Color(0xFFEEF0F1),
    surfaceVariant=Color(0xFF2B3034),onSurfaceVariant=Color(0xFFA8AFB5),outline=Color(0xFF69727A),outlineVariant=Color(0xFF343A40),
    error=Color(0xFFFFA6A3),
    secondary=Color(0xFFB1CCCE),onSecondary=Color(0xFF1C3436),secondaryContainer=Color(0xFF334B4D),onSecondaryContainer=Color(0xFFCDE7E9),
    tertiary=Color(0xFFB8C7E3),onTertiary=Color(0xFF213149),tertiaryContainer=Color(0xFF384860),onTertiaryContainer=Color(0xFFD8E3FD),
    surfaceDim=Color(0xFF121416),surfaceBright=Color(0xFF292D31),surfaceContainerLowest=Color(0xFF121416),
    surfaceContainerLow=Color(0xFF202326),surfaceContainer=Color(0xFF202326),surfaceContainerHigh=Color(0xFF292D31),surfaceContainerHighest=Color(0xFF2B3034),
    inverseSurface=Color(0xFFEEF0F1),inverseOnSurface=Color(0xFF182C2D),
)

@Composable
fun DolphinTheme(preferences: Preferences=Preferences(),content: @Composable ()->Unit) {
    val dark=when(preferences.mode) { "dark"->true;"light"->false;else->isSystemInDarkTheme() }
    val context=LocalContext.current
    // 成套使用 MD3 颜色角色，避免系统主色与旧容器、旧文字色混搭。
    val colors=remember(dark,preferences.dynamicColor,context) {
        val dynamic=if(preferences.dynamicColor && Build.VERSION.SDK_INT>=31) {
            if(dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else null
        dolphinColorScheme(dark,dynamic)
    }
    MaterialTheme(colorScheme=colors,content=content)
}

internal fun dolphinColorScheme(dark: Boolean,dynamic: ColorScheme?=null): ColorScheme {
    val base=if(dark)DarkColors else LightColors
    // 保留 Dolphin 的中性表面层级，避免厂商高饱和动态表面淹没时间卡片。
    return dynamic?.let { base.copy(
        primary=it.primary,onPrimary=it.onPrimary,primaryContainer=it.primaryContainer,onPrimaryContainer=it.onPrimaryContainer,
        secondary=it.secondary,onSecondary=it.onSecondary,secondaryContainer=it.secondaryContainer,onSecondaryContainer=it.onSecondaryContainer,
        tertiary=it.tertiary,onTertiary=it.onTertiary,tertiaryContainer=it.tertiaryContainer,onTertiaryContainer=it.onTertiaryContainer,
        inversePrimary=it.inversePrimary,surfaceTint=it.primary,
    ) } ?: base
}
