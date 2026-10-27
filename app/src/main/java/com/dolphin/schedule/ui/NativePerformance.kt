package com.dolphin.schedule.ui

import android.app.ActivityManager
import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.dolphin.schedule.domain.shouldReduceBackgroundFrames

internal class NativePerformance(val automatic: Boolean,lowMemory: Boolean) {
    var reduced by mutableStateOf(automatic && lowMemory);internal set
    var interaction by mutableLongStateOf(0L);private set
    fun touch() { val now=SystemClock.uptimeMillis();if(automatic && !reduced && now-interaction>900)interaction=now }
}

/** 只在交互开始后采样一小段时间；自动模式遇到掉帧时降低背景采样开销。 */
@Composable
internal fun rememberNativePerformance(mode: String): NativePerformance {
    val context=LocalContext.current
    val lowMemory=remember { context.getSystemService(ActivityManager::class.java).isLowRamDevice }
    val monitor=remember(mode,lowMemory) { NativePerformance(mode!="high",lowMemory) }
    val refresh=LocalView.current.display?.refreshRate?.coerceIn(30f,240f) ?: 60f
    LaunchedEffect(monitor,monitor.interaction) {
        if(!monitor.automatic || monitor.reduced || monitor.interaction==0L)return@LaunchedEffect
        var previous=withFrameNanos { it };val intervals=ArrayList<Long>(24)
        repeat(24) { val now=withFrameNanos { it };intervals+=now-previous;previous=now }
        if(shouldReduceBackgroundFrames(intervals,refresh))monitor.reduced=true
    }
    return monitor
}

internal fun Modifier.observeDolphinPerformance(monitor: NativePerformance): Modifier=pointerInput(monitor) {
    awaitPointerEventScope { while(true) {
        val event=awaitPointerEvent(PointerEventPass.Initial)
        if(event.changes.any { it.pressed })monitor.touch()
    } }
}
