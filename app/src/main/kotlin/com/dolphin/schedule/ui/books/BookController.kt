package com.dolphin.schedule.ui.books

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.*
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import com.dolphin.schedule.academic.AcademicPolicy
import com.dolphin.schedule.data.BookDraftStore
import com.dolphin.schedule.data.NativeRepository
import com.dolphin.schedule.data.RepositoryState
import com.dolphin.schedule.domain.*
import com.dolphin.schedule.media.LocalImages
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import org.json.JSONObject

class BookController(private val context: Context,private val repository: NativeRepository,private val scope: CoroutineScope,private val saved: SavedStateHandle,private val onClosed: (String?)->Unit,private val message: (String)->Unit) {
    private val store=BookDraftStore(context)
    var draft by mutableStateOf<BookDraft?>(null);private set
    var busy by mutableStateOf(saved.get<String>("bookDraft")!=null);private set
    var error by mutableStateOf<String?>(null);private set
    var discard by mutableStateOf(false);private set
    var removing by mutableStateOf(false);private set
    private var restoring=false
    private var restoreJob: Job?=null
    val open get()=draft!=null || restoring
    val dirty get()=draft?.let { it.value!=it.seed }==true
    init {
        saved.get<String>("bookDraft")?.let { id ->
            restoring=true
            restoreJob=scope.launch {
                try {
                    val loaded=withContext(Dispatchers.IO) { store.load(id) }
                    draft=saved.get<String>("bookText")?.let { raw ->
                        require(raw.length<=40_000);val text=JSONObject(raw)
                        loaded.copy(value=loaded.value.copy(title=text.getString("title"),publisher=text.getString("publisher"),edition=text.getString("edition"),text=text.getString("text")))
                    } ?: loaded
                } catch(cancelled: CancellationException) { throw cancelled }
                catch(failure: Exception) { saved.remove<String>("bookDraft");saved.remove<String>("bookText");message(failure.message ?: "教材草稿恢复失败，已保存的教材保留") }
                finally { busy=false;restoring=false }
            }
        }
    }
    fun start(data: AppData,course: Course?=null,key: String?=null,returnToCourse: Boolean=false) {
        if(busy || open)return
        action {
            val next=bookDraft(data,course,key,returnToCourse)
            withContext(Dispatchers.IO) { store.save(next) }
            draft=next;saved["bookDraft"]=next.id;saveText(next.value)
        }
    }
    fun patch(field: String,value: String) {
        val d=draft ?: return;if(busy)return
        val old=when(field) { "title"->d.value.title;"publisher"->d.value.publisher;"edition"->d.value.edition;"text"->d.value.text;else->return }
        val limit=when(field) { "title"->300;"text"->10000;else->500 }
        if(value.length>limit && value.length>=old.length) { error="内容过长，请精简后再填写";return }
        val book=when(field) { "title"->d.value.copy(title=value);"publisher"->d.value.copy(publisher=value);"edition"->d.value.copy(edition=value);else->d.value.copy(text=value) }
        draft=d.copy(value=book);saveText(book);error=null
    }
    private fun saveText(b: Book) { saved["bookText"]=JSONObject().put("title",b.title).put("publisher",b.publisher).put("edition",b.edition).put("text",b.text).toString() }
    fun requestClose() { if(!busy) { if(dirty)discard=true else clear() } }
    fun continueEditing() { discard=false }
    fun discardChanges() { if(!busy) { discard=false;clear() } }
    fun requestRemove() { if(!busy && draft?.original!=null)removing=true }
    fun cancelRemove() { removing=false }
    fun save(remove: Boolean=false) {
        val d=draft ?: return
        action {
            repository.update { saveBook(it,d,remove) }
            removing=false;discard=false;clear();message(if(remove)"教材已移除，课程保留" else "教材已保存在本机")
        }
    }
    fun reload() {
        val d=draft ?: return
        action {
            val data=(repository.state.value as? RepositoryState.Ready)?.snapshot?.data ?: error("教材尚未读取完成")
            val owner=d.ownerId?.let { id -> data.schedule.courses.firstOrNull { it.id==id } ?: error("课程已移除；可保留草稿或放弃修改") }
            val next=bookDraft(data,owner,if(owner==null)d.key else null,d.returnToCourse)
            withContext(Dispatchers.IO) { store.save(next);store.delete(d.id) }
            draft=next;saved["bookDraft"]=next.id;saveText(next.value)
        }
    }
    fun prepareAlbum(): Boolean {
        val d=draft ?: return false;if(busy)return false
        saved["bookAlbumOwner"]=d.id;return true
    }
    fun albumResult(uri: Uri?) {
        val owner=saved.remove<String>("bookAlbumOwner") ?: return
        if(uri==null)return
        coverResult(owner,uri,"album")
    }
    fun prepareCamera(): Uri? {
        val d=draft ?: return null;if(busy)return null
        return runCatching {
            check(saved.get<String>("bookCapture")==null) { "请先完成上次拍照" }
            val id=UUID.randomUUID().toString();val file=captureFile(id)
            check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs());check(file.createNewFile())
            val uri=FileProvider.getUriForFile(context,"${context.packageName}.files",file)
            saved["bookCapture"]=id;saved["bookCaptureOwner"]=d.id;uri
        }.onFailure { error=it.message ?: "无法准备相机" }.getOrNull()
    }
    fun cameraResult(success: Boolean) {
        val id=saved.remove<String>("bookCapture") ?: return
        val owner=saved.remove<String>("bookCaptureOwner")
        val file=runCatching { captureFile(id) }.getOrNull() ?: return
        if(!success || owner==null) { file.delete();return }
        val uri=FileProvider.getUriForFile(context,"${context.packageName}.files",file)
        coverResult(owner,uri,"camera",file)
    }
    fun mediaLaunchFailed(camera: Boolean) {
        if(camera)cameraResult(false) else saved.remove<String>("bookAlbumOwner")
        error=if(camera)"没有可用的相机，请从相册选择封面" else "无法打开相册，请重试"
    }
    private fun captureFile(id: String): File { require(AcademicPolicy.requestId(id));return File(context.cacheDir,"captures/book-$id.jpg") }
    private fun coverResult(owner: String,uri: Uri,source: String,cleanup: File?=null) {
        scope.launch {
            restoreJob?.join()
            if(busy || draft?.id!=owner) { withContext(Dispatchers.IO) { cleanup?.delete() };return@launch }
            busy=true;error=null
            try {
                val d=draft ?: return@launch
                val cover=withContext(Dispatchers.IO) { LocalImages.cover(context,uri) }
                val next=d.copy(value=d.value.copy(cover=cover,source=source))
                withContext(Dispatchers.IO) { store.save(next) }
                draft=next
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(failure: Exception) { error=failure.message ?: "封面处理失败，原封面保留" }
            finally { withContext(NonCancellable+Dispatchers.IO) { cleanup?.delete() };busy=false }
        }
    }
    fun removeCover() {
        val d=draft ?: return
        action { val next=d.copy(value=d.value.copy(cover=null));withContext(Dispatchers.IO) { store.save(next) };draft=next }
    }
    private fun clear() {
        val d=draft;draft=null;error=null;saved.remove<String>("bookDraft");saved.remove<String>("bookText");saved.remove<String>("bookAlbumOwner")
        val capture=saved.remove<String>("bookCapture");saved.remove<String>("bookCaptureOwner")
        if(capture!=null)scope.launch(Dispatchers.IO) { runCatching { captureFile(capture).delete() } }
        if(d!=null)scope.launch(Dispatchers.IO) { runCatching { store.delete(d.id) } }
        onClosed(d?.ownerId?.takeIf { d.returnToCourse })
    }
    private fun action(block: suspend ()->Unit) {
        if(busy)return
        busy=true;error=null
        scope.launch {
            try { block() }
            catch(cancelled: CancellationException) { throw cancelled }
            catch(failure: Exception) { error=failure.message ?: "教材操作未完成，已保存内容保留";if(draft==null)message(error!!) }
            finally { busy=false }
        }
    }
}
