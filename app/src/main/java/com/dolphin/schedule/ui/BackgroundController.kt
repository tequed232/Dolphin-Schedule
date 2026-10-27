package com.dolphin.schedule.ui

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.SavedStateHandle
import com.dolphin.schedule.data.NativeRepository
import com.dolphin.schedule.data.RepositoryState
import com.dolphin.schedule.domain.*
import com.dolphin.schedule.media.LocalImages
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class BackgroundController(private val context: Context,private val repository: NativeRepository,private val scope: CoroutineScope,private val saved: SavedStateHandle,private val message: (String)->Unit) {
    var busy by mutableStateOf(saved.get<String>("backgroundPicker")!=null);private set
    var error by mutableStateOf<String?>(null);private set
    var previewBlur by mutableStateOf<Int?>(null);private set
    private var blurBase: Pair<BackgroundState,Int>?=null
    private fun current()=(repository.state.value as? RepositoryState.Ready)?.snapshot?.data ?: error("外观设置尚未读取完成")
    fun preparePicker(launch: ()->Unit) {
        if(busy)return
        val base=runCatching { backgroundState(current()) }.getOrElse { error=it.message;return }
        operation {
            saved["backgroundPicker"]=withContext(Dispatchers.Default) { backgroundFingerprint(base) }
            try { launch() } catch(failure: Exception) { saved.remove<String>("backgroundPicker");throw IllegalStateException("无法打开相册，请重试",failure) }
        }
    }
    fun selected(uri: Uri?) {
        val token=saved.remove<String>("backgroundPicker") ?: return
        if(uri==null) { busy=false;return }
        // 回调可能在重建后抵达，先等待本地库读取，不额外持久化相册访问权限。
        busy=false
        operation {
            val state=repository.state.first { it !is RepositoryState.Loading }
            require(state is RepositoryState.Ready) { "外观设置读取失败，原背景保留" }
            val image=withContext(Dispatchers.IO) { LocalImages.background(context,uri) }
            repository.update { replaceBackground(it,token,image) }
            previewBlur=null;blurBase=null;message("背景已保存，只留在本机")
        }
    }
    fun enabled(value: Boolean,remove: Boolean=false) {
        if(busy)return
        val base=backgroundState(current())
        operation { repository.update { changeBackground(it,base,value,remove) };previewBlur=null;blurBase=null }
    }
    fun preview(value: Int) {
        if(busy)return
        val data=current();if(data.background==null || !data.settings.backgroundEnabled)return
        if(blurBase==null)blurBase=backgroundState(data) to data.settings.backgroundBlur
        previewBlur=value.coerceIn(0,30)
    }
    fun saveBlur(after: ()->Unit={}) {
        val value=previewBlur ?: return after();val base=blurBase ?: return
        if(busy)return
        operation {
            try { repository.update { changeBackgroundBlur(it,base.first,base.second,value) };after() }
            finally { previewBlur=null;blurBase=null }
        }
    }
    fun clearPreview() { if(!busy) { previewBlur=null;blurBase=null } }
    private fun operation(block: suspend ()->Unit) {
        if(busy)return
        busy=true;error=null
        scope.launch {
            try { block() }
            catch(cancelled: CancellationException) { throw cancelled }
            catch(failure: Exception) { error=failure.message ?: "外观修改未完成，原设置保留";message(error!!) }
            finally { busy=saved.get<String>("backgroundPicker")!=null }
        }
    }
}
