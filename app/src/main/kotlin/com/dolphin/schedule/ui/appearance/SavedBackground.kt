package com.dolphin.schedule.ui.appearance

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import com.dolphin.schedule.domain.AppData
import com.dolphin.schedule.media.LocalImages
import java.security.MessageDigest
import java.util.WeakHashMap
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** 只渲染用户已保存的本机图片；没有图片时使用主题底色。 */
@Composable
internal fun SavedBackground(data: AppData,blurOverride: Int?=null,reduced: Boolean=false,tag: String="saved-background") {
    val url=data.background?.url?.takeIf { data.settings.backgroundEnabled }
    if(url==null)return
    val blur=(blurOverride ?: data.settings.backgroundBlur).coerceIn(0,30)
    val density=LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width=with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val height=with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
        val bitmap by produceState<ImageBitmap?>(null,url,blur,width,height,reduced) {
            if(blur>0)delay(60) // 合并快速拖动，静止时没有帧循环。
            value=withContext(Dispatchers.Default) { BackgroundBitmaps.render(url,blur,width,height,density.density,reduced)?.asImageBitmap() }
        }
        bitmap?.let { image ->
            Image(image,null,Modifier.fillMaxSize().testTag(tag),contentScale=ContentScale.Crop)
            val dark=MaterialTheme.colorScheme.background.luminance()<.3f
            Box(Modifier.fillMaxSize().background(if(dark)Color(0xC40F191B) else Color(0xB8F5F6F7)))
        }
    }
}

/** 不访问 URL / 文件路径；先检查尺寸再降采样，损坏图片降级为主题底色。 */
internal fun decodeSavedBackground(url: String,blur: Int): Bitmap?=BackgroundBitmaps.render(url,blur,384f,384f,1f,false)?.copy(Bitmap.Config.ARGB_8888,false)

private data class BackgroundBitmapKey(val id: String,val edge: Int,val radius: Int=0)
private object BackgroundBitmaps {
    private val identities=WeakHashMap<String,String>()
    private fun identity(url: String)=synchronized(identities) { identities[url] ?: MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }.also { identities[url]=it } }
    private fun cache(size: Int)=object: LruCache<BackgroundBitmapKey,Bitmap>(size) {
        override fun sizeOf(key: BackgroundBitmapKey,value: Bitmap)=max(1,value.allocationByteCount/1024)
        // 不 recycle 被淘汰的图片，Compose 可能仍在绘制；交由生命周期与 GC 回收。
    }
    private val sources=cache(16*1024)
    private val blurred=cache(6*1024)
    fun render(url: String,blur: Int,width: Float,height: Float,density: Float,reduced: Boolean): Bitmap?=runCatching {
        require(url.length<=16*1024*1024)
        val id=identity(url);val sourceKey=BackgroundBitmapKey(id,if(reduced)960 else 1920)
        val source=synchronized(sources) { sources.get(sourceKey) ?: LocalImages.bitmap(url,sourceKey.edge,40_000_000)?.also { sources.put(sourceKey,it) } } ?: return null
        if(blur<=0)return source
        val edge=if(reduced)192 else 384
        val factor=minOf(1f,edge.toFloat()/max(source.width,source.height))
        val w=max(1,(source.width*factor).roundToInt());val h=max(1,(source.height*factor).roundToInt())
        val scale=max(width/w,height/h).coerceAtLeast(.01f)
        val radius=max(1,(blur.coerceIn(0,30)*density/scale).roundToInt()).coerceAtMost(64)
        val key=BackgroundBitmapKey(id,edge,radius)
        synchronized(blurred) {
            blurred.get(key) ?: run {
                val small=Bitmap.createScaledBitmap(source,w,h,true)
                try {
                    val pixels=IntArray(w*h);small.getPixels(pixels,0,w,0,0,w,h)
                    val target=IntArray(pixels.size)
                    repeat(3) { boxBlur(pixels,target,w,h,radius,true);boxBlur(target,pixels,w,h,radius,false) }
                    Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888).also { blurred.put(key,it) }
                } finally { if(small!==source)small.recycle() }
            }
        }
    }.getOrNull()
}

private fun boxBlur(source: IntArray,target: IntArray,width: Int,height: Int,radius: Int,horizontal: Boolean) {
    val major=if(horizontal)height else width;val minor=if(horizontal)width else height
    val divisor=radius*2+1
    for(line in 0 until major) {
        fun pixel(offset: Int): Int=source[if(horizontal)line*width+offset.coerceIn(0,minor-1) else offset.coerceIn(0,minor-1)*width+line]
        var a=0;var r=0;var g=0;var b=0
        fun add(value: Int,sign: Int) { a+=(value ushr 24)*sign;r+=(value ushr 16 and 255)*sign;g+=(value ushr 8 and 255)*sign;b+=(value and 255)*sign }
        for(offset in -radius..radius)add(pixel(offset),1)
        for(offset in 0 until minor) {
            target[if(horizontal)line*width+offset else offset*width+line]=(a/divisor shl 24) or (r/divisor shl 16) or (g/divisor shl 8) or (b/divisor)
            add(pixel(offset-radius),-1);add(pixel(offset+radius+1),1)
        }
    }
}
