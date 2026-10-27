package com.dolphin.schedule.ui.timetable

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dolphin.schedule.R
import com.dolphin.schedule.ui.common.DolphinSymbol
import com.dolphin.schedule.domain.*
import com.dolphin.schedule.ui.DolphinViewModel
import com.dolphin.schedule.ui.NativePage
import java.time.*
import java.time.format.DateTimeFormatter

internal val WeekNames=listOf("周一","周二","周三","周四","周五","周六","周日")

/** 复现原来的首页层级，月历和导入仍由显式点击打开。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeHeader(data: AppData,model: DolphinViewModel,weekly: Boolean) {
    var calendar by rememberSaveable { mutableStateOf(false) }
    val date=model.selectedDate
    val week=weekOf(date,data.schedule.term.startDate)
    val currentWeek=weekOf(LocalDate.now(),data.schedule.term.startDate)==week
    val colors=MaterialTheme.colorScheme
    Surface(color=if(weekly)colors.surface else androidx.compose.ui.graphics.Color.Transparent,shape=RoundedCornerShape(24.dp),modifier=Modifier.fillMaxWidth().testTag("home-header")) {
        Column(Modifier.padding(if(weekly)14.dp else 0.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                TextButton(onClick={calendar=true},modifier=Modifier.weight(1f).semantics { contentDescription="打开月历" },shape=RoundedCornerShape(0.dp),contentPadding=PaddingValues(vertical=6.dp),colors=ButtonDefaults.textButtonColors(contentColor=colors.onSurface)) {
                    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                        Text(if(weekly)(if(week in 1..data.schedule.term.weeks)"第 $week 周" else "学期外")+" ${WeekNames[date.dayOfWeek.value-1]}" else date.format(DateTimeFormatter.ofPattern("M月d日")),fontSize=if(weekly)20.sp else 30.sp,fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis)
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                            Text(if(weekly)date.format(DateTimeFormatter.ofPattern("yyyy/M/d")) else "星期${"一二三四五六日"[date.dayOfWeek.value-1]} · ${coursesOn(data.schedule,date).size} 门课程",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                            HomeSymbol(HomeIcon.CALENDAR,Modifier.size(14.dp),colors.onSurfaceVariant)
                        }
                    }
                }
                HeaderAction("添加课程",HomeIcon.ADD,!weekly) { model.startEdit(data) }
                HeaderAction("导入课表",HomeIcon.IMPORT,!weekly) { model.open(NativePage.IMPORT) }
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                TextButton(onClick={model.open(NativePage.MANAGE)},modifier=Modifier.weight(1f),contentPadding=PaddingValues(0.dp)) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                        HomeSymbol(HomeIcon.CALENDAR,Modifier.size(18.dp));Text("课表管理",style=MaterialTheme.typography.labelLarge)
                    }
                }
                IconButton(onClick={model.moveDate(-7)},modifier=Modifier.semantics { contentDescription="上一周" }) { HomeSymbol(HomeIcon.BACK) }
                TextButton(onClick={model.selectDate(LocalDate.now())},contentPadding=PaddingValues(horizontal=6.dp),modifier=Modifier.semantics { contentDescription="回到今天" }) { Text(if(currentWeek)"本周" else "回到今天",style=MaterialTheme.typography.labelLarge) }
                IconButton(onClick={model.moveDate(7)},modifier=Modifier.semantics { contentDescription="下一周" }) { HomeSymbol(HomeIcon.NEXT) }
            }
            if(weekly)Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(if(model.layout.selectedId!=null)"拖动课程或上下把手；点空白或返回退出。" else "点选查看，长按调整；滑到边缘继续翻周。",Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                if(model.layout.selectedId!=null)TextButton(onClick={model.layout.exit()},modifier=Modifier.testTag("layout-done")) { Text("完成") }
            }
        }
    }
    if(calendar) {
        val picker=rememberDatePickerState(initialSelectedDateMillis=date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),yearRange=1900..9999)
        DatePickerDialog(onDismissRequest={calendar=false},confirmButton={TextButton(onClick={picker.selectedDateMillis?.let { model.selectDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) };calendar=false}) { Text("选择日期") }},dismissButton={TextButton(onClick={calendar=false}) { Text("取消") }}) { DatePicker(picker) }
    }
}

@Composable
private fun HeaderAction(label: String,icon: HomeIcon,circular: Boolean,onClick: ()->Unit) {
    Surface(onClick=onClick,modifier=Modifier.size(48.dp).semantics { contentDescription=label },shape=CircleShape,color=if(circular)MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent,contentColor=MaterialTheme.colorScheme.primary) {
        Box(contentAlignment=Alignment.Center) { HomeSymbol(icon,Modifier.size(24.dp)) }
    }
}

@Composable
internal fun HomeDateStrip(data: AppData,model: DolphinViewModel) {
    val date=model.selectedDate
    val range=remember(data.settings.showWeekend,date.dayOfWeek.value>5) { DateStripRange(data.settings.showWeekend || date.dayOfWeek.value>5) }
    val index=range.indexOf(date)
    Surface(shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth().testTag("home-date-panel")) {
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("${date.year} 年 ${date.monthValue} 月",Modifier.padding(start=4.dp),fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.titleMedium)
            BoxWithConstraints {
                val width=(maxWidth-18.dp)/4
                val offset=with(LocalDensity.current) { -((maxWidth-width)/2).roundToPx() }
                val scroll=rememberLazyListState(initialFirstVisibleItemIndex=index)
                LaunchedEffect(index,offset,range) { scroll.scrollToItem(index,offset) }
                LazyRow(state=scroll,horizontalArrangement=Arrangement.spacedBy(6.dp),modifier=Modifier.fillMaxWidth().testTag("home-date-strip")) {
                    items(range.size,key={range.dateAt(it).toEpochDay()}) { i ->
                        val day=range.dateAt(i);val selected=day==date
                        Surface(selected=selected,onClick={model.selectDate(day)},modifier=Modifier.width(width).heightIn(min=94.dp).semantics { contentDescription="${day.year}年${day.monthValue}月${day.dayOfMonth}日 ${WeekNames[day.dayOfWeek.value-1]}" },shape=RoundedCornerShape(23.dp),color=if(selected)MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,contentColor=if(selected)MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant) {
                            Column(Modifier.padding(vertical=10.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                Text(WeekNames[day.dayOfWeek.value-1],style=MaterialTheme.typography.bodySmall)
                                Text(day.dayOfMonth.toString().padStart(2,'0'),fontSize=30.sp,fontWeight=FontWeight.SemiBold,color=if(selected)MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                                val todayDot=if(selected)MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                                Canvas(Modifier.size(4.dp)) { if(day==LocalDate.now())drawCircle(todayDot) }
                            }
                        }
                    }
                }
            }
            Text("左右滑动选日期，或打开月历跳转",Modifier.align(Alignment.CenterHorizontally),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun NextCourseCard(value: HomeCourse,data: AppData,model: DolphinViewModel) {
    val c=value.course
    Surface(onClick={model.show(c)},shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.primaryContainer,modifier=Modifier.fillMaxWidth().testTag("home-next-course")) {
        Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surface,contentColor=MaterialTheme.colorScheme.primary) { Box(Modifier.size(40.dp),contentAlignment=Alignment.Center) { HomeSymbol(HomeIcon.CLOCK) } }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text(value.title,color=MaterialTheme.colorScheme.onPrimaryContainer,style=MaterialTheme.typography.labelMedium)
                Text(c.name,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.onPrimaryContainer,maxLines=2,overflow=TextOverflow.Ellipsis)
                Text(c.room.ifBlank { "教室待填写" },style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha=.85f),maxLines=2,overflow=TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(6.dp)) {
                if(data.settings.showTimes)Text(data.schedule.periods[c.start-1].start,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.onPrimaryContainer)
                Text("查看详情",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

internal enum class HomeIcon { ADD,IMPORT,CALENDAR,CLOCK,BACK,NEXT,NAVIGATE }

/** 首页和设置共用同一套 24dp 圆角 Material Symbols。 */
@Composable
internal fun HomeSymbol(icon: HomeIcon,modifier: Modifier=Modifier,color: androidx.compose.ui.graphics.Color=LocalContentColor.current) {
    val resource=when(icon) {
        HomeIcon.ADD->R.drawable.ms_add
        HomeIcon.CLOCK->R.drawable.ms_schedule
        HomeIcon.CALENDAR->R.drawable.ms_calendar_month
        HomeIcon.IMPORT->R.drawable.ms_upload_file
        HomeIcon.BACK->R.drawable.ms_chevron_left
        HomeIcon.NEXT->R.drawable.ms_chevron_right
        HomeIcon.NAVIGATE->R.drawable.ms_near_me
    }
    DolphinSymbol(resource,modifier,color)
}
