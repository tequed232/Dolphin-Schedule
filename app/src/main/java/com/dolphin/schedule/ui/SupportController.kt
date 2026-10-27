package com.dolphin.schedule.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dolphin.schedule.BuildConfig
import com.dolphin.schedule.domain.RELEASE_API
import com.dolphin.schedule.domain.ReleaseInfo
import com.dolphin.schedule.domain.parseOfficialRelease
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** 检查只由更新页面触发，无通知、闹钟、后台调度或自动下载安装。 */
class SupportController(
    private val scope: CoroutineScope,
    val installedVersion: String=BuildConfig.VERSION_NAME,
    private val fetch: suspend ()->ReleaseInfo?={ fetchOfficialRelease() },
) {
    var release by mutableStateOf<ReleaseInfo?>(null);private set
    var busy by mutableStateOf(false);private set
    var checked by mutableStateOf(false);private set
    var checkedAt by mutableStateOf<Long?>(null);private set
    var error by mutableStateOf<String?>(null);private set
    var noRelease by mutableStateOf(false);private set

    fun ensureChecked() { if(!checked && !busy)check() }
    fun check() {
        if(busy)return
        busy=true;error=null
        scope.launch {
            try {
                val result=fetch()
                release=result;noRelease=result==null;checkedAt=System.currentTimeMillis()
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(failure: Exception) { error=failure.message?.take(300) ?: "检查未完成，请稍后重试" }
            finally { checked=true;busy=false }
        }
    }
}

private suspend fun fetchOfficialRelease(): ReleaseInfo?=withContext(Dispatchers.IO) {
    val connection=URL(RELEASE_API).openConnection() as HttpsURLConnection
    connection.connectTimeout=5_000;connection.readTimeout=5_000;connection.instanceFollowRedirects=false
    connection.setRequestProperty("Accept","application/vnd.github+json")
    connection.setRequestProperty("X-GitHub-Api-Version","2022-11-28")
    connection.setRequestProperty("User-Agent","Dolphin-Schedule/${BuildConfig.VERSION_NAME}")
    try {
        when(val code=connection.responseCode) {
            404 -> null
            200 -> {
                val bytes=connection.inputStream.use { input ->
                    val output=ByteArrayOutputStream();val buffer=ByteArray(8192)
                    while(true) { val count=input.read(buffer);if(count<0)break;require(output.size()+count<=2_000_000) { "版本信息过大" };output.write(buffer,0,count) }
                    output.toByteArray()
                }
                parseOfficialRelease(bytes.toString(Charsets.UTF_8))
            }
            403,429 -> error("GitHub 暂时限制请求，请稍后重试")
            else -> error("GitHub 暂时不可用（$code）")
        }
    } catch(cancelled: CancellationException) { throw cancelled }
    catch(failure: java.io.IOException) { throw IllegalStateException("无法连接 GitHub，请检查网络后重试",failure) }
    finally { connection.disconnect() }
}
