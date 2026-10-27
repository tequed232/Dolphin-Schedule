package com.dolphin.schedule.ui.navigation

import com.dolphin.schedule.ui.MainTab
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.dolphin.schedule.ui.NativePage
import com.dolphin.schedule.ui.common.DolphinMotion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

private data class PageLocation(val tab: MainTab,val page: NativePage?,val depth: Int)
private class PageMotionHistory(var location: PageLocation)

/** 只变换当前页面的图层，不保留另一份大课表，也不逐帧重排课程。 */
@Composable
internal fun rememberPageMotion(tab: MainTab,page: NativePage?,depth: Int,reduced: Boolean): Modifier {
    val current=PageLocation(tab,page,depth)
    val history=remember { PageMotionHistory(current) }
    val translation=remember { Animatable(0f) }
    val opacity=remember { Animatable(1f) }
    val distance=with(LocalDensity.current) { 18.dp.toPx() }
    LaunchedEffect(current,reduced) {
        val previous=history.location
        history.location=current
        if(reduced) { translation.snapTo(0f);opacity.snapTo(1f);return@LaunchedEffect }
        if(current==previous)return@LaunchedEffect
        val direction=when {
            depth!=previous.depth->if(depth>previous.depth)1f else -1f
            tab!=previous.tab->if(tab.ordinal>previous.tab.ordinal)1f else -1f
            else->1f
        }
        // 快速反向切换从当前绘制位置继续，取消上一段动画时不重置到起点。
        if(abs(translation.value)<.1f && opacity.value>.999f) {
            translation.snapTo(distance*direction);opacity.snapTo(.92f)
        }
        coroutineScope {
            launch { translation.animateTo(0f,tween(DolphinMotion.PAGE,easing=DolphinMotion.enter)) }
            launch { opacity.animateTo(1f,tween(DolphinMotion.SELECTION,easing=DolphinMotion.standard)) }
        }
    }
    return Modifier.graphicsLayer {
        alpha=opacity.value
        translationX=translation.value
    }
}
