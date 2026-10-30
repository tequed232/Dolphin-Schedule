package com.dolphin.schedule.academic

import android.app.job.JobParameters
import android.app.job.JobService
import com.dolphin.schedule.data.NativeRepository
import kotlinx.coroutines.*

open class AcademicCheckJobService: JobService() {
    protected open val legacy=false
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val running=mutableMapOf<Int,Job>()
    override fun onStartJob(params: JobParameters): Boolean {
        val task=scope.launch(start=CoroutineStart.LAZY) {
            try {
                if(legacy)LegacyAcademicActivity.prepareIsolatedStorage()
                AcademicCheckRunner(this@AcademicCheckJobService,NativeRepository.get(this@AcademicCheckJobService)) { term,weeks ->
                    AcademicBackgroundReader.read(this@AcademicCheckJobService,legacy,term,weeks)
                }.run(params.extras.getBoolean("manual",false))
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(_: Exception) { runCatching { NativeRepository.get(this@AcademicCheckJobService).update { it.copy(academicCheck=com.dolphin.schedule.domain.AcademicCheckState("error",java.time.Instant.now().toString(),"检查未完成，请手动识别；原课表保留")) } } }
            finally { if(running[params.jobId]===currentCoroutineContext()[Job]) { running.remove(params.jobId);jobFinished(params,false) } }
        }
        running.remove(params.jobId)?.cancel();running[params.jobId]=task;task.start()
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { running.remove(params.jobId)?.cancel();return false }
    override fun onDestroy() { scope.cancel();super.onDestroy() }
}

class LegacyAcademicCheckJobService: AcademicCheckJobService() { override val legacy=true }
