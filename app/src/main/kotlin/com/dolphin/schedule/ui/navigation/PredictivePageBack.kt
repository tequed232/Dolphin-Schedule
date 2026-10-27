package com.dolphin.schedule.ui.navigation

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.dolphin.schedule.ui.common.DolphinMotion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 系统返回进度驱动当前页面；取消不导航，完成后仍执行原有的草稿保护。 */
@Composable
internal fun rememberPredictivePageBack(enabled: Boolean,route: Any?,reduced: Boolean=false,onBack: ()->Unit): Modifier {
    val progress=remember { Animatable(0f) }
    var edge by remember { mutableIntStateOf(0) }
    val latestBack by rememberUpdatedState(onBack)
    val latestRoute by rememberUpdatedState(route)
    val latestEnabled by rememberUpdatedState(enabled)
    val scope=rememberCoroutineScope()
    var settling by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(route,enabled) {
        settling?.cancel();progress.snapTo(0f)
    }
    PredictiveBackHandler(enabled=enabled) { events ->
        settling?.cancel()
        val origin=latestRoute
        try {
            events.collect { event ->
                edge=event.swipeEdge
                progress.snapTo(event.progress.coerceIn(0f,1f))
            }
            // 同一帧恢复绘制层再改变返回栈，父页面不会继承子页面的缩放。
            progress.snapTo(0f)
            if(latestEnabled && latestRoute==origin)latestBack()
        } catch(cancelled: CancellationException) {
            settling=scope.launch {
                progress.animateTo(0f,tween(if(reduced)0 else 160,easing=DolphinMotion.standard))
            }
            throw cancelled
        }
    }
    val distance=with(LocalDensity.current) { 16.dp.toPx() }
    return Modifier.graphicsLayer {
        val value=if(reduced)0f else progress.value
        scaleX=1f-.08f*value;scaleY=scaleX
        translationX=distance*value*if(edge==0)1f else -1f
        transformOrigin=TransformOrigin(if(edge==0)1f else 0f,.5f)
        shape=RoundedCornerShape((24f*value).dp);clip=value>0f
    }
}
