package com.dolphin.schedule.ui.common

import androidx.compose.animation.core.CubicBezierEasing

/** 统一短促、无回弹的移动曲线；只用于绘制层，不动画课表的测量尺寸。 */
internal object DolphinMotion {
    const val FEEDBACK=120
    const val SELECTION=200
    const val PAGE=220
    val standard=CubicBezierEasing(.2f,0f,0f,1f)
    val enter=CubicBezierEasing(.05f,.7f,.1f,1f)
}
