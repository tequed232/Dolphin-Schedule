package com.dolphin.schedule.ui

import android.app.Application
import android.app.Activity
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.dolphin.schedule.data.AppDataCodec
import com.dolphin.schedule.data.AcademicCodec
import com.dolphin.schedule.data.AcademicDraft
import com.dolphin.schedule.data.AcademicDraftStore
import com.dolphin.schedule.data.NativeRepository
import com.dolphin.schedule.data.RepositoryState
import com.dolphin.schedule.academic.AcademicPolicy
import com.dolphin.schedule.importing.FileImportController
import com.dolphin.schedule.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import java.io.File
import java.util.UUID

enum class NativePage(val title: String) {
    MANAGE("课表管理"),IMPORT("导入课表"),FILE_IMPORT("从文件导入"),TERM("学期与节次"),TIMES("上课时间"),BOOKS("教材"),
    HOME("主页显示"),APPEARANCE("外观"),BACKGROUND("背景图片"),SAFE_AREA("屏幕安全区"),NOTIFICATIONS("实时通知"),
    NAVIGATION("导航与学校"),UPDATES("应用更新"),DATA("数据与备份"),ABOUT("关于")
}
data class EditPlan(val original: Course?,val baseline: Schedule,val seed: Course)
class CourseCollision(val course: Course,val collisions: List<Course>): IllegalArgumentException("课程时间冲突")

