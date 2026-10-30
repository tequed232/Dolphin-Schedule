package com.dolphin.schedule.academic

import android.app.job.JobScheduler
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.dolphin.schedule.data.*
import com.dolphin.schedule.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

/** 合成课表仅用于隔离模拟器；不触碰真机数据库或学校会话。 */
class AcademicCheckTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var repository: NativeRepository
    private lateinit var original: AppData
    private val raw="""{"schema":1,"school":"gdcmxy","term":"2026-2027-1","termStartDate":"2026-09-01","startWeek":1,"endWeek":21,"rows":[{"label":"1","start":"08:00","end":"08:45","cells":[[{"name":"后台合成课程","weeks":"1-21","teacher":"教师","room":"1栋101"}],[],[],[],[],[],[]]}]}"""
    private fun data()=(repository.state.value as RepositoryState.Ready).snapshot.data
    @Before fun setup(): Unit=runBlocking {
        check(InstrumentationRegistry.getArguments().getString("allowNativeDataTests")=="true")
        check(Build.HARDWARE in listOf("ranchu","goldfish") && (Build.MODEL.startsWith("sdk_") || Build.MODEL=="Android SDK built for x86_64"))
        repository=NativeRepository.get(context);original=(repository.initialize() as RepositoryState.Ready).snapshot.data
        val schedule=AcademicImporter.convert(AcademicCodec.decode(raw),defaultSchedule())
        repository.update { AppData(schedule=schedule,academicImport=AcademicHistory("gdcmxy",schedule.term.name,"2026-10-30T04:00:00Z",schedule,schedule),settings=Preferences(academicCheckHours=6),onboarded=true) }
        Unit
    }
    @After fun restore() { if(::original.isInitialized)runBlocking { repository.update { original };AcademicCheckScheduler.sync(context,original) } }
    private fun runner(reader:suspend (String,Int)->String)=AcademicCheckRunner(context,repository,reader)
    @Test fun actualOldRuntimeSelectsIsolatedJobAndReportsMissingLoginAcrossProcesses()=runBlocking {
        Assume.assumeTrue("仅在实际缺少多 Profile 的旧模拟器验收",!androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.MULTI_PROFILE))
        assertTrue(AcademicPolicy.useLegacyBrowser(false,Build.VERSION.SDK_INT))
        val before=data().schedule
        AcademicCheckScheduler.checkNow(context)
        val scheduler=context.getSystemService(JobScheduler::class.java)
        scheduler.getPendingJob(AcademicCheckScheduler.MANUAL_ID)?.let { assertEquals(LegacyAcademicCheckJobService::class.java.name,it.service.className) }
        // 调度时机由系统决定；隔离模拟器中主动执行一次来验证服务，不用等待约束窗口。
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("cmd jobscheduler run -f ${context.packageName} ${AcademicCheckScheduler.MANUAL_ID}").use { descriptor ->
            java.io.FileInputStream(descriptor.fileDescriptor).bufferedReader().use { it.readText() }
        }
        val deadline=android.os.SystemClock.uptimeMillis()+30_000
        while(data().academicCheck.status!="login" && android.os.SystemClock.uptimeMillis()<deadline) {
            kotlinx.coroutines.delay(200);repository.refresh()
        }
        assertEquals("login",data().academicCheck.status);assertEquals(before,data().schedule);assertNull(data().pendingAcademic)
        scheduler.cancel(AcademicCheckScheduler.MANUAL_ID)
    }
    @Test fun changedResultPersistsWithoutApplyingAndPeriodicPausesUntilReviewed()=runBlocking {
        val before=data().schedule
        runner { _,_->raw.replace("1栋101","2栋201") }.run(manual=true)
        val candidate=requireNotNull(data().pendingAcademic)
        assertEquals(before,data().schedule);assertEquals("pending",data().academicCheck.status)
        assertEquals(candidate,AppDataCodec.decode(AppDataCodec.encode(data())).pendingAcademic)
        runner { _,_->error("候选待确认时不应发起周期读取") }.run(manual=false)
        assertEquals(candidate,data().pendingAcademic);assertEquals(before,data().schedule)
        runner { _,_->throw AcademicReadFailure("login","登录已失效") }.run(manual=true)
        assertEquals(candidate,data().pendingAcademic);assertEquals(before,data().schedule);assertEquals("login",data().academicCheck.status)
    }
    @Test fun emptyOrStaleResultNeverOverwritesAndManualRecheckCanRetireAnOldCandidate()=runBlocking {
        val before=data().schedule
        runner { _,_->raw.replace("1栋101","2栋201") }.run(true)
        val pending=data().pendingAcademic
        runner { _,_->raw.replace("[{\"name\":\"后台合成课程\",\"weeks\":\"1-21\",\"teacher\":\"教师\",\"room\":\"1栋101\"}]","[]") }.run(true)
        assertEquals(before,data().schedule);assertEquals(pending,data().pendingAcademic)
        runner { _,_->repository.update { it.copy(schedule=it.schedule.copy(courses=it.schedule.courses.map { c->c.copy(notes="检查期间编辑") })) };raw }.run(true)
        assertEquals("检查期间编辑",data().schedule.courses.single().notes);assertEquals(pending,data().pendingAcademic)
        repository.update { it.copy(schedule=before) }
        runner { _,_->raw }.run(true)
        assertNull(data().pendingAcademic);assertEquals("unchanged",data().academicCheck.status)
    }
    @Test fun disabledOrExpiredPeriodicCheckDoesNotReadAndDisablingDuringReadDropsTheResult()=runBlocking {
        repository.update { it.copy(settings=it.settings.copy(academicCheckHours=0)) }
        runner { _,_->error("关闭时不读取") }.run(false)
        repository.update { it.copy(settings=it.settings.copy(academicCheckHours=6),academicCheck=AcademicCheckState("login")) }
        runner { _,_->error("会话过期时不周期读取") }.run(false)
        repository.update { it.copy(academicCheck=AcademicCheckState()) }
        val before=data().schedule
        runner { _,_->repository.update { it.copy(settings=it.settings.copy(academicCheckHours=0)) };raw.replace("1栋101","新教室") }.run(false)
        assertNull(data().pendingAcademic);assertEquals(before,data().schedule)
    }
    @Test fun periodicJobHasNetworkBatteryAndRebootConstraintsAndCanBeCancelled() {
        AcademicCheckScheduler.sync(context,data())
        val scheduler=context.getSystemService(JobScheduler::class.java)
        val job=requireNotNull(scheduler.getPendingJob(AcademicCheckScheduler.PERIODIC_ID))
        assertTrue(job.isPersisted);assertTrue(job.isRequireBatteryNotLow);assertEquals(6*60*60*1000L,job.intervalMillis)
        assertNotNull(job.requiredNetwork)
        AcademicCheckScheduler.sync(context,data().copy(settings=data().settings.copy(academicCheckHours=0)))
        assertNull(scheduler.getPendingJob(AcademicCheckScheduler.PERIODIC_ID))
    }
}
