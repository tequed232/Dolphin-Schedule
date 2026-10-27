package com.dolphin.schedule.ui

import androidx.compose.runtime.*
import com.dolphin.schedule.data.NativeRepository
import com.dolphin.schedule.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate

data class LayoutDraft(val baseline: Schedule,val original: Course,val date: LocalDate,val edge: LayoutEdge,val next: Course=original)

/** 手势预览不落库；松手才提交，并在同一事务中复核原课程、作息与最新冲突。 */
class LayoutController(private val repository: NativeRepository,private val scope: CoroutineScope,private val message: (String)->Unit,private val onMoved: (Course,LocalDate)->Unit={_,_->}) {
    var selectedId by mutableStateOf<String?>(null);private set
    var draft by mutableStateOf<LayoutDraft?>(null);private set
    var conflicts by mutableStateOf<List<Course>?>(null);private set
    var busy by mutableStateOf(false);private set
    var error by mutableStateOf<String?>(null);private set
    private var leaving=false
    fun begin(schedule: Schedule,course: Course,date: LocalDate,edge: LayoutEdge): Boolean {
        if(busy || conflicts!=null || error!=null)return false
        selectedId=course.id;draft=LayoutDraft(schedule,course,date,edge);leaving=false
        return true
    }
    fun preview(dayDelta: Int,verticalDelta: Float,rows: List<TimetableRow>) {
        val current=draft ?: return
        if(busy || conflicts!=null)return
        val next=layoutCandidate(current.baseline,current.original,current.date,current.edge,dayDelta,verticalDelta,rows)
        if(next!=current.next)draft=current.copy(next=next)
    }
    fun cancelGesture() { if(!busy && conflicts==null)draft=null }
    fun exit() {
        selectedId=null
        if(busy)leaving=true else { draft=null;conflicts=null;error=null }
    }
    fun finish() {
        val current=draft ?: return
        if(current.next==current.original) { draft=null;return }
        save()
    }
    fun confirmConflict() { if(conflicts!=null)save(conflicts) }
    fun cancelPending() { if(!busy) { conflicts=null;draft=null;error=null } }
    private fun save(approved: List<Course>?=null) {
        val current=draft ?: return
        if(busy)return
        busy=true;error=null
        scope.launch {
            try {
                repository.update { data -> data.copy(schedule=applyLayoutChange(data.schedule,current.baseline,current.original,current.next,approved)) }
                conflicts=null;draft=null
                if(!leaving)onMoved(current.next,current.date)
                message("已保存第 ${current.next.start}—${current.next.end} 节")
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(collision: LayoutConflict) { if(!leaving)conflicts=collision.overlaps else message("位置调整存在课程冲突，原课程保留") }
            catch(failure: Exception) { if(!leaving)error=failure.message ?: "调整未保存，原课程保留" else message("位置调整未保存，请重新打开课程核对") }
            finally { busy=false;if(leaving){draft=null;conflicts=null;error=null;leaving=false} }
        }
    }
}
