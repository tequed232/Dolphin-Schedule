package com.dolphin.schedule.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dolphin.schedule.data.RepositoryState
import com.dolphin.schedule.domain.Preferences
import com.dolphin.schedule.domain.homeCourse
import com.dolphin.schedule.ui.appearance.SavedBackground
import com.dolphin.schedule.ui.books.NativeBookEditor
import com.dolphin.schedule.ui.common.DolphinTheme
import com.dolphin.schedule.ui.common.SystemBarAppearance
import com.dolphin.schedule.ui.common.dolphinInsets
import com.dolphin.schedule.ui.common.observeDolphinPerformance
import com.dolphin.schedule.ui.common.rememberNativePerformance
import com.dolphin.schedule.ui.navigation.CapsuleNavigation
import com.dolphin.schedule.ui.MainTab
import com.dolphin.schedule.ui.navigation.rememberPageMotion
import com.dolphin.schedule.ui.notifications.NativeNotificationPermission
import com.dolphin.schedule.ui.settings.NativeSettingsPage
import com.dolphin.schedule.ui.settings.NativeSettingsScreen
import com.dolphin.schedule.ui.support.NativeWelcomeScreen
import com.dolphin.schedule.ui.timetable.CourseDetails
import com.dolphin.schedule.ui.timetable.CourseEditor
import com.dolphin.schedule.ui.timetable.HomeIcon
import com.dolphin.schedule.ui.timetable.HomeSymbol
import com.dolphin.schedule.ui.timetable.LayoutDialogs
import com.dolphin.schedule.ui.timetable.NativeDayScreen
import com.dolphin.schedule.ui.timetable.NativeSearchScreen
import com.dolphin.schedule.ui.timetable.NativeWeekScreen
import com.dolphin.schedule.ui.timetable.PeriodEditor
import com.dolphin.schedule.ui.timetable.rememberTimetableNow

