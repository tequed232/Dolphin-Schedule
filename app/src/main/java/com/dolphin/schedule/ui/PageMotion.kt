package com.dolphin.schedule.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

private data class PageLocation(val tab: MainTab,val page: NativePage?,val depth: Int)
private class PageMotionHistory(var location: PageLocation)

/** 只变换当前页面的图层，不保留另一份大课表，也不逐帧重排课程。 */
@Composable
internal fun rememberPageMotion(tab: MainTab,page: NativePage?,depth: Int,reduced: Boolean): Modifier {
    val current=PageLocation(tab,page,depth)
    val history=remember { PageMotionHistory(current) }
    val direction=remember(current) {
        val previous=history.location
        when { current==previous->0f;depth!=previous.depth->if(depth>previous.depth)1f else -1f;tab!=previous.tab->if(tab.ordinal>previous.tab.ordinal)1f else -1f;else->1f }
    }
    val progress=remember(current) { Animatable(if(direction==0f)1f else 0f) }
    SideEffect { history.location=current }
    LaunchedEffect(progress,reduced) { progress.animateTo(1f,tween(if(reduced)100 else 180,easing=FastOutSlowInEasing)) }
    val distance=with(LocalDensity.current) { 12.dp.toPx() }
    return Modifier.graphicsLayer {
        // 保留可读的首帧，快速切换时不会反复闪成整页空白。
        alpha=.82f+.18f*progress.value
        translationX=if(reduced)0f else (1f-progress.value)*distance*direction
    }
}
