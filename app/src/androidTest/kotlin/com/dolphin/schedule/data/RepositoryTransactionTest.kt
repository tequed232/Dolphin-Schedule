package com.dolphin.schedule.data

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.dolphin.schedule.domain.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RepositoryTransactionTest {
    private lateinit var context: Context
    @Before fun isolatedStorage() {
        check(InstrumentationRegistry.getArguments().getString("allowNativeDataTests")=="true")
        check(Build.HARDWARE in listOf("ranchu","goldfish") && Build.MODEL.startsWith("sdk_")) { "仅允许隔离模拟器" }
        val parent=InstrumentationRegistry.getInstrumentation().targetContext
        val folder=File(parent.cacheDir,"repository-test-${UUID.randomUUID()}").apply { mkdirs() }
        context=object: ContextWrapper(parent) {
            override fun getApplicationContext(): Context=this
            override fun getFilesDir(): File=folder
            override fun getDatabasePath(name: String): File=File(folder,name)
            override fun openOrCreateDatabase(name: String,mode: Int,factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase=SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name),factory)
            override fun openOrCreateDatabase(name: String,mode: Int,factory: SQLiteDatabase.CursorFactory?,errorHandler: DatabaseErrorHandler?): SQLiteDatabase=SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).path,factory,errorHandler)
            override fun getSharedPreferences(name: String,mode: Int)=parent.getSharedPreferences("${folder.name}-$name",mode)
        }
    }
    private fun fixture(): AppData {
        val course=Course(id="stable-id",name="迁移测试课程",teacher="教师",room="A101",day=2,start=1,end=2,weeks=listOf(1,3,7))
        return AppData(schedule=defaultSchedule().copy(courses=listOf(course)),books=mapOf(course.id to Book(title="教材",text="保留备注")),settings=Preferences(mode="dark",scale=1.1f),onboarded=true)
    }
    @Test fun actualWebViewReaderRetainsLegacyOriginAndBacksUpWhateverAlreadyExists()=runBlocking {
        val raw=LegacyDataReader.read(context)
        val expected=raw?.let(AppDataCodec::decode) ?: AppData()
        val migrated=NativeRepository(context).initialize() as RepositoryState.Ready
        assertEquals(expected,migrated.snapshot.data)
        if(raw!=null)assertEquals(raw,File(context.filesDir,"native-migration-backup.json").readText())
    }
    @Test fun migrationBacksUpOriginalThenReadsPersistedSnapshotWithoutCallingLegacyAgain()=runBlocking {
        val original=fixture();val raw=AppDataCodec.encode(original)
        val repository=NativeRepository(context) { raw }
        assertEquals(original,(repository.initialize() as RepositoryState.Ready).snapshot.data)
        assertEquals(raw,File(context.filesDir,"native-migration-backup.json").readText())
        repository.update { it.copy(settings=it.settings.copy(school="测试学校")) }
        val reopened=NativeRepository(context) { error("不应再次读取旧数据") }
        val state=reopened.initialize() as RepositoryState.Ready
        assertEquals(original.schedule,state.snapshot.data.schedule)
        assertEquals(original.books,state.snapshot.data.books)
        assertEquals("测试学校",state.snapshot.data.settings.school)
        assertEquals(2L,state.snapshot.revision)
    }
    @Test fun invalidWritesAndStalePreviewRollBackWithoutPublishing()=runBlocking {
        val repository=NativeRepository(context) { AppDataCodec.encode(fixture()) }
        val original=(repository.initialize() as RepositoryState.Ready).snapshot
        assertTrue(runCatching { repository.update { it.copy(schedule=it.schedule.copy(term=it.schedule.term.copy(weeks=0))) } }.isFailure)
        assertEquals(original,(repository.state.value as RepositoryState.Ready).snapshot)
        val next=repository.update { it.copy(schedule=it.schedule.copy(term=it.schedule.term.copy(name="已更新"))) }
        assertTrue(runCatching { repository.update(original.data.schedule) { it.copy(schedule=original.data.schedule) } }.isFailure)
        assertEquals(next,(repository.state.value as RepositoryState.Ready).snapshot)
        assertEquals(next,(NativeRepository(context) { null }.initialize() as RepositoryState.Ready).snapshot)
    }
    @Test fun concurrentTransactionsReadLatestStateAndMigrationFailureNeverCreatesEmptyData()=runBlocking {
        val failure=NativeRepository(context) { error("旧数据损坏") }
        assertTrue(failure.initialize() is RepositoryState.Failed)
        val repository=NativeRepository(context) { AppDataCodec.encode(fixture()) }
        repository.initialize()
        coroutineScope { (1..12).map { async { repository.update { data -> data.copy(settings=data.settings.copy(topInset=data.settings.topInset+1)) } } }.awaitAll() }
        val result=(repository.state.value as RepositoryState.Ready).snapshot
        assertEquals(12,result.data.settings.topInset)
        assertEquals(13L,result.revision)
    }
    @Test fun cancelledPageInitializationDoesNotPublishAFalseDataFailure()=runBlocking {
        val started=CompletableDeferred<Unit>()
        val repository=NativeRepository(context) { started.complete(Unit);awaitCancellation() }
        val pending=launch { repository.initialize() }
        started.await();pending.cancelAndJoin()
        assertEquals(RepositoryState.Loading,repository.state.value)
        assertTrue(NativeRepository(context) { AppDataCodec.encode(fixture()) }.initialize() is RepositoryState.Ready)
    }
}
