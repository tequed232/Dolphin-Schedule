package com.dolphin.schedule.importing

import com.dolphin.schedule.data.AppDataCodec
import com.dolphin.schedule.domain.*
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

enum class ImportFormat(val title: String) { JSON("JSON"),CSV("CSV"),EXCEL("Excel") }
class FormatMismatch(message: String,val suggested: ImportFormat?=null): IllegalArgumentException(message)
data class CellMerge(val firstRow: Int,val firstColumn: Int,val lastRow: Int,val lastColumn: Int)
data class ImportSheet(val name: String,val rows: List<List<String>>,val merges: List<CellMerge> = emptyList())
data class SheetMapping(val dayRow: Int,val sectionColumn: Int,val firstRow: Int,val lastRow: Int,val firstColumn: Int,val lastColumn: Int)
data class ImportSource(val format: ImportFormat,val sheets: List<ImportSheet> = emptyList(),val json: String?=null)

object TableImport {
    const val MAX_ROWS=3000
    const val MAX_COLUMNS=100
    internal val aliases=linkedMapOf(
        "name" to listOf("课程名","课程名称","课程","name","courseName"),"day" to listOf("星期","周几","day","dayOfWeek"),
        "sections" to listOf("节次","时段","section","sections","period"),"start" to listOf("开始节次","start","startSection"),"end" to listOf("结束节次","end","endSection"),
        "startTime" to listOf("上课时间","开始时间","startTime","timeStart"),"endTime" to listOf("下课时间","结束时间","endTime","timeEnd"),
        "timeRange" to listOf("时间段","时间","上课下课时间","time","timeRange"),"weeks" to listOf("周次","weeks"),"teacher" to listOf("教师","老师","teacher"),
        "room" to listOf("教室","地点","room","classroom"),"notes" to listOf("备注","notes","note"),
    ).flatMap { (key,names) -> names.map { it to key } }.toMap()
    fun decodeText(bytes: ByteArray): String {
        val first=bytes.getOrNull(0)?.toInt()?.and(255);val second=bytes.getOrNull(1)?.toInt()?.and(255)
        val encoding=if(first==255 && second==254)"UTF-16LE" else if(first==254 && second==255)"UTF-16BE" else "UTF-8"
        fun decode(name: String)=Charset.forName(name).newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
        return runCatching { decode(encoding) }.getOrElse { runCatching { decode("GB18030") }.getOrElse { error("无法读取文本编码，请使用 UTF-8 或 GB18030") } }
    }
    fun detect(name: String,bytes: ByteArray): ImportFormat {
        require(bytes.isNotEmpty()) { "文件为空，请选择包含课表的文件" };require(bytes.size<=FileNormalizer.MAX_BYTES) { "文件超过 5 MB，请精简后导入" }
        val extension=name.substringAfterLast('.').lowercase(java.util.Locale.ROOT)
        val expected=when(extension){"json"->ImportFormat.JSON;"csv"->ImportFormat.CSV;"xls","xlsx"->ImportFormat.EXCEL;else->null}
        val binary=isWorkbook(bytes)
        val raw=if(binary)"" else decodeText(bytes).trim()
        val actual=when { binary->ImportFormat.EXCEL;Regex("^(?:[\\{\\[]|```json)",RegexOption.IGNORE_CASE).containsMatchIn(raw)->ImportFormat.JSON
            raw.lineSequence().firstOrNull()?.let { line -> line.any { it in ",;\t" } }==true && raw.any { it=='\n' || it=='\r' }->ImportFormat.CSV;else->null }
        if(expected!=null && actual!=null && expected!=actual)throw FormatMismatch("扩展名 .$extension 与内容不一致，内容更像 ${actual.title}，请指定格式后继续。",actual)
        return actual ?: expected ?: throw FormatMismatch("无法自动识别格式，请指定 JSON、CSV 或 Excel。")
    }
    fun isWorkbook(bytes: ByteArray): Boolean=(bytes.getOrNull(0)==0x50.toByte() && bytes.getOrNull(1)==0x4b.toByte()) || (bytes.getOrNull(0)==0xd0.toByte() && bytes.getOrNull(1)==0xcf.toByte())
    fun csv(raw: String): List<List<String>> {
        val first=raw.lineSequence().firstOrNull().orEmpty();val separator=if('\t' in first)'\t' else if(';' in first && ',' !in first)';' else ','
        val rows=mutableListOf<List<String>>();val row=mutableListOf<String>();val field=StringBuilder();var quoted=false;var i=0
        fun endField() { row+=field.toString();field.setLength(0);require(row.size<=MAX_COLUMNS) { "表格超过 100 列" } }
        fun endRow() { endField();if(row.any { it.isNotBlank() })rows+=row.toList();row.clear();require(rows.size<=MAX_ROWS) { "表格超过 3000 行" } }
        while(i<raw.length) {
            val c=raw[i]
            when {
                c=='"' -> if(quoted && raw.getOrNull(i+1)=='"'){field.append('"');i++} else if(quoted || field.isEmpty())quoted=!quoted else field.append(c)
                !quoted && c==separator->endField()
                !quoted && (c=='\n' || c=='\r')->{if(c=='\r' && raw.getOrNull(i+1)=='\n')i++;endRow()}
                else->field.append(c)
            }
            i++
        }
        require(!quoted) { "CSV 引号未闭合" };endRow();return rows
    }
    internal fun section(raw: String): List<Int> {
        val chinese=(1..24).associateBy { n -> if(n<10)"一二三四五六七八九"[n-1].toString() else (if(n>=20)"二十" else "十")+(if(n%10>0)"一二三四五六七八九"[n%10-1] else "") }
        val source=text(raw).removePrefix("第").removeSuffix("节").replace(Regex("[～~至—–]"),"-").split('-').joinToString("-") { chinese[it]?.toString() ?: it }
        return numbers(source,24,"节次")
    }
    fun mapping(sheet: ImportSheet): SheetMapping {
        val rows=sheet.rows;var dayRow=-1;var best=0
        rows.take(30).forEachIndexed { i,row -> val count=row.count { runCatching { day(it) }.isSuccess };if(count>best){best=count;dayRow=i} }
        require(best>=2) { "未识别星期行，请手动指定星期行、节次列与课程区域" }
        var column=-1;var matches=0
        for(c in 0 until minOf(MAX_COLUMNS,rows.maxOfOrNull { it.size } ?: 0)) { val count=rows.drop(dayRow+1).count { runCatching { section(it.getOrNull(c).orEmpty()) }.isSuccess };if(count>matches){matches=count;column=c} }
        require(column>=0) { "未识别节次列，请手动映射" }
        val columns=rows[dayRow].indices.filter { runCatching { day(rows[dayRow][it]) }.isSuccess }
        return SheetMapping(dayRow,column,dayRow+1,rows.lastIndex,columns.first(),columns.last())
    }
    fun parse(source: ImportSource,current: Schedule,index: Int=0,mapping: SheetMapping?=null): FilePreview = if(source.format==ImportFormat.JSON)FileNormalizer.json(source.json.orEmpty(),current)
        else parseSheet(source.sheets.getOrNull(index) ?: error("请选择工作表"),current,mapping)
    fun parseSheet(sheet: ImportSheet,current: Schedule,mapping: SheetMapping?=null): FilePreview {
        require(sheet.rows.size<=MAX_ROWS && sheet.rows.all { it.size<=MAX_COLUMNS }) { "工作表超过 3000 行或 100 列" }
        val rows=sheet.rows.map { it.map(String::trim) };require(rows.any { row -> row.any { it.isNotEmpty() } }) { "工作表为空，请选择其他工作表" }
        val errors=mutableListOf<String>();val raw=mutableListOf<Map<String,Any?>>();val notes=mutableListOf<String>();val times=mutableListOf<ImportedTime>();val rests=mutableListOf<RestTime>()
        fun cell(r: Int,c: Int)=rows.getOrNull(r)?.getOrNull(c).orEmpty()
        fun rowTime(item: Map<String,Any?>,first: Int,last: Int,single: Boolean=false): ImportedTime? {
            val r=integer(item["_row"],"行号")
            try { val time=timing(item.filterKeys { it in listOf("startTime","endTime","timeRange") });if(time==null){require(!single) { "上课和下课时间必须同时填写" };return null};require(!single || first==last) { "独立时间行只能填写一节" };return ImportedTime(first,last,time.first,time.second,r,single) }
            catch(failure: Exception) { throw IllegalArgumentException("第 $r 行：${failure.message}",failure) }
        }
        fun addRest(item: Map<String,Any?>,label: String) { val time=rowTime(item,1,1,true)!!;rests+=RestTime(label,time.start,time.end) }
        val headerRow=rows.indexOfFirst { row -> val headers=row.map { aliases[it] };headers.contains("name") && headers.contains("day") || row.count { runCatching { day(it) }.isSuccess }<2 && headers.any { it in listOf("startTime","endTime","timeRange") } && headers.any { it in listOf("sections","start") } }
        if(headerRow>=0 && mapping==null) {
            val headers=rows[headerRow].map { aliases[it] ?: it }
            listOf("startTime","endTime","timeRange").forEach { h -> require(headers.count { it==h }<=1) { "第 ${headerRow+1} 行存在重复的时间列" } }
            rows.drop(headerRow+1).forEachIndexed { i,row ->
                if(row.none { it.isNotBlank() })return@forEachIndexed
                val item=headers.mapIndexed { j,h -> h to row.getOrNull(j).orEmpty() }.toMap().toMutableMap<String,Any?>();item["_row"]=headerRow+i+2
                val label=restLabel(item["sections"]).ifEmpty { if(text(item["day"]).isBlank())restLabel(item["name"]) else "" }
                if(label.isNotEmpty()){require(text(item["day"]).isBlank()) { "第 ${item["_row"]} 行：休息是每日配置，请清空星期" };addRest(item,label);return@forEachIndexed}
                val hasTime=listOf("startTime","endTime","timeRange").any { text(item[it]).isNotEmpty() }
                val timeOnly=text(item["name"]).isEmpty() && text(item["day"]).isEmpty() && (hasTime || "name" !in headers && "day" !in headers)
                if(text(item["sections"]).isNotEmpty())try { item["sections"]=section(text(item["sections"])).joinToString(",") } catch(failure: Exception) {
                    if(timeOnly || hasTime)throw IllegalArgumentException("第 ${item["_row"]} 行：${failure.message}",failure)
                    errors+="第 ${item["_row"]} 行：${failure.message}";return@forEachIndexed
                }
                if(timeOnly) {
                    val slots=try { section(text(item["sections"]).ifBlank { "${text(item["start"])}-${text(item["end"]).ifBlank { text(item["start"]) }}" }) } catch(failure: Exception) { throw IllegalArgumentException("第 ${item["_row"]} 行：${failure.message}",failure) }
                    times+=rowTime(item,slots.first(),slots.last(),true)!!
                } else raw+=item
            }
        } else {
            val m=mapping ?: mapping(sheet)
            require(listOf(m.dayRow,m.sectionColumn,m.firstRow,m.lastRow,m.firstColumn,m.lastColumn).all { it>=0 } && m.dayRow<rows.size && m.lastRow<rows.size && m.firstRow<=m.lastRow && m.firstColumn<=m.lastColumn && m.lastColumn<MAX_COLUMNS && m.sectionColumn<MAX_COLUMNS) { "映射范围无效，行列从 1 开始" }
            val days=(m.firstColumn..m.lastColumn).mapNotNull { c -> runCatching { c to day(cell(m.dayRow,c)) }.getOrNull() };require(days.isNotEmpty()) { "所选星期行中没有可识别的星期" }
            val headers=rows[m.dayRow].map { aliases[it] };val a=headers.indexOf("startTime");val b=headers.indexOf("endTime");val range=headers.indexOf("timeRange")
            if(a>=0 || b>=0 || range>=0)for(r in m.firstRow..m.lastRow) {
                val item=mapOf("_row" to r+1,"startTime" to cell(r,a),"endTime" to cell(r,b),"timeRange" to cell(r,range))
                if(listOf("startTime","endTime","timeRange").all { text(item[it]).isBlank() })continue
                val label=restLabel(cell(r,m.sectionColumn));if(label.isNotEmpty()){addRest(item,label);continue}
                val slots=section(cell(r,m.sectionColumn));rowTime(item,slots.first(),slots.last(),slots.size==1)?.let(times::add)
            }
            for(r in m.firstRow..m.lastRow)for((c,day) in days) {
                if(restLabel(cell(r,m.sectionColumn)).isNotEmpty())continue
                val value=cell(r,c);if(value.isBlank())continue
                val merge=sheet.merges.firstOrNull { r in it.firstRow..it.lastRow && c in it.firstColumn..it.lastColumn }
                if(merge!=null && (r!=merge.firstRow || c!=merge.firstColumn))continue
                try {
                    require(merge==null || merge.firstColumn==merge.lastColumn) { "课程横跨多个星期，请拆分或核对" }
                    val start=section(cell(r,m.sectionColumn)).first();val end=section(cell(minOf(merge?.lastRow ?: r,m.lastRow),m.sectionColumn)).last()
                    val lines=value.lines().map(String::trim).filter(String::isNotEmpty)
                    fun tagged(vararg tags: String): String?=lines.drop(1).firstOrNull { line -> tags.any { line.startsWith("$it：") || line.startsWith("$it:") } }?.replace(Regex("^[^:：]+[:：]\\s*"),"")
                    raw+=mapOf("name" to lines.first(),"day" to day,"start" to start,"end" to end,"teacher" to tagged("教师","老师"),"room" to tagged("教室"),"weeks" to tagged("周次")?.takeIf { it.isNotBlank() },"notes" to lines.drop(1).joinToString("\n"),"_row" to r+1)
                } catch(failure: Exception) { errors+="第 ${r+1} 行，列 ${c+1}：${failure.message}" }
            }
        }
        require(raw.isNotEmpty() || times.isNotEmpty() || rests.isNotEmpty()) { errors.joinToString("\n").ifBlank { "没有识别到课程或作息，请调整映射" } }
        val validation=current.copy(periods=(0 until MAX_PERIODS).map { current.periods.getOrNull(it) ?: Period("","") })
        val normalized=mutableListOf<Course>();val valid=mutableListOf<Map<String,Any?>>()
        raw.forEach { item ->
            rowTime(item,1,1)
            val stripped=item.filterKeys { it !in listOf("startTime","endTime","timeRange") }
            val course=try { FileNormalizer.normalize(mapOf("courses" to listOf(stripped)),validation).schedule.courses.single() }
                catch(failure: Exception) { errors+="第 ${item["_row"]} 行：${failure.message}";return@forEach }
            rowTime(item,course.start,course.end)?.let(times::add);normalized+=course;valid+=stripped
        }
        require(!(raw.isNotEmpty() && normalized.isEmpty()) && (normalized.isNotEmpty() || times.isNotEmpty() || rests.isNotEmpty())) { errors.joinToString("\n").ifBlank { "没有有效课程或时间" } }
        val periods=applyRests(applyTimes(current,normalized,times,notes),rests)
        val source=map(jsonValue(AppDataCodec.encodeSchedule(current.copy(periods=periods,courses=emptyList())))).toMutableMap()
        if(raw.isNotEmpty())source["courses"]=valid else source.remove("courses")
        val result=FileNormalizer.normalize(source,current)
        if(errors.isNotEmpty())notes+="成功识别 ${normalized.size} 个课程块，跳过 ${errors.size} 项，请核对错误明细。"
        return result.copy(schedule=result.schedule.copy(importNotes=(result.schedule.importNotes+notes).distinct()),errors=errors)
    }
}
