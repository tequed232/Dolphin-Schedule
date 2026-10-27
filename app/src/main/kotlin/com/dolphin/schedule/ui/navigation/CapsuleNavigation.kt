package com.dolphin.schedule.ui.navigation

import com.dolphin.schedule.ui.MainTab
import com.dolphin.schedule.R
import com.dolphin.schedule.ui.common.DolphinMotion
import com.dolphin.schedule.ui.common.DolphinSymbol
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp


/** 四个入口不删减；标签只在选中项展开，触摸区域始终至少 48dp。 */
@Composable
fun CapsuleNavigation(selected: MainTab,onSelect: (MainTab)->Unit,modifier: Modifier=Modifier,reduced: Boolean=false) {
    Surface(modifier.widthIn(max=320.dp).height(56.dp).testTag("capsule-navigation"),shape=CircleShape,color=MaterialTheme.colorScheme.surfaceContainer,shadowElevation=4.dp,tonalElevation=0.dp) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
        val density=LocalDensity.current
        val base=(maxWidth-14.dp)/4.7f
        val left=4.dp+(base+2.dp)*selected.ordinal
        val target=with(density) { Rect(left.toPx(),4.dp.toPx(),(left+base*1.7f).toPx(),52.dp.toPx()) }
        val indicator by animateRectAsState(target,tween(if(reduced)0 else DolphinMotion.SELECTION,easing=DolphinMotion.standard),label="胶囊选中背景")
        val color=MaterialTheme.colorScheme.primaryContainer
        Canvas(Modifier.fillMaxSize()) { drawRoundRect(color,indicator.topLeft,indicator.size,CornerRadius(indicator.height/2)) }
        Row(Modifier.padding(4.dp).selectableGroup(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(2.dp)) {
            MainTab.entries.forEach { tab ->
                val active=tab==selected
                val interaction=remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val pressScale by animateFloatAsState(if(pressed && !reduced).96f else 1f,tween(DolphinMotion.FEEDBACK,easing=DolphinMotion.standard),label="胶囊按压")
                val tint by animateColorAsState(if(active)MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,tween(if(reduced)0 else DolphinMotion.FEEDBACK),label="胶囊图标颜色")
                Surface(
                    selected=active,onClick={if(!active)onSelect(tab)},interactionSource=interaction,
                    // 触摸目标只在选项改变时布局一次，动画仅绘制背景，快速连点不会追着移动的目标跑。
                    modifier=Modifier.weight(if(active)1.7f else 1f).fillMaxHeight().sizeIn(minWidth=48.dp,minHeight=48.dp)
                        .semantics { contentDescription=tab.title;role=Role.Tab }.graphicsLayer { scaleX=pressScale;scaleY=pressScale },
                    shape=CircleShape,color=Color.Transparent,
                    contentColor=tint,
                ) {
                    Row(Modifier.padding(horizontal=7.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center) {
                        TabSymbol(tab,Modifier.size(24.dp),selected=active)
                        if(active) { Text(tab.title,Modifier.padding(start=7.dp),style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.SemiBold,maxLines=1) }
                    }
                }
            }
        }
        }
    }
}

@Composable
fun TabSymbol(tab: MainTab,modifier: Modifier=Modifier,selected: Boolean=false) {
    val resource=when(tab) {
        MainTab.LIST->if(selected)R.drawable.ms_view_agenda_filled else R.drawable.ms_view_agenda
        MainTab.GRID->if(selected)R.drawable.ms_view_week_filled else R.drawable.ms_view_week
        MainTab.SEARCH->R.drawable.ms_search
        MainTab.SETTINGS->if(selected)R.drawable.ms_account_circle_filled else R.drawable.ms_account_circle
    }
    DolphinSymbol(resource,modifier)
}
