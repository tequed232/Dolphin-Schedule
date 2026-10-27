package com.dolphin.schedule.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 仅升级时读取旧默认 Profile 的 IndexedDB；不加载旧业务界面或任何网络资源。 */
object LegacyDataReader {
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun read(context: Context): String? {
        // 全新安装不需要创建 WebView；没有系统 WebView 时仍可使用原生课表。
        if(!File(context.applicationInfo.dataDir,"app_webview").exists())return null
        return withContext(Dispatchers.Main) {
        withTimeout(8000) {
            suspendCancellableCoroutine { continuation ->
                val handler=Handler(Looper.getMainLooper());val web=WebView(context)
                var completed=false
                fun close() { handler.removeCallbacksAndMessages(null);web.stopLoading();web.destroy() }
                fun finish(raw: String?) {
                    if(completed || !continuation.isActive)return
                    completed=true
                    try {
                        require(raw!=null && raw.length<=AppDataCodec.MAX_BYTES) { "旧数据读取结果无效或过大" }
                        val result=JSONObject(raw)
                        require(result.optBoolean("success")) { "旧课表读取失败，请重试；原数据保留" }
                        val value=result.optJSONObject("value")?.toString()
                        close();continuation.resume(value)
                    } catch(error: Exception) { close();continuation.resumeWithException(error) }
                }
                fun poll() {
                    if(completed || !continuation.isActive)return
                    web.evaluateJavascript("window.__dolphinLegacyResult || null") { raw ->
                        if(raw==null || raw=="null") handler.postDelayed({poll()},50) else finish(raw)
                    }
                }
                web.settings.javaScriptEnabled=true;web.settings.domStorageEnabled=true
                web.settings.allowFileAccess=false;web.settings.allowContentAccess=false
                web.webViewClient=object: WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView?,request: android.webkit.WebResourceRequest): WebResourceResponse {
                        if(request.isForMainFrame && request.url.toString()=="https://appassets.androidplatform.net/assets/migration.html")return WebResourceResponse("text/html","utf-8","<!doctype html><meta charset=utf-8><title>本地数据迁移</title>".byteInputStream())
                        return WebResourceResponse("text/plain","utf-8",403,"Blocked",emptyMap(),ByteArray(0).inputStream())
                    }
                    override fun onPageFinished(view: WebView?,url: String?) {
                        if(completed || !continuation.isActive)return
                        if(url!="https://appassets.androidplatform.net/assets/migration.html") { finish("{\"success\":false}");return }
                        web.evaluateJavascript("""
                            (() => {
                              try {
                              let created=false;
                              const request=indexedDB.open('dolphin-schedule');
                              request.onupgradeneeded=()=>{created=true;request.transaction.abort();};
                              request.onerror=()=>{window.__dolphinLegacyResult={success:created,value:null};};
                              request.onblocked=()=>{window.__dolphinLegacyResult={success:false};};
                              request.onsuccess=()=>{
                                const db=request.result;
                                if(!db.objectStoreNames.contains('state')){db.close();window.__dolphinLegacyResult={success:false};return;}
                                const value=db.transaction('state').objectStore('state').get('app');
                                value.onsuccess=()=>{db.close();window.__dolphinLegacyResult={success:true,value:value.result??null};};
                                value.onerror=()=>{db.close();window.__dolphinLegacyResult={success:false};};
                              };
                              } catch(error) {window.__dolphinLegacyResult={success:false};}
                            })()
                        """.trimIndent()) { poll() }
                    }
                }
                continuation.invokeOnCancellation { handler.post { if(!completed){completed=true;close()} } }
                web.loadUrl("https://appassets.androidplatform.net/assets/migration.html")
            }
        }
    }
    }
}
