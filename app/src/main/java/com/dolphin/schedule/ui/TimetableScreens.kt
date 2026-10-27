package com.dolphin.schedule.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dolphin.schedule.domain.*
import androidx.compose.ui.input.pointer.PointerEventPass
import kotlin.math.abs
import java.time.*
import java.time.temporal.ChronoUnit

@Composable
fun NativeDayScreen(data: AppData,model: DolphinViewModel,bottomClearance: androidx.compose.ui.unit.Dp=0.dp) {
    val date=model.selectedDate
    var drag by remember { mutableFloatStateOf(0f) }
    val threshold=with(LocalDensity.current) { 72.dp.toPx() }
    val courses=remember(data.schedule,date) { coursesOn(data.schedule,date) }
    val now by produceState(LocalDateTime.now()) { while(true) { kotlinx.coroutines.delay(30_000);value=LocalDateTime.now() } }
    val next=homeCourse(data.schedule,date,now)
    Column {
        LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=20.dp,end=20.dp,top=12.dp,bottom=20.dp+bottomClearance),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item { HomeHeader(data,model,false) }
            item { HomeDateStrip(data,model) }
            item {
                val week=weekOf(date,data.schedule.term.startDate)
                FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text("下一站，知识的海洋。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${data.schedule.term.name} · "+if(week in 1..data.schedule.term.weeks)"第 $week 周" else "学期外",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if(next!=null)item { NextCourseCard(next,data,model) }
            else if(courses.isNotEmpty())item { Text(if(date==now.toLocalDate())"今天的课程已结束，辛苦啦。" else "点选课程，查看教材、教室与上课周次。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
            item { Column(Modifier.padding(top=8.dp,bottom=4.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text("日课表",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                Text("点选课程查看详情",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            } }
            item { Column(Modifier.pointerInput(date) {
            detectHorizontalDragGestures(onDragStart={drag=0f},onDragCancel={drag=0f},onDragEnd={if(kotlin.math.abs(drag)>=threshold)model.moveDate(if(drag<0)1 else -1);drag=0f}) { change,amount -> change.consume();drag+=amount }
            },verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(courses.isEmpty())Surface(shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(22.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("今天的时间，由你安排",style=MaterialTheme.typography.titleMedium)
                    Text("长按空白节次或点击右上角，添加一门课程。",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick={model.open(NativePage.IMPORT)}) { Text("导入课表") }
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                TimetableBoard(data,model,listOf(date),maxWidth-72.dp,compact=false)
            }
            } }
            val other=data.schedule.courses.filter { it.specificDate==null && it.day==date.dayOfWeek.value && it !in courses }
            if(other.isNotEmpty())item {
                var expanded by rememberSaveable(date.toString()) { mutableStateOf(false) }
                TextButton(onClick={expanded=!expanded}) { Text(if(expanded) "收起其他周课程" else "查看其他周课程 · ${other.size}") }
                if(expanded)Column(verticalArrangement=Arrangement.spacedBy(8.dp)) { other.forEach { c -> CourseCard(c,data.schedule,{model.show(c)},Modifier.fillMaxWidth()) } }
            }
        }
    }
}

@Composable
fun NativeWeekScreen(data: AppData,model: DolphinViewModel,bottomClearance: androidx.compose.ui.unit.Dp=0.dp) {
    val first=remember { LocalDate.of(1900,1,1) }
    val count=remember { (ChronoUnit.DAYS.between(first,LocalDate.of(9999,12,31))/7+1).toInt() }
    fun pageOf(date: LocalDate)=(ChronoUnit.DAYS.between(first,date)/7).toInt().coerceIn(0,count-1)
    val pager=rememberPagerState(initialPage=pageOf(model.selectedDate),pageCount={count})
    val scrolls=remember { mutableMapOf<Int,ScrollState>() }
    val gesture=remember { WeekBrowseGesture() }
    val currentModel by rememberUpdatedState(model)
    val defaultFling=ScrollableDefaults.flingBehavior()
    val fling=remember(pager,defaultFling) { object: FlingBehavior {
        override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
            val active=gesture.active ?: return initialVelocity
            try {
                if(!active.pages)return with(defaultFling) { performFling(initialVelocity) }
                val offset=pager.currentPage-active.origin+pager.currentPageOffsetFraction
                val velocityPage=when { initialVelocity < -400f -> 1;initialVelocity > 400f -> -1;else -> 0 }
                val step=when {
                    velocityPage!=0 && (offset*velocityPage>=0f || abs(offset)<.15f) -> velocityPage
                    velocityPage!=0 -> 0
                    abs(offset)>=.22f -> if(offset>0f)1 else -1
                    else -> 0
                }
                val target=(active.origin+step).coerceIn(0,count-1)
                // 此动画属于同一个 scrollable 事务，新手势会取消它，不会留下第二层惯性。
                pager.animateScrollToPage(target,animationSpec=tween(220))
                val selected=currentModel.selectedDate
                if(pageOf(selected)!=target)currentModel.selectDate(first.plusWeeks(target.toLong()).plusDays((selected.dayOfWeek.value-1).toLong()).coerceAtMost(LocalDate.of(9999,12,31)))
                return 0f
            } finally { if(gesture.active===active)gesture.active=null }
        }
    } }
    val browse=rememberScrollableState { delta ->
        val active=gesture.active ?: run {
            val horizontal=scrolls[pager.currentPage]
            val interior=horizontal!=null && (if(delta<0f)horizontal.canScrollForward else horizontal.canScrollBackward)
            WeekBrowseGesture.Active(pager.currentPage,horizontal,!interior || abs(pager.currentPageOffsetFraction)>.001f).also { gesture.active=it }
        }
        if(active.pages) {
            val size=pager.layoutInfo.pageSize.toFloat()
            if(size<=0f)0f else {
                val offset=pager.currentPage-active.origin+pager.currentPageOffsetFraction
                val next=(offset-delta/size).coerceIn(-1f,1f)
                -pager.dispatchRawDelta((next-offset)*size)
            }
        } else -(active.horizontal?.dispatchRawDelta(-delta) ?: 0f)
    }
    val requestedWeek=pageOf(model.selectedDate)
    LaunchedEffect(requestedWeek) {
        if(requestedWeek!=pager.currentPage || (!browse.isScrollInProgress && abs(pager.currentPageOffsetFraction)>.001f)) {
            browse.stopScroll()
            gesture.active=null
            if(abs(requestedWeek-pager.currentPage)==1)pager.animateScrollToPage(requestedWeek,animationSpec=tween(220)) else pager.scrollToPage(requestedWeek)
        }
    }
    Column {
        Box(Modifier.padding(horizontal=20.dp,vertical=12.dp)) { HomeHeader(data,model,true) }
        HorizontalPager(pager,Modifier.fillMaxSize().testTag("week-pager").pointerInput(gesture) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
                gesture.active=null
                do { val event=awaitPointerEvent(PointerEventPass.Initial) } while(event.changes.any { it.pressed })
            }
        }.scrollable(browse,Orientation.Horizontal,flingBehavior=fling),key={it},beyondViewportPageCount=0,userScrollEnabled=false) { page ->
            val monday=first.plusWeeks(page.toLong())
            val days=remember(monday) { (0 until 7).map { monday.plusDays(it.toLong()) }.filter { it.year in 1900..9999 } }
            val horizontal=rememberScrollState()
            val vertical=rememberScrollState()
            DisposableEffect(page,horizontal) { scrolls[page]=horizontal;onDispose { if(scrolls[page]===horizontal)scrolls.remove(page) } }
            BoxWithConstraints(Modifier.padding(horizontal=20.dp)) {
            val columnWidth=maxOf(64.dp,(maxWidth-52.dp)/5)
            Column {
                Row(Modifier.fillMaxWidth()) {
                    Surface(Modifier.width(52.dp).height(56.dp).padding(2.dp),shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surface) { Box(contentAlignment=Alignment.Center) { Text("${monday.monthValue}月",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) } }
                    Row(Modifier.horizontalScroll(horizontal,enabled=false).testTag("week-days-$monday")) { days.forEach { date ->
                        Column(Modifier.width(columnWidth).height(56.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(3.dp)) {
                            Text(WeekNames[date.dayOfWeek.value-1],style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Surface(onClick={model.selectDate(date)},modifier=Modifier.size(32.dp),color=if(date==model.selectedDate)MaterialTheme.colorScheme.primary else Color.Transparent,contentColor=if(date==model.selectedDate)MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,shape=RoundedCornerShape(11.dp)) {
                                Box(contentAlignment=Alignment.Center) { Text(date.dayOfMonth.toString(),fontWeight=FontWeight.Bold,fontSize=20.sp) }
                            }
                        }
                    } }
                }
                Column(Modifier.fillMaxWidth().verticalScroll(vertical)) {
                    TimetableBoard(data,model,days,columnWidth,horizontal,vertical,horizontalGesturesEnabled=false)
                    Spacer(Modifier.height(20.dp+bottomClearance))
                }
            }
        } }
    }
}

/** 同一条手势只交给一个横向容器：先浏览星期，触边后的下一次向外滑才翻周。 */
private class WeekBrowseGesture {
    class Active(val origin: Int,val horizontal: ScrollState?,val pages: Boolean)
    var active: Active?=null
}

@Composable
fun CourseCard(course: Course,schedule: Schedule,onClick: ()->Unit,modifier: Modifier=Modifier,compact: Boolean=false,onLongClick: (() -> Unit)?=null,interactive: Boolean=true,contentPadding: PaddingValues=PaddingValues(horizontal=if(compact)6.dp else 16.dp,vertical=if(compact)8.dp else 16.dp)) {
    val colors=coursePalette(course.color)
    Surface(if(interactive)modifier.combinedClickable(onClick=onClick,onLongClick=onLongClick) else modifier,shape=RoundedCornerShape(if(compact)16.dp else 20.dp),color=colors.first,contentColor=colors.second) {
        Column(Modifier.padding(contentPadding),verticalArrangement=Arrangement.spacedBy(if(compact)4.dp else 6.dp)) {
            Text(course.name,fontWeight=FontWeight.SemiBold,style=if(compact)MaterialTheme.typography.labelLarge.copy(fontSize=12.sp,lineHeight=17.sp) else MaterialTheme.typography.titleMedium,maxLines=if(compact)3 else 2,overflow=TextOverflow.Ellipsis)
            if(course.room.isNotBlank())Text(course.room,style=if(compact)MaterialTheme.typography.bodySmall.copy(fontSize=10.sp,lineHeight=14.sp) else MaterialTheme.typography.bodySmall,maxLines=if(compact)3 else 1,overflow=TextOverflow.Ellipsis)
            if(compact)Spacer(Modifier.weight(1f))
            if(course.teacher.isNotBlank())Text(course.teacher,style=if(compact)MaterialTheme.typography.bodySmall.copy(fontSize=10.sp,lineHeight=14.sp) else MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)
            if(!compact)Text(coursePeriod(course,schedule),style=MaterialTheme.typography.labelMedium)
            if(course.temporary)Text("临时课程",style=MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
internal fun coursePalette(name: String): Pair<Color,Color> {
    val dark=MaterialTheme.colorScheme.background.luminance()<.3f
    val light=when(name) { "lavender"->0xFFEEEBF2 to 0xFF5C4E70;"peach"->0xFFF3ECE4 to 0xFF6D503A;"blue"->0xFFE8EEF3 to 0xFF405E75;"rose"->0xFFF3E9ED to 0xFF784B5C;else->0xFFE7EEE7 to 0xFF35533E }
    val night=when(name) { "lavender"->0xFF302D39 to 0xFFD5CFE1;"peach"->0xFF38312B to 0xFFE2D0BF;"blue"->0xFF29343D to 0xFFC7D8E4;"rose"->0xFF392E34 to 0xFFE0CBD3;else->0xFF26342D to 0xFFC2D8C7 }
    val pair=if(dark)night else light
    return Color(pair.first) to Color(pair.second)
}

@Composable
fun NativeSearchScreen(data: AppData,model: DolphinViewModel,bottomClearance: androidx.compose.ui.unit.Dp=0.dp) {
    var query by rememberSaveable { mutableStateOf("") }
    val results=remember(query,data.schedule.courses) { val q=query.trim();data.schedule.courses.filter { q.isEmpty() || listOf(it.name,it.teacher,it.room).any { text -> text.contains(q,true) } }.sortedWith(compareBy<Course>{it.day}.thenBy { it.start }.thenBy { it.name }) }
    Column(Modifier.fillMaxSize().padding(horizontal=20.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical=16.dp),verticalAlignment=Alignment.CenterVertically) { Text("搜索",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f));TextButton(onClick={model.startEdit(data)}) { Text("添加课程") } }
        OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),placeholder={Text("课程、教师或教室")},singleLine=true,shape=RoundedCornerShape(24.dp))
        Text("${results.size} 门课程",Modifier.padding(vertical=16.dp),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=20.dp+bottomClearance)) {
            items(results,key={it.id}) { c ->
                Column { CourseCard(c,data.schedule,{model.show(c)},Modifier.fillMaxWidth());Text("${WeekNames[c.day-1]} · ${if(c.specificDate!=null)c.specificDate else "第 ${compactWeeks(c.weeks).ifBlank { "无" }} 周"}",Modifier.padding(start=16.dp,top=5.dp),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if(results.isEmpty())item { Text(if(query.isBlank())"还没有课程，添加后即可在这里查找。" else "没有找到相关课程",Modifier.padding(vertical=30.dp),color=MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
