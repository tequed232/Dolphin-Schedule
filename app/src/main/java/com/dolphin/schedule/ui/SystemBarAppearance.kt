package com.dolphin.schedule.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

/** App 与原生编辑对话框各自的 Window 都跟随 App 主题，避免深色背景上的黑色系统图标。 */
@Composable
internal fun SystemBarAppearance() {
    val view=LocalView.current
    val context=LocalContext.current
    val light=MaterialTheme.colorScheme.background.luminance()>=.3f
    SideEffect {
        val window=(view.parent as? DialogWindowProvider)?.window ?: context.activity()?.window
        if(window!=null)WindowCompat.getInsetsController(window,view).apply {
            isAppearanceLightStatusBars=light
            isAppearanceLightNavigationBars=light
        }
    }
}

private tailrec fun Context.activity(): Activity? = when(this) {
    is Activity->this
    is ContextWrapper->baseContext.activity()
    else->null
}