/** 原生业务入口，界面与课程规则均由 Kotlin 实现。 */
@Composable
fun DolphinApp(model: DolphinViewModel,onExit: ()->Unit,onAcademic: (Boolean)->Unit={}) {
    NativeNotificationPermission(model.notifications)
    val state by model.state.collectAsStateWithLifecycle()
    val data=(state as? RepositoryState.Ready)?.snapshot?.data
    val preferences=data?.settings ?: Preferences()
    val density=LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density,density.fontScale*preferences.scale)) {
        DolphinTheme(preferences) {
            SystemBarAppearance()
            val snack=remember { SnackbarHostState() }
            LaunchedEffect(model) { model.messages.collect { snack.showSnackbar(it) } }
            val page=model.pages.lastOrNull()
            val performance=rememberNativePerformance(preferences.performance)
            val pageMotion=rememberPageMotion(model.tab,page,model.pages.size,performance.reduced)
            val savedTabs=rememberSaveableStateHolder()
            val safeInsets=dolphinInsets(preferences)
            val safeBottom=with(LocalDensity.current) { safeInsets.getBottom(this).toDp() }
            val keyboard=WindowInsets.ime.getBottom(LocalDensity.current)>0
            BackHandler(enabled=page!=null && model.edit==null && model.detailId==null && !model.books.open && model.periodEdit==null) { model.back() }
            BackHandler(enabled=page==null && model.layout.selectedId!=null && model.edit==null && model.detailId==null && model.periodEdit==null) { model.layout.exit() }
            BackHandler(enabled=model.showGuide) { model.finishGuide() }
            val performanceObserver=if(data?.background!=null && preferences.backgroundEnabled)Modifier.observeDolphinPerformance(performance) else Modifier
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).then(performanceObserver)) {
            data?.let { SavedBackground(it,blurOverride=model.background.previewBlur,reduced=performance.reduced) }
            Scaffold(
                containerColor=androidx.compose.ui.graphics.Color.Transparent,
                contentColor=MaterialTheme.colorScheme.onBackground,
                contentWindowInsets=if(page==null && !keyboard)safeInsets.only(WindowInsetsSides.Top+WindowInsetsSides.Horizontal) else safeInsets,
                snackbarHost={SnackbarHost(snack,Modifier.padding(bottom=if(page==null && !keyboard)72.dp else 0.dp))},
            ) { insets ->
                Box(Modifier.fillMaxSize().padding(insets)) {
                    when(val current=state) {
                        RepositoryState.Loading -> Column(Modifier.align(Alignment.Center),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
                            CircularProgressIndicator();Text("正在读取本地课表")
                        }
                        is RepositoryState.Failed -> Column(Modifier.align(Alignment.Center).padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
                            Text("课表暂时无法读取",style=MaterialTheme.typography.titleLarge)
                            Text(current.message);Button(onClick=model::reload) { Text("重试") };TextButton(onClick=onExit) { Text("返回") }
                        }
                        is RepositoryState.Ready -> {
                            val value=current.snapshot.data
                            val welcome=!value.onboarded || model.showGuide
                            Column(Modifier.fillMaxSize().then(pageMotion)) {
                                if(welcome)NativeWelcomeScreen(model.busy,{model.finishGuide("import")},{model.finishGuide("manual")},{model.finishGuide()})
                                else if(page!=null) NativeSettingsPage(page,value,model,onAcademic,performance.reduced)
                                else savedTabs.SaveableStateProvider(model.tab) { when(model.tab) {
                                    MainTab.LIST -> NativeDayScreen(value,model,72.dp+safeBottom)
                                    MainTab.GRID -> NativeWeekScreen(value,model,72.dp+safeBottom)
                                    MainTab.SEARCH -> NativeSearchScreen(value,model,if(keyboard)0.dp else 72.dp+safeBottom)
                                    MainTab.SETTINGS -> NativeSettingsScreen(value,model,72.dp+safeBottom)
                                } }
                            }
                            if(!welcome && page==null && !keyboard && !model.books.open)CapsuleNavigation(model.tab,model::select,Modifier.align(Alignment.BottomCenter).padding(start=16.dp,end=16.dp,top=8.dp,bottom=8.dp+safeBottom).widthIn(max=320.dp).fillMaxWidth())
                            if(!welcome && page==null && !keyboard && model.tab in listOf(MainTab.LIST,MainTab.GRID) && value.settings.showNavigation && model.layout.selectedId==null) {
                                val now=rememberTimetableNow()
                                homeCourse(value.schedule,model.selectedDate,now)?.let { next ->
                                    SmallFloatingActionButton(onClick={model.navigate(next.course)},modifier=Modifier.align(Alignment.BottomEnd).padding(end=20.dp,bottom=80.dp+safeBottom).semantics { contentDescription="导航课程" },containerColor=MaterialTheme.colorScheme.primaryContainer) { HomeSymbol(HomeIcon.NAVIGATE) }
                                }
                            }
                            model.detailId?.let { id -> value.schedule.courses.find { it.id==id }?.let { CourseDetails(it,value,model) } }
                            model.edit?.let { plan -> key(plan.seed.id) { CourseEditor(plan,value,model) } }
                            model.periodEdit?.let { plan -> key(plan.index) { PeriodEditor(plan,model) } }
                            if(model.books.open)NativeBookEditor(model.books,value.settings)
                        }
                    }
                }
            }
            }
            model.error?.let { message -> AlertDialog(onDismissRequest={model.error=null},title={Text("操作未完成")},text={Text(message)},confirmButton={TextButton(onClick={model.error=null}) { Text("知道了") }}) }
            LayoutDialogs(model.layout)
            model.notifications.info?.let { text -> AlertDialog(onDismissRequest={model.notifications.info=null},title={Text("通知状态")},text={Text(text)},confirmButton={TextButton(onClick={model.notifications.info=null}) { Text("知道了") }}) }
            if(model.discardAcademic)AlertDialog(onDismissRequest={model.discardAcademic=false},title={Text("放弃这次识别？")},text={Text("识别结果尚未导入，已保存的课表不会改变。")},confirmButton={TextButton(onClick=model::confirmDiscardAcademic) { Text("放弃识别") }},dismissButton={TextButton(onClick={model.discardAcademic=false}) { Text("继续核对") }})
            model.collision?.let { collision -> AlertDialog(
                onDismissRequest={model.collision=null},title={Text("与现有课程时间重叠")},
                text={Text(collision.collisions.joinToString("\n") { it.name }+"\n\n仍要保留这门课程吗？")},
                confirmButton={TextButton(enabled=!model.busy,onClick={model.collision=null;model.saveCourse(collision.course,true)}) { Text("仍然保存") }},
                dismissButton={TextButton(onClick={model.collision=null}) { Text("继续编辑") }},
            ) }
        }
    }
}
