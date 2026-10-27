package com.dolphin.schedule.ui

import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class MainTab(val title: String) { LIST("列表"),GRID("平铺"),SEARCH("搜索"),SETTINGS("设置") }

/** 四个入口不删减；标签只在选中项展开，触摸区域始终至少 48dp。 */
@Composable
fun CapsuleNavigation(selected: MainTab,onSelect: (MainTab)->Unit,modifier: Modifier=Modifier) {
    Surface(modifier.widthIn(max=320.dp).height(56.dp).testTag("capsule-navigation"),shape=CircleShape,color=MaterialTheme.colorScheme.surface,shadowElevation=3.dp,tonalElevation=2.dp) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
        val density=LocalDensity.current
        val base=(maxWidth-14.dp)/4.7f
        val left=4.dp+(base+2.dp)*selected.ordinal
        val target=with(density) { Rect(left.toPx(),4.dp.toPx(),(left+base*1.7f).toPx(),52.dp.toPx()) }
        val indicator by animateRectAsState(target,tween(160,easing=FastOutSlowInEasing),label="胶囊选中背景")
        val color=MaterialTheme.colorScheme.primaryContainer
        Canvas(Modifier.fillMaxSize()) { drawRoundRect(color,indicator.topLeft,indicator.size,CornerRadius(indicator.height/2)) }
        Row(Modifier.padding(4.dp).selectableGroup(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(2.dp)) {
            MainTab.entries.forEach { tab ->
                val active=tab==selected
                Surface(
                    selected=active,onClick={onSelect(tab)},
                    // 触摸目标只在选项改变时布局一次，动画仅绘制背景，快速连点不会追着移动的目标跑。
                    modifier=Modifier.weight(if(active)1.7f else 1f).fillMaxHeight().sizeIn(minWidth=48.dp,minHeight=48.dp)
                        .semantics { contentDescription=tab.title;role=Role.Tab },
                    shape=CircleShape,color=Color.Transparent,
                    contentColor=if(active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Row(Modifier.padding(horizontal=7.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center) {
                        TabSymbol(tab,Modifier.size(24.dp))
                        if(active) { Text(tab.title,Modifier.padding(start=7.dp),style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.SemiBold,maxLines=1) }
                    }
                }
            }
        }
        }
    }
}

@Composable
fun TabSymbol(tab: MainTab,modifier: Modifier=Modifier) {
    val color=androidx.compose.material3.LocalContentColor.current
    Canvas(modifier.size(24.dp)) {
        val unit=size.minDimension/24f;val stroke=Stroke(1.8f*unit,cap=StrokeCap.Round)
        fun point(x: Float,y: Float)=Offset(x*unit,y*unit)
        when(tab) {
            MainTab.LIST -> listOf(5f,12f,19f).forEach { y -> drawRoundRect(color,point(3f,y-1.5f),Size(18*unit,4*unit),CornerRadius(unit,unit),style=stroke) }
            MainTab.GRID -> listOf(4f,10f,16f).forEach { x -> drawRoundRect(color,point(x,3f),Size(4*unit,18*unit),CornerRadius(1.5f*unit,1.5f*unit)) }
            MainTab.SEARCH -> { drawCircle(color,6.5f*unit,point(10.5f,10.5f),style=stroke);drawLine(color,point(15.5f,15.5f),point(21f,21f),1.8f*unit,StrokeCap.Round) }
            MainTab.SETTINGS -> { drawCircle(color,9f*unit,point(12f,12f),style=stroke);drawCircle(color,3f*unit,point(12f,9f),style=stroke);drawArc(color,200f,140f,false,point(6f,13f),Size(12*unit,9*unit),style=stroke) }
        }
    }
}
