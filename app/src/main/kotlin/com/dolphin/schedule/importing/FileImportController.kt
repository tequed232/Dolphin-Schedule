package com.dolphin.schedule.importing

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.AtomicFile
import androidx.compose.runtime.*
import androidx.lifecycle.SavedStateHandle
import com.dolphin.schedule.academic.AcademicPolicy
import com.dolphin.schedule.data.AppDataCodec
import com.dolphin.schedule.data.NativeRepository
import com.dolphin.schedule.data.RepositoryState
import com.dolphin.schedule.domain.Schedule
import com.dolphin.schedule.domain.validateSchedule
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import org.json.JSONObject

private data class FileDraft(val id: String,val name: String,val format: ImportFormat?=null,val sheet: Int=0,val mapping: SheetMapping?=null,val base: Schedule?=null,val date: String?=null)
class FileImportController(private val context: Context,private val repository: NativeRepository,private val scope: CoroutineScope,private val saved: SavedStateHandle,private val onSaved: ()->Unit,private val onLeave: ()->Unit,private val message: (String)->Unit) {
    private val folder=File(context.filesDir,"file-import-drafts")
    private var draft by mutableStateOf<FileDraft?>(null)
    val name get()=draft?.name.orEmpty()
    val id get()=draft?.id
    val index get()=draft?.sheet ?: 0
    val appliedMapping get()=draft?.mapping
    val baseline get()=draft?.base
    var source by mutableStateOf<ImportSource?>(null);private set
    var preview by mutableStateOf<FilePreview?>(null);private set
    var busy by mutableStateOf(saved.get<String>("fileDraft")!=null);private set
    var saving by mutableStateOf(false);private set
    var error by mutableStateOf<String?>(null);private set
    var formatIssue by mutableStateOf(false);private set
    var suggested by mutableStateOf(ImportFormat.JSON);private set
    var reviewing by mutableStateOf(false);private set
    var acknowledged by mutableStateOf(false);private set
    var pasted by mutableStateOf("")
    var discardDialog by mutableStateOf(false)
    private var leaveAfterDiscard=false
    private var generation=0
    private var job: Job?=null
    val dirty get()=draft!=null || pasted.isNotBlank() || busy
    init { saved.get<String>("fileDraft")?.let { id -> task {
        draft=withContext(Dispatchers.IO) { load(id) };readSource(false)
    } } }
    private fun target(id: String,suffix: String): File { require(AcademicPolicy.requestId(id));return File(folder,"$id.$suffix") }
    private fun write(d: FileDraft,bytes: ByteArray?=null) {
        check(folder.isDirectory || folder.mkdirs()) { "无法保存导入草稿" }
        fun atomic(file: File,value: ByteArray) { val a=AtomicFile(file);val output=a.startWrite();try { output.write(value);a.finishWrite(output) } catch(failure: Exception) { a.failWrite(output);throw failure } }
        bytes?.let { atomic(target(d.id,"source"),it) }
        val meta=JSONObject().put("schema",1).put("name",d.name).put("format",d.format?.name).put("sheet",d.sheet).put("date",d.date)
        d.base?.let { meta.put("base",AppDataCodec.encodeSchedule(it)) }
        d.mapping?.let { m -> meta.put("mapping",JSONObject().put("dayRow",m.dayRow).put("sectionColumn",m.sectionColumn).put("firstRow",m.firstRow).put("lastRow",m.lastRow).put("firstColumn",m.firstColumn).put("lastColumn",m.lastColumn)) }
        atomic(target(d.id,"json"),meta.toString().toByteArray(Charsets.UTF_8))
    }
    private fun load(id: String): FileDraft {
        val meta=target(id,"json");require(meta.length() in 1..AppDataCodec.MAX_BYTES.toLong()) { "导入草稿已失效，请重新选择文件" }
        val obj=AppDataCodec.parseObject(AtomicFile(meta).readFully().toString(Charsets.UTF_8));require(obj.optInt("schema")==1)
        val mapping=obj.optJSONObject("mapping")?.let { SheetMapping(it.getInt("dayRow"),it.getInt("sectionColumn"),it.getInt("firstRow"),it.getInt("lastRow"),it.getInt("firstColumn"),it.getInt("lastColumn")) }
        return FileDraft(id,obj.getString("name"),if(obj.isNull("format"))null else ImportFormat.valueOf(obj.getString("format")),obj.optInt("sheet"),mapping,obj.optJSONObject("base")?.let(AppDataCodec::decodeSchedule),if(obj.isNull("date"))null else obj.getString("date"))
    }
    private fun remove(id: String) { AtomicFile(target(id,"source")).delete();AtomicFile(target(id,"json")).delete() }
    private fun task(block: suspend ()->Unit) {
        if(saving)return
        val token=++generation;job?.cancel();busy=true;acknowledged=false;error=null
        job=scope.launch {
            try { block() }
            catch(cancelled: CancellationException) { throw cancelled }
            catch(format: FormatMismatch) { formatIssue=true;suggested=format.suggested ?: ImportFormat.JSON;error=format.message }
            catch(failure: Exception) { error=failure.message ?: "文件读取未完成，原课表保留" }
            finally { if(token==generation)busy=false }
        }
    }
    private fun current()=(repository.state.value as? RepositoryState.Ready)?.snapshot?.data?.schedule ?: error("本地课表尚未读取完成")
    fun select(uri: Uri) { task {
        val pair=withContext(Dispatchers.IO) {
            val name=context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { if(it.moveToFirst())it.getString(0) else null } ?: "课表文件"
            val bytes=requireNotNull(context.contentResolver.openInputStream(uri)) { "无法读取文件" }.use { input ->
                val output=ByteArrayOutputStream();val buffer=ByteArray(8192)
                while(true){val n=input.read(buffer);if(n<0)break;require(output.size()+n<=FileNormalizer.MAX_BYTES) { "文件超过 5 MB" };output.write(buffer,0,n)};output.toByteArray()
            }
            name.take(300) to bytes
        }
        install(pair.first,pair.second,null)
    } }
    fun paste() { val value=pasted;task { install("粘贴课表.json",value.toByteArray(Charsets.UTF_8),ImportFormat.JSON) } }
    fun updatePasted(value: String) {
        if(value.toByteArray(Charsets.UTF_8).size>FileNormalizer.MAX_BYTES)error="粘贴内容超过 5 MB，请精简后再试"
        else { pasted=value;error=null }
    }
    internal fun selectBytes(name: String,bytes: ByteArray) { task { install(name,bytes,null) } }
    private suspend fun install(name: String,bytes: ByteArray,format: ImportFormat?) {
        require(bytes.size in 1..FileNormalizer.MAX_BYTES) { "文件为空或超过 5 MB" }
        val old=draft?.id;val next=FileDraft(UUID.randomUUID().toString(),name,format)
        withContext(Dispatchers.IO) { write(next,bytes) }
        draft=next;saved["fileDraft"]=next.id;pasted="";source=null;preview=null;reviewing=false;formatIssue=false
        if(old!=null)withContext(Dispatchers.IO) { remove(old) }
        readSource(true)
    }
    fun overrideFormat(format: ImportFormat) { task {
        val next=(draft ?: return@task).copy(format=format,sheet=0,mapping=null,base=null,date=null);withContext(Dispatchers.IO) { write(next) };draft=next;readSource(true)
    } }
    private suspend fun readSource(auto: Boolean) {
        val d=draft ?: return
        val bytes=withContext(Dispatchers.IO) { val file=target(d.id,"source");require(file.length() in 1..FileNormalizer.MAX_BYTES.toLong()) { "导入文件已失效，请重新选择" };file.readBytes() }
        val format=d.format ?: withContext(Dispatchers.Default) { TableImport.detect(d.name,bytes) }
        val decoded=when(format) {
            ImportFormat.EXCEL->ImportSource(format,SpreadsheetDecoder.decode(context,bytes))
            ImportFormat.JSON->withContext(Dispatchers.Default) { ImportSource(format,json=TableImport.decodeText(bytes)) }
            ImportFormat.CSV->withContext(Dispatchers.Default) { ImportSource(format,listOf(ImportSheet(d.name,TableImport.csv(TableImport.decodeText(bytes))))) }
        }
        source=decoded;formatIssue=false
        if(decoded.sheets.size<=1 || !auto)analyze(d,decoded,d.base ?: current(),auto)
    }
    private suspend fun analyze(d: FileDraft,source: ImportSource,base: Schedule,auto: Boolean) {
        acknowledged=false;preview=null
        val result=withContext(Dispatchers.Default) { TableImport.parse(source,base,d.sheet,d.mapping).let { p -> if(d.date==null)p else p.copy(schedule=validateSchedule(p.schedule.copy(term=p.schedule.term.copy(startDate=d.date)))) } }
        val next=d.copy(base=base);withContext(Dispatchers.IO) { write(next) };draft=next;preview=result;reviewing=auto && result.errors.isEmpty()
    }
    fun selectSheet(index: Int) { task { val d=draft ?: return@task;val src=source ?: return@task;require(index in src.sheets.indices);val next=d.copy(sheet=index,mapping=null,base=null,date=null);draft=next;withContext(Dispatchers.IO) { write(next) };analyze(next,src,current(),false) } }
    fun analyzeMapping(mapping: SheetMapping?) { task { val next=(draft ?: return@task).copy(mapping=mapping,base=null);draft=next;withContext(Dispatchers.IO) { write(next) };analyze(next,source ?: return@task,current(),false) } }
    fun repreview() { task { val d=draft ?: return@task;analyze(d,source ?: return@task,current(),false) } }
    fun setDate(date: String) { task { val d=draft ?: return@task;val candidate=preview ?: return@task;val updated=validateSchedule(candidate.schedule.copy(term=candidate.schedule.term.copy(startDate=date)));val next=d.copy(date=date);withContext(Dispatchers.IO) { write(next) };draft=next;preview=candidate.copy(schedule=updated) } }
    fun showPreview() { if(!busy && preview!=null){reviewing=true;acknowledged=false} }
    fun showReader() { if(!busy){reviewing=false;acknowledged=false} }
    fun acknowledge(value: Boolean) { acknowledged=value && !busy && preview!=null && current()==baseline }
    fun commit() {
        val candidate=preview ?: return;val base=baseline ?: return
        if(busy || saving || !acknowledged)return
        saving=true;error=null
        job=scope.launch {
            try { repository.update(base) { it.copy(schedule=validateSchedule(if(candidate.onlyConfiguration)candidate.schedule else candidate.schedule.copy(courses=candidate.schedule.courses.map { c -> c.copy(source="file") })),onboarded=true) };clear();message(if(candidate.onlyConfiguration)"作息已更新，课程保留" else "课表已导入");onSaved() }
            catch(cancelled: CancellationException) { throw cancelled }
            catch(failure: Exception) { error=failure.message ?: "保存未完成，导入内容仍保留" }
            finally { saving=false }
        }
    }
    fun requestDiscard(leave: Boolean=false) { if(!saving){leaveAfterDiscard=leave;discardDialog=true} }
    fun confirmDiscard() { if(!saving){++generation;job?.cancel();clear();busy=false;discardDialog=false;if(leaveAfterDiscard)onLeave()} }
    private fun clear() { val old=draft?.id;draft=null;source=null;preview=null;pasted="";error=null;reviewing=false;acknowledged=false;formatIssue=false;saved.remove<String>("fileDraft");if(old!=null)scope.launch(Dispatchers.IO) { runCatching { remove(old) } } }
}