class DolphinViewModel(application: Application,private val saved: SavedStateHandle): AndroidViewModel(application) {
    val repository=NativeRepository.get(application)
    val state=repository.state
    private val mutableMessages=MutableSharedFlow<String>(extraBufferCapacity=8)
    private val modeUpdates=Channel<String>(Channel.CONFLATED)
    val messages=mutableMessages.asSharedFlow()
    var selectedDate by mutableStateOf(saved.get<String>("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now());private set
    var tab by mutableStateOf(saved.get<String>("tab")?.let { runCatching { MainTab.valueOf(it) }.getOrNull() } ?: MainTab.LIST);private set
    val pages=mutableStateListOf<NativePage>()
    var periodEdit by mutableStateOf<PeriodEditPlan?>(null)
    fun openPeriod(schedule: Schedule,index: Int) { layout.exit();if(index in schedule.periods.indices)periodEdit=PeriodEditPlan(schedule,index) }
    var detailId by mutableStateOf<String?>(null)
    var edit by mutableStateOf<EditPlan?>(null);private set
    var collision by mutableStateOf<CourseCollision?>(null)
    var busy by mutableStateOf(false);private set
    var error by mutableStateOf<String?>(null)
    private val academicStore=AcademicDraftStore(application)
    var academicPending by mutableStateOf(saved.get<String>("academicPending"));private set
    var academicDraft by mutableStateOf<AcademicDraft?>(null);private set
    var academicPreview by mutableStateOf<Schedule?>(null);private set
    var academicError by mutableStateOf<String?>(null);private set
    var academicBusy by mutableStateOf(saved.get<String>("academicDraft")!=null);private set
    var academicAcknowledged by mutableStateOf(false);private set
    var discardAcademic by mutableStateOf(false)
    private var leaveAfterDiscard=false
    private var initialized=false
    val books=BookController(application,repository,viewModelScope,saved,onClosed={ id -> detailId=id },message=::message)
    val background=BackgroundController(application,repository,viewModelScope,saved,message=::message)
    val layout=LayoutController(repository,viewModelScope,::message) { course,date -> selectDate(date.minusDays((date.dayOfWeek.value-1).toLong()).plusDays((course.day-1).toLong())) }
    val notifications=NotificationController(application,repository,viewModelScope,saved,::message)
    val support=SupportController(viewModelScope)
    var showGuide by mutableStateOf(saved.get<Boolean>("showGuide")==true);private set
    fun openGuide() { showGuide=true;saved["showGuide"]=true }
    fun finishGuide(next: String="home") {
        change(after={
            showGuide=false;saved["showGuide"]=false
            val value=(state.value as RepositoryState.Ready).snapshot.data
            if(next=="import")open(NativePage.IMPORT) else if(next=="manual")startEdit(value)
        }) { it.copy(onboarded=true) }
    }
    fun navigate(course: Course) {
        val data=(state.value as? RepositoryState.Ready)?.snapshot?.data ?: return
        if(data.settings.school.isBlank()) { detailId=null;open(NativePage.NAVIGATION);message("请先填写学校名称，才能准确导航到楼栋");return }
        notifications.navigate(course)
    }
    fun editBook(data: AppData,course: Course) { detailId=null;books.start(data,course=course,returnToCourse=true) }
    val files=FileImportController(application,repository,viewModelScope,saved,onSaved={
        pages.clear();savePages();val value=(repository.state.value as RepositoryState.Ready).snapshot.data
        selectDate(firstMonday(value.schedule.term.startDate));select(if(value.settings.timetableMode=="grid")MainTab.GRID else MainTab.LIST)
    },onLeave={back()},message=::message)

    init {
        saved.get<ArrayList<String>>("pages")?.mapNotNull { runCatching { NativePage.valueOf(it) }.getOrNull() }?.let(pages::addAll)
        saved.get<String>("edit")?.let { raw -> runCatching {
            val obj=JSONObject(raw);val baseline=AppDataCodec.decodeSchedule(obj.getJSONObject("base"))
            val draft=obj.getJSONObject("seed");val fields=draft.getJSONArray("courses").getJSONObject(0);val name=fields.getString("name")
            if(name.isBlank())fields.put("name","未命名课程")
            val seed=AppDataCodec.decodeSchedule(draft).courses.single().copy(name=name)
            edit=EditPlan(baseline.courses.firstOrNull(),baseline,seed)
        }.onFailure { error="上次课程草稿无法恢复；已保存的课程保留" } }
        reload()
        saved.get<String>("academicDraft")?.let { id -> viewModelScope.launch {
            academicBusy=true
            try { academicDraft=withContext(Dispatchers.IO) { academicStore.load(id) };rebuildAcademicPreview() }
            catch(cancelled: CancellationException) { throw cancelled }
            catch(failure: Exception) { academicError=failure.message ?: "上次识别结果无法恢复，请重新识别";saved.remove<String>("academicDraft") }
            finally { academicBusy=false }
        } }
        viewModelScope.launch { repository.warning.collect { if(it!=null)message(it) } }
        viewModelScope.launch {
            modeUpdates.receiveAsFlow().collectLatest { mode ->
                delay(350)
                if((repository.state.value as? RepositoryState.Ready)?.snapshot?.data?.settings?.timetableMode!=mode)try {
                    repository.update { if(it.settings.timetableMode==mode)it else it.copy(settings=it.settings.copy(timetableMode=mode)) }
                } catch(cancelled: CancellationException) { throw cancelled }
                catch(failure: Exception) { message(failure.message ?: "显示模式未保存，请重试") }
            }
        }
    }
    fun reload() { viewModelScope.launch {
        val result=repository.initialize()
        if(!initialized && result is RepositoryState.Ready) { initialized=true;if(saved.get<String>("tab")==null)tab=if(result.snapshot.data.settings.timetableMode=="grid") MainTab.GRID else MainTab.LIST }
    } }
    fun selectDate(date: LocalDate) { require(date.year in 1900..9999);if(firstMonday(date.toString())!=firstMonday(selectedDate.toString()))layout.exit();selectedDate=date;saved["date"]=date.toString() }
    fun moveDate(days: Long) { runCatching { selectDate(selectedDate.plusDays(days)) }.onFailure { message("已到可选日期边界") } }
    fun select(tab: MainTab) {
        if(this.tab==tab && pages.isEmpty())return
        layout.exit()
        this.tab=tab;saved["tab"]=tab.name;pages.clear();savePages()
        if(tab==MainTab.LIST || tab==MainTab.GRID)modeUpdates.trySend(tab.name.lowercase(java.util.Locale.ROOT))
    }
    fun open(page: NativePage) { layout.exit();if(pages.lastOrNull()!=page){pages+=page;savePages()} }
    fun back(): Boolean {
        if(pages.isEmpty())return false
        if(pages.last()==NativePage.BACKGROUND) {
            if(background.busy)return true
            if(background.previewBlur!=null) { background.saveBlur { if(pages.lastOrNull()==NativePage.BACKGROUND)popPage() };return true }
            background.clearPreview()
        }
        if(pages.last()==NativePage.FILE_IMPORT && files.dirty) { files.requestDiscard(true);return true }
        if(pages.last()==NativePage.IMPORT && academicDraft!=null) { requestDiscardAcademic(true);return true }
        if(academicBusy || busy)return true
        popPage();return true
    }
    private fun popPage() { pages.removeAt(pages.lastIndex);savePages() }
    private fun savePages() { saved["pages"]=ArrayList(pages.map { it.name }) }
    fun show(course: Course,date: LocalDate?=null) { layout.exit();date?.let(::selectDate);detailId=course.id }
    fun startEdit(data: AppData,course: Course?=null,temporary: Boolean=false,start: Int=1,day: LocalDate=selectedDate) {
        layout.exit()
        detailId=null
        val week=weekOf(day,data.schedule.term.startDate).coerceIn(1,data.schedule.term.weeks)
        val seed=course ?: Course(name="",day=day.dayOfWeek.value,start=start,end=if(temporary) start else minOf(start+1,data.schedule.periods.size),weeks=if(temporary)listOf(week) else (1..data.schedule.term.weeks).toList(),temporary=temporary,specificDate=if(temporary)day.toString() else null)
        val baseline=data.schedule.copy(courses=course?.let(::listOf) ?: emptyList())
        edit=EditPlan(course,baseline,seed)
        saved["edit"]=JSONObject().put("base",AppDataCodec.encodeSchedule(baseline)).put("seed",AppDataCodec.encodeSchedule(baseline.copy(courses=listOf(seed)))).toString()
    }
    fun cancelEdit() { if(!busy){edit=null;collision=null;saved.remove<String>("edit")} }
    fun saveCourse(course: Course,allowConflict: Boolean=false) {
        val plan=edit ?: return
        change("课程已保存",after={cancelEdit()}) { data ->
            val original=data.schedule.courses.find { it.id==course.id }
            require(original==plan.original && data.schedule.term==plan.baseline.term && data.schedule.periods==plan.baseline.periods) { "课程或作息已变化，请保留草稿并重新核对" }
            val overlaps=data.schedule.courses.filter { coursesConflict(course,it,data.schedule) }
            if(overlaps.isNotEmpty() && !allowConflict)throw CourseCollision(course,overlaps)
            data.copy(schedule=validateSchedule(data.schedule.copy(courses=data.schedule.courses.filterNot { it.id==course.id }+course)))
        }
    }
    fun deleteCourse(course: Course) { change("课程已删除",after={detailId=null;cancelEdit()}) { data ->
        require(data.schedule.courses.find { it.id==course.id }==course) { "课程已更新，请重新打开后删除" }
        data.copy(schedule=data.schedule.copy(courses=data.schedule.courses.filterNot { it.id==course.id }))
    } }
    fun prepareAcademicRequest(clear: Boolean=false): String? {
        if(busy || academicBusy || academicPending!=null || academicDraft!=null)return null
        val id=UUID.randomUUID().toString();academicPending=id;saved["academicPending"]=id;saved["academicClear"]=clear;academicError=null
        return id
    }
    fun academicLaunchFailed() { academicPending=null;saved.remove<String>("academicPending");saved.remove<Boolean>("academicClear");academicError="无法打开教务页面，请稍后重试" }
    fun academicResult(resultCode: Int,result: Intent?) {
        val id=academicPending ?: return
        val clear=saved.get<Boolean>("academicClear")==true
        academicPending=null;saved.remove<String>("academicPending");saved.remove<Boolean>("academicClear")
        if(!AcademicPolicy.requestId(id))return
        viewModelScope.launch {
            academicBusy=true
            try {
                if(resultCode==Activity.RESULT_CANCELED)return@launch
                require(resultCode==Activity.RESULT_OK && result?.getStringExtra("requestId")==id) { "教务结果不匹配，请重新识别；原课表保留" }
                if(clear) { require(result.getBooleanExtra("cleared",false)) { "登录清理未完成，请重试" };message("教务登录已清除");return@launch }
                require(!result.getBooleanExtra("cleared",false)) { "未收到课表，请重新识别" }
                val current=(repository.state.value as? RepositoryState.Ready)?.snapshot?.data ?: error("本地课表尚未读取完成")
                val draft=withContext(Dispatchers.IO) {
                    val file=File(getApplication<Application>().cacheDir,"academic-$id.json")
                    require(file.length() in 1..AcademicPolicy.MAX_BYTES.toLong()) { "识别结果已失效，请重新识别；原课表保留" }
                    val raw=file.readText(Charsets.UTF_8)
                    AcademicDraft(id,current.schedule,raw,AcademicCodec.decode(raw)).also { academicStore.save(it);file.delete() }
                }
                academicDraft=draft;saved["academicDraft"]=id;academicAcknowledged=false;rebuildAcademicPreview()
                if(pages.lastOrNull()!=NativePage.IMPORT)open(NativePage.IMPORT)
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(failure: Exception) { academicError=failure.message ?: "教务结果读取失败，原课表保留" }
            finally {
                withContext(NonCancellable+Dispatchers.IO) { File(getApplication<Application>().cacheDir,"academic-$id.json").delete() }
                academicBusy=false
            }
        }
    }
    private fun rebuildAcademicPreview() {
        academicAcknowledged=false
        val draft=academicDraft ?: return
        if(draft.confirmedDate==null && draft.snapshot.termStartDate==null) { academicPreview=null;academicError=null;return }
        runCatching { AcademicImporter.convert(draft.snapshot,draft.baseline,draft.confirmedDate) }
            .onSuccess { academicPreview=it;academicError=null }.onFailure { academicPreview=null;academicError=it.message ?: "课表内容无法识别" }
    }
    fun acknowledgeAcademic(checked: Boolean) { academicAcknowledged=checked && academicPreview!=null && !academicBusy }
    fun invalidateAcademicAcknowledgment() { academicAcknowledged=false }
    fun updateAcademicDate(date: String) {
        val draft=academicDraft ?: return
        if(academicBusy || busy)return
        academicAcknowledged=false
        viewModelScope.launch {
            academicBusy=true
            try {
                require(runCatching { LocalDate.parse(date).year in 1900..9999 }.getOrDefault(false)) { "请选择有效的开学日期" }
                val changed=draft.copy(confirmedDate=date)
                withContext(Dispatchers.IO) { academicStore.save(changed) }
                academicDraft=changed;rebuildAcademicPreview()
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(failure: Exception) { academicError=failure.message ?: "日期保存失败，识别结果保留" }
            finally { academicBusy=false }
        }
    }
    fun applyAcademicImport() {
        val draft=academicDraft ?: return;val preview=academicPreview ?: return
        if(!academicAcknowledged || academicBusy)return
        change("课表已导入",expected=draft.baseline,after={
            clearAcademicDraft();pages.clear();savePages();selectDate(firstMonday(preview.term.startDate))
            val preferences=(repository.state.value as RepositoryState.Ready).snapshot.data.settings
            select(if(preferences.timetableMode=="grid")MainTab.GRID else MainTab.LIST)
        }) { AcademicImporter.apply(it,preview) }
    }
    fun requestDiscardAcademic(leave: Boolean=false) { if(!busy && !academicBusy) { leaveAfterDiscard=leave;discardAcademic=true } }
    fun confirmDiscardAcademic() { if(!busy && !academicBusy) { clearAcademicDraft();discardAcademic=false;if(leaveAfterDiscard)back() } }
    private fun clearAcademicDraft() {
        val id=academicDraft?.id;academicDraft=null;academicPreview=null;academicError=null;academicAcknowledged=false;saved.remove<String>("academicDraft")
        if(id!=null)viewModelScope.launch(Dispatchers.IO) { runCatching { academicStore.delete(id) } }
    }
    fun change(successMessage: String?=null,expected: Schedule?=null,after: ()->Unit={},transform: (AppData)->AppData) {
        if(busy)return
        busy=true
        viewModelScope.launch {
            try { repository.update(expected,transform);busy=false;after();if(successMessage!=null)message(successMessage) }
            catch(cancelled: CancellationException) { throw cancelled }
            catch(conflict: CourseCollision) { collision=conflict }
            catch(failure: Exception) { error=failure.message ?: "保存未完成，原数据保留" }
            finally { busy=false }
        }
    }
    fun message(text: String) { mutableMessages.tryEmit(text) }
}
