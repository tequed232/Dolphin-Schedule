package com.dolphin.schedule.academic

import android.content.Context
import com.dolphin.schedule.data.*
import com.dolphin.schedule.domain.*
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException

/** 读取和提交分开；只有有效差异成为候选，绝不在后台替换正式课表。 */
internal class AcademicCheckRunner(private val context: Context,private val repository: NativeRepository,
    private val reader: suspend (String,Int)->String) {
    suspend fun run(manual: Boolean) {
        val initial=repository.initialize() as? RepositoryState.Ready ?: return
        val baseline=initial.snapshot.data
        if(!manual && baseline.settings.academicCheckHours==0)return
        val history=baseline.academicImport ?: run {
            repository.update { it.copy(academicCheck=AcademicCheckState("setup",Instant.now().toString(),"请先完成一次本校教务导入")) };return
        }
        if(baseline.pendingAcademic!=null && !manual) {
            repository.update { it.copy(academicCheck=AcademicCheckState("pending",it.academicCheck.checkedAt,"已有待确认结果，周期检查暂停等待核对")) };return
        }
        // 登录失效后不周期反复触网；用户重新登录并手动识别会重置此状态。
        if(!manual && baseline.academicCheck.status=="login")return
        repository.update { it.copy(academicCheck=AcademicCheckState("checking",Instant.now().toString(),"正在只读检查教务课表")) }
        try {
            val raw=reader(history.term,history.after.term.weeks)
            val snapshot=AcademicCodec.decode(raw)
            require(snapshot.term==history.term) { "教务学期已变化，请手动选择学期" }
            val converted=AcademicImporter.convert(snapshot,baseline.schedule,snapshot.termStartDate ?: baseline.schedule.term.startDate)
            val comparable=academicMerge(baseline.schedule,converted,baseline.settings.keepManualOnAcademic).schedule
            val changed=snapshot.termStartDate==null || compareSchedules(baseline.schedule,comparable).hasChanges
            repository.update(baseline.schedule) { current ->
                require(current.pendingAcademic?.id==baseline.pendingAcademic?.id) { "已有新的待确认结果，本次检查未覆盖它" }
                require(manual || current.settings.academicCheckHours!=0) { "周期检查已关闭，本次结果未保存" }
                require(current.academicImport==history) { "教务导入记录已变化，请重新检查" }
                if(changed)current.copy(pendingAcademic=current.pendingAcademic?.takeIf { it.raw==raw && it.baseline==current.schedule } ?: PendingAcademicUpdate(UUID.randomUUID().toString(),raw,current.schedule,Instant.now().toString()),academicCheck=AcademicCheckState("pending",Instant.now().toString(),"教务结果有待核对，确认前不会覆盖课表"))
                else current.copy(pendingAcademic=null,academicCheck=AcademicCheckState("unchanged",Instant.now().toString(),"未发现课程、学期或作息变化"))
            }
        } catch(cancelled: CancellationException) { throw cancelled }
        catch(failure: Exception) {
            val status=(failure as? AcademicReadFailure)?.status ?: "error"
            val safeMessage=if(failure is AcademicReadFailure)failure.message!! else "本次检查未能生成有效更新，请手动识别；原课表与待确认结果保留"
            repository.update { current ->
                current.copy(academicCheck=AcademicCheckState(status,Instant.now().toString(),safeMessage+if(current.pendingAcademic!=null)"；原待确认结果已保留" else ""))
            }
        }
    }
}
