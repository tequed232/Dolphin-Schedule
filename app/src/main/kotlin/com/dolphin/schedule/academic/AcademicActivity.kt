package com.dolphin.schedule.academic

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.webkit.*
import android.widget.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.BackEventCompat
import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.dolphin.schedule.BuildConfig
import java.io.File
import java.util.UUID
import org.json.JSONObject

/** 教务内容进入独立 Profile 或专用进程的数据目录，永不安装 Dolphin 原生桥。 */
open class AcademicActivity : ComponentActivity() {
    protected open val legacyIsolation: Boolean=false
    protected open fun prepareStorage() {}
    private var browser: WebView? = null
    private lateinit var status: TextView
    private lateinit var address: TextView
    private lateinit var recognize: Button
    private var profileName: String? = null
    private var remember = false
    private var loading = false
    private var generation = 0
    private var capture = 0
    private var leaving = false
    private var pageFailure = false
    private var requestId = ""
    private val handler = Handler(Looper.getMainLooper())
    private val browserBack=object: OnBackPressedCallback(false) {
        override fun handleOnBackStarted(backEvent: BackEventCompat) { browser?.animate()?.cancel() }
        override fun handleOnBackProgressed(backEvent: BackEventCompat) {
            browser?.let { view ->
                val value=backEvent.progress.coerceIn(0f,1f)
                view.scaleX=1f-.06f*value;view.scaleY=view.scaleX
                view.translationX=dp(12)*value*if(backEvent.swipeEdge==BackEventCompat.EDGE_LEFT)1f else -1f
            }
        }
        override fun handleOnBackCancelled() {
            browser?.animate()?.scaleX(1f)?.scaleY(1f)?.translationX(0f)?.setDuration(160)?.start()
        }
        override fun handleOnBackPressed() {
            browser?.let { it.animate().cancel();it.scaleX=1f;it.scaleY=1f;it.translationX=0f }
            back()
        }
    }
    private val preferences by lazy { getSharedPreferences(if(legacyIsolation)"academic-legacy" else "academic", MODE_PRIVATE) }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this,browserBack)
        requestId = intent.getStringExtra("requestId") ?: ""
        if(!AcademicPolicy.requestId(requestId)) { finish(); return }
        try { prepareStorage() } catch(_: Exception) {
            AlertDialog.Builder(this).setTitle("教务浏览暂不可用")
                .setMessage("无法准备独立的教务登录存储。请重新打开 App；原课表保留。")
                .setPositiveButton("返回") { _,_->finish() }.setOnCancelListener { finish() }.show()
            return
        }
        if(!legacyIsolation && !WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            AlertDialog.Builder(this).setTitle("请更新系统 WebView")
                .setMessage("教务导入需要独立的登录存储。请更新 Android System WebView 后重试；现有文件导入仍可使用。")
                .setPositiveButton("知道了") { _, _ -> finish() }.setOnCancelListener { finish() }.show()
            return
        }
        val savedProfile = preferences.getString("profile", null)
        var initializationStage = "profile-store"
        try {
            initializationStage = "profile-cleanup"
            if(legacyIsolation) {
                remember=preferences.getBoolean("remember",false)
                if(intent.getBooleanExtra("clearOnly",false)) {
                    check(preferences.edit().remove("remember").commit())
                    clearLegacySession(null) {
                        setResult(RESULT_OK,Intent().putExtra("requestId",requestId).putExtra("cleared",true));finish()
                    }
                    return
                }
            } else {
                val store=ProfileStore.getInstance()
                if(intent.getBooleanExtra("clearOnly", false)) {
                    check(preferences.edit().remove("profile").commit())
                    cleanProfiles(store, null) {
                        setResult(RESULT_OK, Intent().putExtra("requestId", requestId).putExtra("cleared", true)); finish()
                    }
                    return
                }
                cleanProfiles(store, savedProfile) {}
                remember = savedProfile != null
                profileName = savedProfile ?: "${AcademicPolicy.PROFILE_PREFIX}${UUID.randomUUID()}"
            }
            initializationStage = "webview-create"
            val web = WebView(this)
            browser = web
            initializationStage = "profile-assign"
            if(!legacyIsolation)WebViewCompat.setProfile(web, profileName!!)
            // 保留独立配置；退出时清除站点数据，占用中的配置延到新进程再删除。
            initializationStage = "webview-settings"
            with(web.settings) {
                javaScriptEnabled = true; domStorageEnabled = true
                allowFileAccess = false; allowContentAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                javaScriptCanOpenWindowsAutomatically = false; setSupportMultipleWindows(false)
                mediaPlaybackRequiresUserGesture = true; setSupportZoom(true); builtInZoomControls = true; displayZoomControls = false
                useWideViewPort = true; loadWithOverviewMode = true
                userAgentString = DESKTOP_AGENT
            }
            initializationStage = "webview-layout"
            val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(246,247,242)); setPadding(dp(10),dp(6),dp(10),dp(8)) }
            setContentView(root)
            ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                root.setPadding(dp(10)+bars.left,dp(6)+bars.top,dp(10)+bars.right,dp(8)+bars.bottom); insets
            }
            val toolbar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0,0,0,dp(8)) }
            toolbar.addView(button("返回") { back() },LinearLayout.LayoutParams(dp(52),dp(44)))
            address = TextView(this).apply {
                textSize = 13f; setTextColor(Color.rgb(40,56,45)); gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12),0,dp(12),0); text = "jwxt.gdcmxy.edu.cn:1004"; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                background = rounded(Color.WHITE,16); contentDescription = "编辑教务网址"; isFocusable = true
                setOnClickListener { editAddress(web) }
            }
            toolbar.addView(address, LinearLayout.LayoutParams(0,dp(44),1f).apply { setMargins(dp(4),0,dp(4),0) })
            toolbar.addView(button("刷新") { web.reload() },LinearLayout.LayoutParams(dp(52),dp(44))); root.addView(toolbar)
            web.background = rounded(Color.WHITE,18); web.clipToOutline = true
            root.addView(web, LinearLayout.LayoutParams(-1,0,1f))
            status = TextView(this).apply { text = "登录后进入个人学期课表，再点击识别。"; textSize = 12f; setTextColor(Color.rgb(85,104,90)); setPadding(dp(6),dp(8),dp(6),0); accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE }; root.addView(status)
            val session = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            val login = CheckBox(this).apply {
                text = "记住登录 · 仅此设备"; textSize = 13f; setTextColor(Color.rgb(40,56,45)); minHeight = dp(40); buttonTintList = ColorStateList.valueOf(Color.rgb(59,101,80)); isChecked = remember
                setOnCheckedChangeListener { _, checked ->
                    remember = checked
                    if(legacyIsolation)preferences.edit().putBoolean("remember",checked).commit()
                    else if(checked) preferences.edit().putString("profile", profileName).commit()
                    else preferences.edit().remove("profile").commit()
                }
            }; session.addView(login,LinearLayout.LayoutParams(0,dp(44),1f))
            session.addView(button("帮助") { AlertDialog.Builder(this).setTitle("从教务导入").setMessage("1. 登录学校账号，进入个人学期课表。\n2. 选择完整学期和周次，取消“大节次”后查询。\n3. 点击识别，先核对开学日期，再确认课程。\n\n点击地址栏可编辑网址。当前适配本校教务与统一认证站点；不读取或保存密码。").setPositiveButton("知道了",null).show() },LinearLayout.LayoutParams(dp(56),dp(44)))
            root.addView(session)
            recognize = button("识别课表",true) { extract() }; root.addView(recognize,LinearLayout.LayoutParams(-1,dp(46)))
            web.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                    if(AcademicPolicy.allowed(request.url.toString())) return false
                    if(request.isForMainFrame) { pageFailure = true; status.text = "已阻止未适配的网址，请留在学校教务系统；可返回后使用文件导入。" }
                    return true
                }
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse? {
                    return if(request.url.scheme == "https") null else WebResourceResponse("text/plain","utf-8",403,"Blocked",emptyMap(),ByteArray(0).inputStream())
                }
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) { loading = true; pageFailure = false; generation++; capture++; recognize.isEnabled = true; address.text = if(url != null && AcademicPolicy.allowed(url)) android.net.Uri.parse(url).authority else "jwxt.gdcmxy.edu.cn:1004" }
                override fun onPageFinished(view: WebView?, url: String?) { browserBack.isEnabled=view?.canGoBack()==true; loading = false; if(!pageFailure) status.text = if(url?.startsWith("https://sso.gdcmxy.edu.cn/") == true) "请在学校统一身份认证页面登录，完成后返回个人学期课表。" else "请选择学期及完整周次，取消“大节次”后点击查询，再识别课表。" }
                override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler, error: SslError?) { handler.cancel(); loading = false; pageFailure = true; status.text = "教务站点证书验证失败，已停止连接。请联系学校或稍后再试。" }
                override fun onReceivedError(view: WebView?, request: WebResourceRequest, error: WebResourceError) { if(request.isForMainFrame) { loading = false; pageFailure = true; status.text = "教务页面加载失败，请检查网络后刷新；原课表保留不变。" } }
            }
            web.webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
                override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback) { callback.invoke(origin, false, false) }
            }
            initializationStage = "webview-navigate"
            if(legacyIsolation && !remember) {
                recognize.isEnabled=false;loading=true
                clearLegacySession(web) {
                    if(!leaving && !isFinishing && !isDestroyed) { recognize.isEnabled=true;web.loadUrl(AcademicPolicy.ENTRY) }
                }
            } else web.loadUrl(AcademicPolicy.ENTRY)
        } catch(error: Exception) {
            if(BuildConfig.DEBUG) Log.w("DolphinAcademic", "Initialization failed at $initializationStage", error)
            val explanation = if(initializationStage == "profile-cleanup") "上次教务会话暂时无法清理，请完全退出 App 后重新打开。" else "教务浏览初始化失败，请返回后重试；文件导入仍可使用。"
            AlertDialog.Builder(this).setTitle("教务浏览暂不可用").setMessage("$explanation\n原课表未改变。${if(BuildConfig.DEBUG) "\n诊断：$initializationStage / ${error.javaClass.simpleName}" else ""}")
                .setPositiveButton("返回") { _, _ -> finish() }.setOnCancelListener { finish() }.show()
        }
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun button(label: String, primary: Boolean = false, action: () -> Unit) = Button(this).apply {
        text = label; textSize = 13f; isAllCaps = false; minWidth = 0; minimumWidth = 0; minimumHeight = 0; setPadding(dp(4),0,dp(4),0)
        setTypeface(typeface,Typeface.BOLD); setTextColor(if(primary) Color.WHITE else Color.rgb(59,101,80))
        background = RippleDrawable(ColorStateList.valueOf(0x223B6550),rounded(if(primary) Color.rgb(59,101,80) else Color.rgb(232,238,227),14),null)
        setOnClickListener { action() }
    }
    private fun editAddress(web: WebView) {
        val input = EditText(this).apply {
            setSingleLine(true); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO; contentDescription = "教务网址"
            setSelectAllOnFocus(true)
            setText(web.url?.takeIf { AcademicPolicy.allowed(it) } ?: AcademicPolicy.ENTRY); selectAll()
        }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20),dp(4),dp(20),0); addView(input) }
        val dialog = AlertDialog.Builder(this).setTitle("编辑教务网址").setView(box).setNegativeButton("取消",null).setNeutralButton("学校课表") { _, _ -> web.loadUrl(AcademicPolicy.ENTRY) }.setPositiveButton("前往",null).create()
        dialog.setOnShowListener {
            input.requestFocus(); input.selectAll()
            val navigate = {
                val destination = AcademicPolicy.destination(input.text.toString())
                if(destination == null) input.error = "请输入本校 HTTPS 教务或统一认证网址"
                else { dialog.dismiss(); web.loadUrl(destination) }
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { navigate() }
            input.setOnEditorActionListener { _, action, _ -> if(action == EditorInfo.IME_ACTION_GO) { navigate(); true } else false }
        }
        dialog.show()
    }
    private fun extract() {
        val web = browser ?: return
        if(loading || !AcademicPolicy.schedule(web.url ?: "")) { status.text = "请等待加载完成，并进入个人学期课表后再识别。"; return }
        recognize.isEnabled = false; status.text = "正在读取课表…"
        val token = ++capture; val page = generation
        val resultKey = "__dolphinAcademic_${UUID.randomUUID()}"
        handler.postDelayed({ if(token == capture && !leaving) { capture++; web.evaluateJavascript("delete window[${JSONObject.quote(resultKey)}]",null); recognize.isEnabled = true; status.text = "读取超时，请重新查询课表后重试。" } }, 8000)
        val source = try { assets.open("academic/extract.js").bufferedReader().use { it.readText() }.replace("export default async function extractAcademicDocument()", "async function extractAcademicDocument()") }
        catch(_: Exception) { capture++; recognize.isEnabled = true; status.text = "识别组件暂时不可用，请重新打开应用。"; return }
        // evaluateJavascript 不等待 Promise。结果仅暂存在当前页面的随机键中，
        // 由原生轮询取回；教务页面仍不安装任何 Dolphin 原生桥。
        web.evaluateJavascript("(() => { $source; extractAcademicDocument().then(value => { window[${JSONObject.quote(resultKey)}] = value; }).catch(() => { window[${JSONObject.quote(resultKey)}] = {error:'课表读取失败，请重试'}; }); return true; })()") {
            readExtractedResult(web, resultKey, token, page)
        }
    }
    private fun readExtractedResult(web: WebView, resultKey: String, token: Int, page: Int) {
        if(leaving || token != capture || page != generation || !AcademicPolicy.schedule(web.url ?: "")) return
        val key = JSONObject.quote(resultKey)
        web.evaluateJavascript("(() => { if(!Object.prototype.hasOwnProperty.call(window,$key)) return null; const value = window[$key]; delete window[$key]; return value; })()") { raw ->
            if(leaving || token != capture || page != generation || !AcademicPolicy.schedule(web.url ?: "")) return@evaluateJavascript
            if(raw == null || raw == "null") { handler.postDelayed({ readExtractedResult(web,resultKey,token,page) },100); return@evaluateJavascript }
            capture++; recognize.isEnabled = true
            try {
                if(raw.toByteArray(Charsets.UTF_8).size > AcademicPolicy.MAX_BYTES) throw IllegalArgumentException()
                val result = JSONObject(raw)
                if(result.has("error")) { status.text = result.optString("error").take(300); return@evaluateJavascript }
                if(result.optInt("schema") != 1 || result.optString("school") != "gdcmxy") throw IllegalArgumentException()
                File(cacheDir,"academic-$requestId.json").writeText(raw, Charsets.UTF_8)
                setResult(RESULT_OK, Intent().putExtra("requestId", requestId)); finish()
            } catch(_: Exception) { status.text = "读取结果无效或过大，原课表保留不变，请重新查询。" }
        }
    }
    private fun back() { val web = browser; if(web?.canGoBack() == true) web.goBack() else finish() }
    private fun clearLegacySession(web: WebView?,done: ()->Unit) {
        check(legacyIsolation)
        // 此分支只在 :academic_legacy 进程运行，其默认存储已由目录后缀与主应用隔离。
        web?.clearCache(true)
        WebStorage.getInstance().deleteAllData()
        CookieManager.getInstance().removeAllCookies { CookieManager.getInstance().flush();done() }
    }
    private fun clearProfileData(name: String, profile: Profile, done: () -> Unit) {
        retiredProfiles.add(name)
        // 两个 API 都属于这个教务 Profile，不是 Dolphin 默认 Profile。
        profile.webStorage.deleteAllData()
        profile.cookieManager.removeAllCookies { profile.cookieManager.flush(); done() }
    }
    private fun cleanProfiles(store: ProfileStore, keep: String?, done: () -> Unit) {
        var pending = 1
        val complete = { pending--; if(pending == 0) done() }
        AcademicProfileCleanup.clean(store.allProfileNames, keep, retiredProfiles, { store.deleteProfile(it) }) { name ->
            val profile = store.getProfile(name)
            if(profile != null) { pending++; clearProfileData(name, profile, complete) }
        }
        complete()
    }
    override fun onDestroy() {
        leaving = true; capture++; handler.removeCallbacksAndMessages(null)
        browser?.let { web ->
            if(legacyIsolation && !remember)runCatching { clearLegacySession(web) {} }
            else if(!remember && profileName != null && WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                runCatching { clearProfileData(profileName!!, WebViewCompat.getProfile(web)) {} }
                    .onFailure { if(BuildConfig.DEBUG) Log.w("DolphinAcademic", "Session cleanup failed", it) }
            }
            (web.parent as? android.view.ViewGroup)?.removeView(web); web.stopLoading(); web.destroy()
        }; browser = null
        super.onDestroy()
    }
    companion object {
        private val retiredProfiles = mutableSetOf<String>()
        private const val DESKTOP_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
    }
}
