package com.dolphin.schedule.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.AtomicFile
import com.dolphin.schedule.JourneyState
import com.dolphin.schedule.Notifications
import com.dolphin.schedule.domain.AppData
import com.dolphin.schedule.domain.Schedule
import com.dolphin.schedule.domain.Book
import com.dolphin.schedule.domain.Course
import com.dolphin.schedule.domain.courseDestination
import com.dolphin.schedule.domain.notificationBooks
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

data class StoredData(val data: AppData,val revision: Long)
sealed interface RepositoryState {
    data object Loading: RepositoryState
    data class Ready(val snapshot: StoredData): RepositoryState
    data class Failed(val message: String): RepositoryState
}

/** 单行版本快照在 SQLite 事务中提交；失败不发布 UI，也不更新行程镜像。 */
class NativeRepository internal constructor(context: Context,private val legacyReader: suspend (Context)->String?=LegacyDataReader::read) {
    companion object {
        @Volatile private var instance: NativeRepository?=null
        fun get(context: Context): NativeRepository=instance ?: synchronized(this) { instance ?: NativeRepository(context.applicationContext).also { instance=it } }
    }
    private val app=context.applicationContext
    private val mutex=Mutex()
    private val mutable=MutableStateFlow<RepositoryState>(RepositoryState.Loading)
    val state: StateFlow<RepositoryState> = mutable
    private val mutableWarning=MutableStateFlow<String?>(null)
    private var lastBooks: Map<String,Book>?=null
    private var lastOwners: List<Pair<String,String>>?=null
    private var bookTitles="{}"
    private var lastCourses: List<Course>?=null
    private var lastNavigation: Pair<String,String>?=null
    private var journeyTargets="{}"
    val warning: StateFlow<String?> = mutableWarning
    private val helper=object: SQLiteOpenHelper(app,"dolphin-native.db",null,1) {
        override fun onCreate(db: SQLiteDatabase) { db.execSQL("CREATE TABLE app_state (id INTEGER PRIMARY KEY CHECK(id=1), payload TEXT NOT NULL, revision INTEGER NOT NULL)") }
        override fun onUpgrade(db: SQLiteDatabase,oldVersion: Int,newVersion: Int) { error("不支持的本地数据库升级，请保留数据并重试") }
    }.apply { setWriteAheadLoggingEnabled(true) }

    suspend fun initialize(): RepositoryState=mutex.withLock {
        try {
            val existing=withContext(Dispatchers.IO) { read(helper.readableDatabase) }
            if(existing!=null) { withContext(Dispatchers.IO) { publish(existing) };return@withLock mutable.value }
            val legacy=legacyReader(app)
            val initial=legacy?.let(AppDataCodec::decode) ?: AppData()
            withContext(Dispatchers.IO) {
                // 迁移原文先写入应用私有备份，绝不放进源码或日志；旧 IndexedDB 不删除。
                if(legacy!=null) {
                    val backup=AtomicFile(File(app.filesDir,"native-migration-backup.json"));val output=backup.startWrite()
                    try { output.write(legacy.toByteArray(Charsets.UTF_8));backup.finishWrite(output) } catch(error: Exception) { backup.failWrite(output);throw error }
                }
                val db=helper.writableDatabase
                db.beginTransaction()
                val committed: StoredData
                try {
                    committed=read(db) ?: StoredData(initial,1).also { db.execSQL("INSERT INTO app_state(id,payload,revision) VALUES(1,?,?)",arrayOf(AppDataCodec.encode(it.data),it.revision)) }
                    db.setTransactionSuccessful()
                } finally { db.endTransaction() }
                publish(committed)
            }
        } catch(cancelled: CancellationException) { throw cancelled }
        catch(error: Exception) { mutable.value=RepositoryState.Failed(error.message ?: "本地数据读取失败，请重试；原数据保留") }
        mutable.value
    }
    suspend fun update(expectedSchedule: Schedule?=null,transform: (AppData)->AppData): StoredData=withContext(Dispatchers.IO) {
        mutex.withLock {
            val db=helper.writableDatabase
            db.beginTransaction()
            val next: StoredData
            try {
                val current=read(db) ?: error("本地数据尚未读取完成")
                require(expectedSchedule==null || current.data.schedule==expectedSchedule) { "课表已发生变化，请重新核对后保存；当前草稿保留" }
                val changed=transform(current.data)
                if(changed==current.data) { db.setTransactionSuccessful();return@withLock current }
                val raw=AppDataCodec.encode(changed)
                next=StoredData(changed,current.revision+1)
                db.execSQL("UPDATE app_state SET payload=?,revision=? WHERE id=1",arrayOf(raw,next.revision))
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
            publish(next)
            next
        }
    }
    private fun read(db: SQLiteDatabase): StoredData?=db.rawQuery("SELECT payload,revision FROM app_state WHERE id=1",null).use { rows -> if(rows.moveToFirst()) StoredData(AppDataCodec.decode(rows.getString(0)),rows.getLong(1)) else null }
    private fun publish(snapshot: StoredData) {
        mutable.value=RepositoryState.Ready(snapshot)
        // 只镜像行程所需的目标和教材标题；不保存第二份课表或安排自动提醒。
        runCatching {
            val data=snapshot.data
            val owners=data.schedule.courses.map { it.id to it.name }
            if(lastBooks!=data.books || lastOwners!=owners) {
                val books=notificationBooks(data)
                val titles=JSONObject();books.forEach { (name,book) -> titles.put(name,book.title) }
                bookTitles=titles.toString();lastBooks=data.books;lastOwners=owners
            }
            val navigation=data.settings.school to data.settings.map
            if(lastCourses!=data.schedule.courses || lastNavigation!=navigation) {
                val targets=JSONObject()
                data.schedule.courses.forEach { course ->
                    runCatching { courseDestination(course,data.settings) }.getOrNull()?.let { target ->
                        targets.put(course.id,JSONObject().put("title",course.name).put("room",target.room).put("url",target.url))
                    }
                }
                journeyTargets=targets.toString();lastCourses=data.schedule.courses;lastNavigation=navigation
            }
            JourneyState.removeRetiredFeatures(app)
            Notifications.updateMirror(app,JSONObject().put("notificationsEnabled",data.settings.notificationsEnabled)
                .put("journeyLive",data.settings.journeyLive).put("school",data.settings.school),bookTitles,journeyTargets)
        }.onSuccess { mutableWarning.value=null }.onFailure { mutableWarning.value="课表已保存，行程状态更新失败；请重新打开应用重试" }
    }
}
