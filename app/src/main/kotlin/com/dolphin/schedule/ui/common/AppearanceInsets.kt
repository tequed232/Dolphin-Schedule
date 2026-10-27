package com.dolphin.schedule.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.dolphin.schedule.domain.Preferences

/** 手动留白只替代上下安全区；屏幕切口的横向避让与键盘避让仍生效。 */
@Composable
internal fun dolphinInsets(settings: Preferences): WindowInsets {
    val density=LocalDensity.current
    val safe=WindowInsets.safeDrawing
    val top=if(settings.topAuto)safe.only(WindowInsetsSides.Top) else WindowInsets(0,with(density) { settings.topInset.coerceIn(0,100).dp.roundToPx() },0,0)
    val bottom=if(settings.bottomAuto)safe.only(WindowInsetsSides.Bottom) else WindowInsets(0,0,0,with(density) { settings.bottomInset.coerceIn(0,100).dp.roundToPx() })
    return safe.only(WindowInsetsSides.Horizontal).union(top).union(bottom).union(WindowInsets.ime)
}
