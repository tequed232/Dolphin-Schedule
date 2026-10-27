package com.dolphin.schedule.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dolphin.schedule.domain.*
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
internal fun rememberTimetableNow(): LocalDateTime {
    val now by produceState(LocalDateTime.now()) {
        while(true) { delay(60_000-System.currentTimeMillis()%60_000);value=LocalDateTime.now() }
    }
    return now
}

/** 空白格只用一次 Canvas 绘制，课程保留独立点击语义，减少切换时的布局与手势节点。 */
@Composable
internal fun TimetableBoard(data: AppData,model: DolphinViewModel,days: List<LocalDate>,columnWidth: Dp,horizontal: ScrollState?=null,vertical: ScrollState?=null,compact: Boolean=true,horizontalGesturesEnabled: Boolean=true) {
    val drag=remember { BoardDragState() }
    val rows=remember(data.schedule,days,compact) {
        val base=displayTimetableRows(data.schedule)
        if(compact)base else {
            val singles=days.flatMap { coursesOn(data.schedule,it) }.filter { it.start==it.end }.map { it.start-1 }.toSet()
            base.mapIndexed { index,row -> if(index in singles)row.copy(lesson=maxOf(row.lesson,144f)) else row }
        }
    }
    val height=remember(rows) { rows.sumOf { it.height.toDouble() }.toFloat() }
    val top=remember(rows) { var y=0f;rows.map { row -> y.also { y+=row.height } } }
    val now=rememberTimetableNow()
    val density=LocalDensity.current
    val visible=remember(data.schedule) { visiblePeriodIndices(data.schedule) }
    val emptyColor=MaterialTheme.colorScheme.surface.copy(alpha=.62f)
    DisposableEffect(drag) { onDispose { drag.stop();model.layout.cancelGesture() } }
    LaunchedEffect(drag.active) {
        val threshold=with(density) { 36.dp.toPx() };val speed=with(density) { 10.dp.toPx() }
        while(drag.active) {
            withFrameNanos { }
            if(model.layout.draft==null) { drag.stop();break }
            val pointer=drag.pointer;val viewport=drag.viewport
            if(pointer==Offset.Unspecified || viewport.isEmpty)continue
            fun velocity(value: Float,min: Float,max: Float)=when { value<min+threshold -> -speed*((min+threshold-value)/threshold).coerceIn(0f,1f);value>max-threshold -> speed*((value-max+threshold)/threshold).coerceIn(0f,1f);else -> 0f }
            val x=if(model.layout.draft?.edge==LayoutEdge.MOVE)velocity(pointer.x,viewport.left+with(density) { 52.dp.toPx() },viewport.right) else 0f
            val y=velocity(pointer.y,viewport.top,viewport.bottom)
            val dx=if(x!=0f)horizontal?.scrollBy(x) ?: 0f else 0f
            val dy=if(y!=0f)vertical?.scrollBy(y) ?: 0f else 0f
            if(dx!=0f || dy!=0f)drag.scrolled(Offset(dx,dy))
        }
    }
    Box(Modifier.fillMaxWidth().height(height.dp).onGloballyPositioned { drag.viewport=it.boundsInWindow() }.testTag("timetable-board")) {
        Row {
            Column(Modifier.width(if(compact)52.dp else 60.dp)) { visible.forEach { index ->
                val period=data.schedule.periods[index]
                Surface(onClick={model.openPeriod(data.schedule,index)},modifier=Modifier.height(rows[index].lesson.dp).fillMaxWidth().padding(2.dp).testTag("period-$index"),shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(top=12.dp).semantics { contentDescription="编辑第${periodLabel(period,index)}节时间" },horizontalAlignment=Alignment.CenterHorizontally) {
                        Text(periodLabel(period,index),style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.SemiBold,maxLines=1)
                        if(data.settings.showTimes) { Text(period.start,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(period.end,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                Spacer(Modifier.height(rows[index].gap.dp))
            } }
            if(!compact)Spacer(Modifier.width(12.dp))
            Row(if(horizontal!=null)Modifier.horizontalScroll(horizontal,enabled=horizontalGesturesEnabled) else Modifier) { days.forEach { date ->
                val placed=remember(data.schedule,date) { positionCourses(coursesOn(data.schedule,date)) }
                Box(Modifier.width(columnWidth).height(height.dp).zIndex(if(placed.any { it.course.id==model.layout.selectedId })1f else 0f)) {
                    Canvas(Modifier.fillMaxSize().pointerInput(data.schedule,date,rows) {
                        detectTapGestures(onTap={model.layout.exit()},onLongPress={ point ->
                            val y=with(density) { point.y.toDp().value }
                            visible.firstOrNull { y>=top[it] && y<top[it]+rows[it].lesson }?.let { model.startEdit(data,temporary=true,start=it+1,day=date) }
                        })
                    }) {
                        val inset=2.dp.toPx();val radius=16.dp.toPx()
                        visible.forEach { index -> drawRoundRect(emptyColor,Offset(inset,top[index].dp.toPx()+inset),Size((size.width-2*inset).coerceAtLeast(0f),(rows[index].lesson.dp.toPx()-2*inset).coerceAtLeast(0f)),CornerRadius(radius,radius)) }
                    }
                    placed.forEach { placedCourse ->
                        val course=placedCourse.course
                        val preview=model.layout.draft?.takeIf { compact && it.original.id==course.id }?.next ?: course
                        val span=rows.subList(preview.start-1,preview.end).sumOf { it.height.toDouble() }.toFloat()-rows[preview.end-1].gap
                        val width=columnWidth/placedCourse.lanes
                        val x=width*placedCourse.lane+columnWidth*(preview.day-course.day)
                        val card=Modifier.offset(x=x,y=top[preview.start-1].dp).width(width).height(span.dp).padding(2.dp)
                        if(compact)LayoutCourse(course,data.schedule,date,rows,with(density) { columnWidth.toPx() },model,drag,card)
                        else CourseCard(course,data.schedule,{model.show(course,date)},card,compact=false,onLongClick={model.startEdit(data,course,day=date)})
                    }
                }
            } }
        }
        if(data.settings.showCurrentTimeLine && now.toLocalDate() in days) {
            timetableTimeOffset(data.schedule.periods,rows,now.toLocalTime())?.let { y ->
                val color=MaterialTheme.colorScheme.primary.copy(alpha=.48f)
                Canvas(Modifier.fillMaxWidth().height(8.dp).offset(y=y.dp-4.dp).testTag("current-time-line").semantics { contentDescription="当前时间 ${now.format(DateTimeFormatter.ofPattern("HH:mm"))}" }) {
                    drawLine(color,Offset(0f,size.height/2),Offset(size.width,size.height/2),1.5.dp.toPx())
                    drawCircle(color,3.dp.toPx(),Offset(3.dp.toPx(),size.height/2))
                }
            }
        }
    }
}
