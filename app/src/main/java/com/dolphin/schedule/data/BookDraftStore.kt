package com.dolphin.schedule.data

import android.content.Context
import android.util.AtomicFile
import com.dolphin.schedule.academic.AcademicPolicy
import com.dolphin.schedule.domain.Book
import com.dolphin.schedule.domain.BookDraft
import org.json.JSONObject
import java.io.File

/** 封面与基线只写应用私有文件，Activity Bundle 仅保存草稿 ID 和有界文字。 */
class BookDraftStore(context: Context) {
    private val folder=File(context.filesDir,"book-drafts")
    private fun file(id: String): File { require(AcademicPolicy.requestId(id));return File(folder,"$id.json") }
    fun save(d: BookDraft) {
        require(folder.isDirectory || folder.mkdirs()) { "无法保存教材草稿" }
        val obj=JSONObject().put("schema",1).put("key",d.key).put("original",d.original?.let(::encodeBook)).put("seed",encodeBook(d.seed)).put("value",encodeBook(d.value)).put("ownerId",d.ownerId).put("ownerName",d.ownerName).put("returnToCourse",d.returnToCourse)
        val bytes=obj.toString().toByteArray(Charsets.UTF_8);require(bytes.size<=AppDataCodec.MAX_BYTES) { "教材草稿过大，请精简封面" }
        val atomic=AtomicFile(file(d.id));val output=atomic.startWrite()
        try { output.write(bytes);atomic.finishWrite(output) } catch(failure: Exception) { atomic.failWrite(output);throw failure }
    }
    fun load(id: String): BookDraft {
        val bytes=AtomicFile(file(id)).openRead().use { input ->
            require(input.channel.size() in 1..AppDataCodec.MAX_BYTES.toLong()) { "教材草稿已失效；已保存的教材保留" };input.readBytes()
        }
        val obj=AppDataCodec.parseObject(bytes.toString(Charsets.UTF_8));require(obj.optInt("schema")==1)
        return BookDraft(id,obj.getString("key"),obj.optJSONObject("original")?.let(::decodeBook),decodeBook(obj.getJSONObject("seed")),decodeBook(obj.getJSONObject("value")),obj.optional("ownerId"),obj.optional("ownerName"),obj.optBoolean("returnToCourse"))
    }
    fun delete(id: String) { AtomicFile(file(id)).delete() }
    private fun JSONObject.optional(key: String)=if(isNull(key))null else getString(key)
    private fun encodeBook(b: Book)=JSONObject().put("title",b.title).put("publisher",b.publisher).put("edition",b.edition).put("text",b.text).put("source",b.source).put("cover",b.cover)
    private fun decodeBook(b: JSONObject)=Book(b.optString("title"),b.optString("publisher"),b.optString("edition"),b.optional("cover"),b.optString("text"),b.optString("source","manual"))
}
