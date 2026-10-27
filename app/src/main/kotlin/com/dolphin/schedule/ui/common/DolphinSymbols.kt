package com.dolphin.schedule.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/** Google Material Symbols Rounded 的离线子集。外层控件负责文字与辅助功能名称。 */
@Composable
internal fun DolphinSymbol(@DrawableRes resource: Int,modifier: Modifier=Modifier,tint: Color=LocalContentColor.current) {
    Icon(painterResource(resource),contentDescription=null,modifier=modifier.size(24.dp),tint=tint)
}
