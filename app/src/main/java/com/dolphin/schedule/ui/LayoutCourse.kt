package com.dolphin.schedule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import com.dolphin.schedule.domain.*
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException

/** 像素移动仅保存在手势对象；只有吸附目标变化时才更新 Compose 预览。 */
internal class BoardDragState {
    var active by mutableStateOf(false);private set
    var delta=Offset.Zero;private set
    var pointer=Offset.Unspecified;private set
    var viewport=Rect.Zero
    private var refresh: (Offset)->Unit={}
    fun begin(point: Offset,update: (Offset)->Unit) { delta=Offset.Zero;pointer=point;refresh=update;active=true }
    fun move(amount: Offset,point: Offset) { if(active){delta+=amount;pointer=point;refresh(delta)} }
    fun scrolled(amount: Offset) { if(active){delta+=amount;refresh(delta)} }
    fun stop() { active=false;pointer=Offset.Unspecified }
}

@Composable
internal fun LayoutCourse(course: Course,schedule: Schedule,date: LocalDate,rows: List<TimetableRow>,columnWidthPixels: Float,model: DolphinViewModel,drag: BoardDragState,modifier: Modifier) {
    val controller=model.layout
    val selected=controller.selectedId==course.id
    val shown=controller.draft?.takeIf { it.original.id==course.id }?.next ?: course
    val density=LocalDensity.current
    val haptic=LocalHapticFeedback.current
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    fun window(point: Offset)=coordinates?.takeIf { it.isAttached }?.localToWindow(point) ?: Offset.Unspecified
    fun begin(edge: LayoutEdge,point: Offset,global: Offset=window(point)) {
        model.selectDate(date)
        if(controller.begin(schedule,course,date,edge)) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            drag.begin(global) { offset -> controller.preview((offset.x/columnWidthPixels).roundToInt(),with(density) { offset.y.toDp().value },rows) }
        }
    }
    fun end() { if(drag.active){drag.stop();controller.finish()} }
    fun cancel() { drag.stop();controller.cancelGesture() }
    fun step(edge: LayoutEdge,day: Int,vertical: Float): Boolean {
        if(!controller.begin(schedule,course,date,edge))return false
        controller.preview(day,vertical,rows);controller.finish();return true
    }
    fun stepDistance(direction: Int): Float {
        val index=if(direction<0)(shown.start-2).coerceAtLeast(0) else (shown.end).coerceAtMost(rows.lastIndex)
        return rows[index].height.coerceAtLeast(84f)*direction
    }
    val move=Modifier.pointerInput(course,date,rows) {
        awaitEachGesture {
            val down=awaitFirstDown(requireUnconsumed=false)
            try {
                val held=awaitLongPressOrCancellation(down.id)
                if(held!=null) {
                    begin(LayoutEdge.MOVE,held.position)
                    val finished=drag(held.id) { change ->
                        if(drag.active) { drag.move(change.positionChange(),window(change.position));change.consume() }
                    }
                    if(finished) { currentEvent.changes.forEach { if(it.changedToUp())it.consume() };end() } else cancel()
                } else {
                    // 未长按且父级未消费的抬手才是点击；快速滑动仍交给课表滚动。
                    val up=currentEvent.changes.firstOrNull { it.id==down.id }
                    if(up!=null && up.changedToUp()) { up.consume();model.show(course,date) }
                }
            } catch(cancelled: CancellationException) { cancel();throw cancelled }
        }
    }
    Box(modifier.onGloballyPositioned { coordinates=it }.then(move).zIndex(if(selected)2f else 0f).testTag("layout-course-${course.id}").semantics {
        onClick { model.show(course,date);true }
        onLongClick("调整课程位置") { if(controller.begin(schedule,course,date,LayoutEdge.MOVE))controller.finish();true }
        customActions=listOf(
            CustomAccessibilityAction("向前一天移动") { step(LayoutEdge.MOVE,-1,0f) },
            CustomAccessibilityAction("向后一天移动") { step(LayoutEdge.MOVE,1,0f) },
            CustomAccessibilityAction("向上一节移动") { step(LayoutEdge.MOVE,0,stepDistance(-1)) },
            CustomAccessibilityAction("向下一节移动") { step(LayoutEdge.MOVE,0,stepDistance(1)) },
        )
    }) {
        CourseCard(shown,schedule,{model.show(course,date)},Modifier.fillMaxSize().then(if(selected)Modifier.border(2.dp,MaterialTheme.colorScheme.primary,RoundedCornerShape(16.dp)) else Modifier),compact=true,interactive=false,contentPadding=PaddingValues(horizontal=6.dp,vertical=if(selected)22.dp else 8.dp))
        if(selected && !controller.busy) {
            listOf(LayoutEdge.START,LayoutEdge.END).forEach { edge ->
                val start=edge==LayoutEdge.START
                var handleCoordinates by remember(course.id,edge) { mutableStateOf<LayoutCoordinates?>(null) }
                fun handleWindow(point: Offset)=handleCoordinates?.takeIf { it.isAttached }?.localToWindow(point) ?: Offset.Unspecified
                Box(Modifier.align(if(start)Alignment.TopCenter else Alignment.BottomCenter).offset(y=if(start)(-12).dp else 12.dp).size(48.dp).testTag(if(start)"layout-start" else "layout-end").semantics {
                    contentDescription=course.name+if(start)"开始节次拖动" else "结束节次拖动"
                    customActions=listOf(
                        CustomAccessibilityAction("提前一节") { step(edge,0,stepDistance(-1)) },
                        CustomAccessibilityAction("推后一节") { step(edge,0,stepDistance(1)) },
                    )
                }.onGloballyPositioned { handleCoordinates=it }.pointerInput(course,date,rows,edge) {
                    detectDragGestures(onDragStart={begin(edge,it,handleWindow(it))},onDragEnd=::end,onDragCancel=::cancel) { change,amount ->
                        if(drag.active){change.consume();drag.move(amount,handleWindow(change.position))}
                    }
                },contentAlignment=Alignment.Center) {
                    Box(Modifier.size(28.dp,8.dp).background(MaterialTheme.colorScheme.primary,RoundedCornerShape(8.dp)))
                }
            }
        }
    }
}

@Composable
internal fun LayoutDialogs(controller: LayoutController) {
    controller.conflicts?.let { collisions -> AlertDialog(onDismissRequest=controller::cancelPending,title={Text("调整后与其他课程重叠")},text={Text(collisions.joinToString("\n") { it.name }+"\n\n仍要保存这个位置吗？")},confirmButton={TextButton(enabled=!controller.busy,onClick=controller::confirmConflict) { Text("仍然保存") }},dismissButton={TextButton(enabled=!controller.busy,onClick=controller::cancelPending) { Text("取消调整") }}) }
    controller.error?.let { error -> AlertDialog(onDismissRequest=controller::cancelPending,title={Text("位置未保存")},text={Text(error)},confirmButton={TextButton(enabled=!controller.busy,onClick=controller::cancelPending) { Text("知道了") }}) }
}
