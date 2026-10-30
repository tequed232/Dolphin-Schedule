package com.dolphin.schedule.academic

import android.annotation.SuppressLint
import android.content.Context
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.webkit.*
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.*
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class AcademicReadFailure(val status: String,message: String): IllegalStateException(message)

/** 短时无界面读取，复用已记住的学校 Profile；不启动 Activity、不安装原生桥。 */
internal object AcademicBackgroundReader {
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun read(context: Context,legacy: Boolean,term: String,weeks: Int): String=withContext(Dispatchers.Main.immediate) {
        require(weeks in 1..60 && Regex("\\d{4}-\\d{4}-[12]").matches(term)) { "请先完成一次本校教务导入" }
        if(AcademicSessionGate.interactive || AcademicSessionGate.cancelRead!=null)throw AcademicReadFailure("busy","教务浏览正在使用中，稍后再检查")
        if(legacy)LegacyAcademicActivity.prepareIsolatedStorage()
        val preferences=context.getSharedPreferences(if(legacy)"academic-legacy" else "academic",Context.MODE_PRIVATE)
        val profile=if(legacy)null else preferences.getString("profile",null)
        if(if(legacy)!preferences.getBoolean("remember",false) else profile==null || !profile.startsWith(AcademicPolicy.PROFILE_PREFIX))throw AcademicReadFailure("login","请在教务页面登录并勾选记住登录，再手动识别一次")
        if(!legacy && !WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE))throw AcademicReadFailure("unsupported","当前教务登录环境已变化，请手动重新打开教务页面")
        val bootstrap=context.assets.open("academic/check.js").bufferedReader().use { it.readText() }
        val extractor=context.assets.open("academic/extract.js").bufferedReader().use { it.readText() }.replace("export default async function extractAcademicDocument()","async function extractAcademicDocument()")
        suspendCancellableCoroutine { continuation ->
            val handler=Handler(Looper.getMainLooper());var web: WebView?=null;var complete=false;var queried=false;var extracting=false
            val key="__dolphinCheck_"+java.util.UUID.randomUUID().toString().replace("-","")
            fun finish(value: String?=null,failure: Throwable?=null) {
                if(complete)return
                complete=true;handler.removeCallbacksAndMessages(null);AcademicSessionGate.cancelRead=null
                web?.let { runCatching { it.stopLoading();it.destroy() } };web=null
                if(continuation.isActive) { if(failure!=null)continuation.resumeWithException(failure) else continuation.resume(requireNotNull(value)) }
            }
            fun fail(status: String,message: String)=finish(failure=AcademicReadFailure(status,message))
            fun decode(value: String?): JSONObject?=runCatching { (JSONTokener(value ?: "null").nextValue() as? String)?.let(::JSONObject) }.getOrNull()
            lateinit var extract: ()->Unit
            fun pollResult() {
                if(complete)return
                web?.evaluateJavascript("JSON.stringify(window[${JSONObject.quote(key)}] || null)") { encoded ->
                    if(complete)return@evaluateJavascript
                    val result=decode(encoded)
                    if(result==null)handler.postDelayed({pollResult()},250)
                    else if(result.has("error")) {
                        // 查询结束后页面仍可能重绘；结构不完整时重试，不把旧表或空表当更新。
                        extracting=false;handler.postDelayed({extract()},500)
                    }
                    else finish(result.toString())
                }
            }
            extract={
                if(!complete && !extracting)web?.evaluateJavascript("JSON.stringify(window.__dolphinReadOnlyQuery || null)") { encoded ->
                    if(!complete) {
                        val query=decode(encoded)
                        if(query?.optBoolean("failed")==true)fail("error","学校课表查询失败，请手动查询后重试")
                        else if(query==null || query.optInt("pending")!=0 || query.optInt("completed")==0)handler.postDelayed({extract()},500)
                        else {
                            extracting=true
                            web?.evaluateJavascript("window[${JSONObject.quote(key)}]=null;\n"+extractor+"\nextractAcademicDocument().then(value=>{window[${JSONObject.quote(key)}]=value}).catch(()=>{window[${JSONObject.quote(key)}]={error:'read'}})",null)
                            pollResult()
                        }
                    }
                }
            }
            fun prepare() {
                if(complete || queried)return
                web?.evaluateJavascript(bootstrap+"\nJSON.stringify(prepareAcademicCheck(${JSONObject.quote(term)},$weeks))") { encoded ->
                    if(complete)return@evaluateJavascript
                    val result=decode(encoded)
                    when(result?.optString("error")) {
                        "login" -> fail("login","教务登录已失效，请在 App 内重新登录并识别")
                        "term" -> fail("term","教务当前学期已变化，请手动选择学期并识别")
                        else -> if(result?.optBoolean("queried")==true) {
                            queried=true
                            handler.postDelayed({extract()},1000)
                        } else handler.postDelayed({prepare()},500)
                    }
                }
            }
            try {
                AcademicSessionGate.cancelRead={fail("busy","用户已打开教务浏览，本次后台检查已停止")}
                web=WebView(context).apply {
                    if(!legacy)WebViewCompat.setProfile(this,requireNotNull(profile))
                    with(settings) {
                        javaScriptEnabled=true;domStorageEnabled=true;allowFileAccess=false;allowContentAccess=false
                        mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW;javaScriptCanOpenWindowsAutomatically=false;setSupportMultipleWindows(false)
                        mediaPlaybackRequiresUserGesture=true;useWideViewPort=true;loadWithOverviewMode=true
                        userAgentString="Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
                    }
                    measure(android.view.View.MeasureSpec.makeMeasureSpec(1280,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(2400,android.view.View.MeasureSpec.EXACTLY));layout(0,0,1280,2400)
                    webChromeClient=object: WebChromeClient() {
                        override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
                        override fun onGeolocationPermissionsShowPrompt(origin: String?,callback: GeolocationPermissions.Callback) { callback.invoke(origin,false,false) }
                    }
                    webViewClient=object: WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?,request: WebResourceRequest): Boolean {
                            if(!AcademicPolicy.schedule(request.url.toString())) { handler.post { fail("login","教务登录已失效，请在 App 内重新登录") };return true };return false
                        }
                        override fun shouldInterceptRequest(view: WebView?,request: WebResourceRequest): WebResourceResponse? {
                            // 学校课表查询使用 POST；拒绝其他非 GET/HEAD 操作，防止后台提交写入。
                            val allowed=request.method in listOf("GET","HEAD") || (request.method=="POST" && request.url.host=="jwxt.gdcmxy.edu.cn" && request.url.path=="/api/arrange/CourseScheduleAllQuery/studentCourseSchedule")
                            if(!allowed)android.util.Log.i("DolphinAcademicCheck","Blocked non-read request: "+request.method+" "+(request.url.lastPathSegment?.takeIf { Regex("[A-Za-z]{1,50}").matches(it) } ?: "[redacted]"))
                            return if(allowed)null else WebResourceResponse("text/plain","utf-8",403,"Read only",emptyMap(),java.io.ByteArrayInputStream(ByteArray(0)))
                        }
                        override fun onPageFinished(view: WebView?,url: String?) { if(!complete && !queried)handler.postDelayed({prepare()},1000) }
                        override fun onReceivedSslError(view: WebView?,ssl: SslErrorHandler,error: SslError?) { ssl.cancel();fail("error","教务站点证书校验失败，检查已停止") }
                        override fun onReceivedError(view: WebView?,request: WebResourceRequest,error: WebResourceError) { if(request.isForMainFrame)fail("error","教务页面无法加载，请检查网络后重试") }
                        override fun onRenderProcessGone(view: WebView?,detail: RenderProcessGoneDetail): Boolean { fail("error","WebView 读取进程已退出，原课表保留");return true }
                    }
                    loadUrl(AcademicPolicy.ENTRY)
                }
                handler.postDelayed({fail("error","教务检查超时，请手动重新登录或稍后检查")},45_000)
                continuation.invokeOnCancellation { handler.post { finish(failure=CancellationException()) } }
            } catch(failure: Exception) { finish(failure=failure) }
        }
    }
}
