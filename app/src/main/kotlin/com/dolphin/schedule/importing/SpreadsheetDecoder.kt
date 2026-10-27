package com.dolphin.schedule.importing

import android.annotation.SuppressLint
import android.content.Context
import android.util.Base64
import android.webkit.*
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.*
import org.json.JSONObject
import org.json.JSONTokener

/** SheetJS 只解码 XLS/XLSX 单元格；课程、时间、映射、预览和保存全部由 Kotlin 负责。 */
object SpreadsheetDecoder {
    private const val ORIGIN="https://appassets.androidplatform.net"
    private const val ENTRY="$ORIGIN/spreadsheet/index.html"
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun decode(context: Context,bytes: ByteArray): List<ImportSheet> {
        require(bytes.size in 1..FileNormalizer.MAX_BYTES && TableImport.isWorkbook(bytes)) { "无法解析 Excel，请检查是否为损坏、加密或格式不符的 XLS/XLSX 文件" }
        withContext(Dispatchers.IO) { validateArchive(bytes) }
        return withContext(Dispatchers.Main) {
            val web=WebView(context);var ready=false;var failed=false
            try {
                web.settings.javaScriptEnabled=true;web.settings.allowFileAccess=false;web.settings.allowContentAccess=false
                web.settings.domStorageEnabled=false;web.settings.mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
                web.webViewClient=object: WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?,request: WebResourceRequest)=request.url.toString()!=ENTRY
                    override fun shouldInterceptRequest(view: WebView?,request: WebResourceRequest): WebResourceResponse {
                        val path=request.url.toString()
                        if(path==ENTRY && request.isForMainFrame)return WebResourceResponse("text/html","utf-8","<!doctype html><meta charset=utf-8><meta http-equiv=Content-Security-Policy content=\"default-src 'none'; script-src 'self'; connect-src 'none'\"><script src='/spreadsheet/xlsx.js'></script>".byteInputStream())
                        if(path=="$ORIGIN/spreadsheet/xlsx.js")runCatching { context.assets.open("spreadsheet/xlsx.js") }.getOrNull()?.let { return WebResourceResponse("application/javascript","utf-8",it) }
                        return WebResourceResponse("text/plain","utf-8",403,"Blocked",emptyMap(),ByteArray(0).inputStream())
                    }
                    override fun onPageFinished(view: WebView?,url: String?) { ready=url==ENTRY;if(!ready)failed=true }
                    override fun onReceivedError(view: WebView?,request: WebResourceRequest,error: WebResourceError) { failed=true }
                    override fun onRenderProcessGone(view: WebView?,detail: RenderProcessGoneDetail): Boolean { failed=true;return true }
                }
                web.webChromeClient=object: WebChromeClient() { override fun onConsoleMessage(message: ConsoleMessage?)=true }
                suspend fun evaluate(script: String): Any?=suspendCancellableCoroutine { continuation ->
                    web.evaluateJavascript(script) { result -> if(continuation.isActive)try { continuation.resume(if(result==null || result=="null")null else JSONTokener(result).nextValue()) } catch(failure: Exception) { continuation.resumeWithException(failure) } }
                }
                val decoded=withTimeoutOrNull(15_000) {
                    web.loadUrl(ENTRY)
                    while(!ready && !failed)delay(25)
                    require(!failed && evaluate("typeof XLSX==='object'")==true) { "本地 Excel 解码器无法启动，请重新安装完整版本" }
                    evaluate("window.__dolphinChunks=[]")
                    for(offset in bytes.indices step 36_000) {
                        val encoded=Base64.encodeToString(bytes.copyOfRange(offset,minOf(offset+36_000,bytes.size)),Base64.NO_WRAP)
                        evaluate("window.__dolphinChunks.push("+JSONObject.quote(encoded)+")")
                    }
                    evaluate(SCRIPT)
                    val length=(evaluate("window.__dolphinOutput.length") as? Number)?.toInt() ?: error("Excel 解码结果无效")
                    require(length in 1..12_000_000) { "工作簿解码后过大，请按学期拆分" }
                    val output=StringBuilder(length)
                    for(offset in 0 until length step 36_000)output.append(evaluate("window.__dolphinOutput.slice($offset,${minOf(offset+36_000,length)})") as? String ?: error("Excel 结果读取失败"))
                    parseOutput(output.toString())
                }
                decoded ?: error("Excel 解析超时，请精简文件或另存为 CSV 后重试")
            } finally { web.stopLoading();web.destroy() }
        }
    }
    private fun validateArchive(bytes: ByteArray) {
        if(bytes[0]!=0x50.toByte())return
        var total=0L;var entries=0;val buffer=ByteArray(8192)
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while(zip.nextEntry!=null) {
                require(++entries<=3000) { "工作簿内部文件过多，请精简后导入" }
                while(true) { val n=zip.read(buffer);if(n<0)break;total+=n;require(total<=64L*1024*1024) { "Excel 解压后超过 64 MB，请按学期拆分" } }
            }
        }
    }
    internal fun parseOutput(raw: String): List<ImportSheet> {
        val root=JSONObject(raw)
        require(root.optBoolean("success")) { root.optString("error","Excel 无法识别，请另存为 XLSX 或 CSV 后重试") }
        val sheets=root.getJSONArray("sheets");require(sheets.length() in 1..100) { "工作表数量应为 1～100" }
        return (0 until sheets.length()).map { i ->
            val sheet=sheets.getJSONObject(i);val rows=sheet.getJSONArray("rows");val merges=sheet.getJSONArray("merges")
            require(rows.length()<=TableImport.MAX_ROWS && merges.length()<=10_000) { "工作表过大" }
            ImportSheet(sheet.getString("name"),(0 until rows.length()).map { r -> val row=rows.getJSONArray(r);require(row.length()<=TableImport.MAX_COLUMNS);(0 until row.length()).map(row::getString) },
                (0 until merges.length()).map { m -> val merge=merges.getJSONObject(m);val first=merge.getJSONObject("s");val last=merge.getJSONObject("e");CellMerge(first.getInt("r"),first.getInt("c"),last.getInt("r"),last.getInt("c")) })
        }
    }
    private val SCRIPT="""
        (()=>{try{
          const binary=atob(window.__dolphinChunks.join(''));window.__dolphinChunks=null;
          const bytes=new Uint8Array(binary.length);for(let i=0;i<binary.length;i++)bytes[i]=binary.charCodeAt(i);
          const book=XLSX.read(bytes,{type:'array',cellDates:false,sheetRows:3001});
          if(!book.SheetNames.length||book.SheetNames.length>100)throw Error('工作表数量应为 1～100');
          let cells=0;
          for(const name of book.SheetNames){const sheet=book.Sheets[name],ref=sheet['!fullref']||sheet['!ref'];
            if(ref){const range=XLSX.utils.decode_range(ref);if(range.e.r>=3000||range.e.c>=100)throw Error('工作表「'+name+'」超过 3000 行或 100 列');cells+=(range.e.r+1)*(range.e.c+1);if(cells>300000)throw Error('工作簿超过 30 万个单元格，请按学期拆分');}}
          const sheets=book.SheetNames.map(name=>({name,rows:XLSX.utils.sheet_to_json(book.Sheets[name],{header:1,defval:'',raw:false,blankrows:true}).map(row=>row.map(value=>String(value))),merges:book.Sheets[name]['!merges']||[]}));
          window.__dolphinOutput=JSON.stringify({success:true,sheets});
        }catch(error){window.__dolphinOutput=JSON.stringify({success:false,error:/超过|工作表|工作簿/.test(error.message)?error.message:'无法解析 Excel，请检查文件是否损坏、加密或格式不符。'});}})()
    """.trimIndent()
}
