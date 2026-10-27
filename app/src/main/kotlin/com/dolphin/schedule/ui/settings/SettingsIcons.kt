package com.dolphin.schedule.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.dolphin.schedule.ui.NativePage
import com.dolphin.schedule.ui.timetable.HomeIcon
import com.dolphin.schedule.ui.timetable.HomeSymbol

/** 图标随主题取色；作为文字标签的装饰，不重复播报选项名称。 */
@Composable
internal fun SettingsIconBadge(page: NativePage) {
    Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.primaryContainer.copy(alpha=.45f)) {
        Box(Modifier.size(36.dp),contentAlignment=Alignment.Center) { SettingsSymbol(page) }
    }
}

@Composable
private fun SettingsSymbol(page: NativePage) {
    val color=MaterialTheme.colorScheme.primary
    val homeIcon=when(page) {
        NativePage.IMPORT,NativePage.FILE_IMPORT->HomeIcon.IMPORT
        NativePage.TERM->HomeIcon.CALENDAR
        NativePage.TIMES->HomeIcon.CLOCK
        NativePage.NAVIGATION->HomeIcon.NAVIGATE
        else->null
    }
    if(homeIcon!=null) { HomeSymbol(homeIcon,Modifier.size(22.dp),color);return }
    Canvas(Modifier.size(22.dp)) {
        val u=size.minDimension/24f
        val stroke=Stroke(1.7f*u,cap=StrokeCap.Round)
        fun p(x: Float,y: Float)=Offset(x*u,y*u)
        fun line(x: Float,y: Float,x2: Float,y2: Float)=drawLine(color,p(x,y),p(x2,y2),1.7f*u,StrokeCap.Round)
        fun box(x: Float,y: Float,w: Float,h: Float,r: Float=2f)=drawRoundRect(color,p(x,y),Size(w*u,h*u),CornerRadius(r*u),style=stroke)
        when(page) {
            NativePage.MANAGE->{
                box(3f,3f,18f,18f)
                line(3f,9f,21f,9f);line(9f,3f,9f,21f)
                line(13f,15f,15f,17f);line(15f,17f,19f,13f)
            }
            NativePage.BOOKS->{
                val book=Path().apply {
                    moveTo(12*u,5*u);cubicTo(9*u,3*u,5*u,3*u,2*u,4*u)
                    lineTo(2*u,20*u);cubicTo(5*u,19*u,9*u,19*u,12*u,21*u)
                    cubicTo(15*u,19*u,19*u,19*u,22*u,20*u);lineTo(22*u,4*u)
                    cubicTo(19*u,3*u,15*u,3*u,12*u,5*u);lineTo(12*u,21*u)
                }
                drawPath(book,color,style=stroke)
                line(5f,8f,9f,9f);line(15f,9f,19f,8f)
            }
            NativePage.HOME->{
                val roof=Path().apply { moveTo(2*u,11*u);lineTo(12*u,3*u);lineTo(22*u,11*u) }
                drawPath(roof,color,style=stroke)
                val walls=Path().apply { moveTo(5*u,9*u);lineTo(5*u,21*u);lineTo(19*u,21*u);lineTo(19*u,9*u) }
                drawPath(walls,color,style=stroke);box(9f,14f,6f,7f,1f)
            }
            NativePage.APPEARANCE->{
                val palette=Path().apply {
                    moveTo(12*u,3*u);cubicTo(6*u,3*u,2*u,7*u,2*u,12*u)
                    cubicTo(2*u,18*u,6*u,21*u,11*u,21*u)
                    cubicTo(15*u,21*u,11*u,17*u,15*u,16*u)
                    lineTo(18*u,16*u);cubicTo(24*u,16*u,23*u,3*u,12*u,3*u);close()
                }
                drawPath(palette,color,style=stroke)
                listOf(p(7f,9f),p(12f,7f),p(17f,9f),p(6f,14f)).forEach { drawCircle(color,1.1f*u,it) }
            }
            NativePage.NOTIFICATIONS->{
                val bell=Path().apply {
                    moveTo(5*u,16*u);lineTo(6*u,14*u);lineTo(6*u,10*u)
                    cubicTo(6*u,6*u,8*u,4*u,12*u,4*u);cubicTo(16*u,4*u,18*u,6*u,18*u,10*u)
                    lineTo(18*u,14*u);lineTo(20*u,17*u);lineTo(4*u,17*u);close()
                }
                drawPath(bell,color,style=stroke);line(12f,2f,12f,4f)
                drawArc(color,20f,140f,false,p(9f,17f),Size(6*u,5*u),style=stroke)
            }
            NativePage.UPDATES->{
                drawArc(color,135f,285f,false,p(3f,3f),Size(18*u,18*u),style=stroke)
                line(3f,13f,3f,18f);line(3f,18f,8f,18f)
                line(12f,17f,12f,8f);line(8f,12f,12f,8f);line(12f,8f,16f,12f)
            }
            NativePage.DATA->{
                drawOval(color,p(3f,3f),Size(18*u,6*u),style=stroke)
                line(3f,6f,3f,18f);line(21f,6f,21f,18f)
                drawArc(color,0f,180f,false,p(3f,9f),Size(18*u,6*u),style=stroke)
                drawArc(color,0f,180f,false,p(3f,15f),Size(18*u,6*u),style=stroke)
            }
            NativePage.ABOUT->{
                drawCircle(color,9*u,p(12f,12f),style=stroke)
                drawCircle(color,1f*u,p(12f,7f));line(12f,11f,12f,17f)
            }
            NativePage.BACKGROUND->{
                box(3f,3f,18f,18f)
                drawCircle(color,1.8f*u,p(8f,8f),style=stroke)
                val mountains=Path().apply { moveTo(3*u,17*u);lineTo(9*u,12*u);lineTo(13*u,16*u);lineTo(17*u,11*u);lineTo(21*u,15*u) }
                drawPath(mountains,color,style=stroke)
            }
            NativePage.SAFE_AREA->{
                box(5f,2f,14f,20f,3f)
                line(9f,5f,15f,5f);line(9f,19f,15f,19f)
                line(8f,9f,16f,9f);line(8f,15f,16f,15f)
            }
            NativePage.IMPORT,NativePage.FILE_IMPORT,NativePage.TERM,NativePage.TIMES,NativePage.NAVIGATION->Unit
        }
    }
}
