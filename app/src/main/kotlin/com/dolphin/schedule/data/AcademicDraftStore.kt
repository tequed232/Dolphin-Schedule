package com.dolphin.schedule.data

import android.content.Context
import android.util.AtomicFile
import com.dolphin.schedule.academic.AcademicPolicy
import com.dolphin.schedule.domain.AcademicSnapshot
import com.dolphin.schedule.domain.Schedule
import java.io.File
import org.json.JSONObject

data class AcademicDraft(val id: String,val baseline: Schedule,val raw: String,val snapshot: AcademicSnapshot,val confirmedDate: String?=null)

/** 草稿只写应用私有目录；SavedStateHandle 只保存 UUID，不把整张课表塞进 Activity Bundle。 */
class AcademicDraftStore(context: Context) {
    private val folder=File(context.filesDir,"academic-drafts")
    private fun file(id: String): File {
        require(AcademicPolicy.requestId(id)) { "教务草稿标识无效" }
        return File(folder,"$id.json")
    }
    fun save(draft: AcademicDraft) {
        require(folder.isDirectory || folder.mkdirs()) { "无法创建导入草稿" }
        val value=JSONObject().put("schema",1).put("baseline",AppDataCodec.encodeSchedule(draft.baseline)).put("snapshot",JSONObject(draft.raw)).put("confirmedDate",draft.confirmedDate)
        val bytes=value.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size<=AppDataCodec.MAX_BYTES) { "导入草稿过大" }
        val atomic=AtomicFile(file(draft.id));val output=atomic.startWrite()
        try { output.write(bytes);atomic.finishWrite(output) } catch(failure: Exception) { atomic.failWrite(output);throw failure }
    }
    fun load(id: String): AcademicDraft {
        val target=file(id)
        require(target.length() in 1..AppDataCodec.MAX_BYTES.toLong()) { "上次识别结果已失效，请重新识别；原课表保留" }
        val value=AppDataCodec.parseObject(AtomicFile(target).readFully().toString(Charsets.UTF_8))
        require(value.optInt("schema")==1) { "无法读取导入草稿" }
        val raw=value.getJSONObject("snapshot").toString()
        return AcademicDraft(id,AppDataCodec.decodeSchedule(value.getJSONObject("baseline")),raw,AcademicCodec.decode(raw),if(value.isNull("confirmedDate")) null else value.getString("confirmedDate"))
    }
    fun delete(id: String) { AtomicFile(file(id)).delete() }
}
