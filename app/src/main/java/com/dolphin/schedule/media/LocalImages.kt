package com.dolphin.schedule.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.provider.OpenableColumns
import com.dolphin.schedule.domain.Background
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.roundToInt

/** 系统选图/相机的图片仅在本机压缩；数据 URL 保持完整备份可移植。 */
object LocalImages {
    const val MAX_COVER_BYTES=30*1024*1024
    const val MAX_BACKGROUND_BYTES=10*1024*1024
    fun background(context: Context,uri: Uri): Background {
        require(uri.scheme=="content") { "请选择系统提供的图片" }
        val name=context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { if(it.moveToFirst())it.getString(0) else null }?.take(300) ?: "背景图片"
        return requireNotNull(context.contentResolver.openInputStream(uri)) { "无法读取这张图片" }.use { background(it,name) }
    }
    fun background(input: InputStream,name: String): Background {
        val bytes=readBounded(input,MAX_BACKGROUND_BYTES)
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true };BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
        require(bounds.outMimeType in setOf("image/jpeg","image/png","image/webp")) { "请选择 JPG、PNG 或 WebP 图片" }
        val image=decode(bytes,1920,maxPixels=40_000_000)
        return try {
            val format=if(Build.VERSION.SDK_INT>=30)Bitmap.CompressFormat.WEBP_LOSSY else legacyWebp()
            val output=ByteArrayOutputStream();check(image.compress(format,86,output)) { "图片压缩未完成" }
            var url="data:image/webp;base64,"+Base64.encodeToString(output.toByteArray(),Base64.NO_WRAP)
            if(url.length>3_000_000)url=jpeg(image,82)
            require(url.length<=3_000_000) { "处理后的图片仍过大，请选择较小的图片" }
            val rotated=orientation(bytes) in 5..8
            Background(url,name.take(300).ifBlank { "背景图片" },image.width,image.height,if(rotated)bounds.outHeight else bounds.outWidth,if(rotated)bounds.outWidth else bounds.outHeight)
        } finally { image.recycle() }
    }
    @Suppress("DEPRECATION")
    private fun legacyWebp()=Bitmap.CompressFormat.WEBP
    fun cover(context: Context,uri: Uri): String {
        require(uri.scheme=="content") { "请选择系统相册或相机提供的图片" }
        return requireNotNull(context.contentResolver.openInputStream(uri)) { "无法读取这张图片" }.use(::cover)
    }
    fun cover(input: InputStream): String {
        val bytes=readBounded(input,MAX_COVER_BYTES)
        val image=decode(bytes,1200)
        return try { jpeg(image,82).also { require(it.length<=3_000_000) { "封面仍然过大，请选择较小的图片" } } } finally { image.recycle() }
    }
    fun bitmap(url: String?,maximum: Int=256,maxPixels: Long=80_000_000): Bitmap? = runCatching {
        require(url!=null && url.length<=16*1024*1024 && maximum in 1..1920)
        val comma=url.indexOf(',');require(comma in 1..80)
        require(url.substring(0,comma).lowercase() in setOf("data:image/jpeg;base64","data:image/jpg;base64","data:image/png;base64","data:image/webp;base64"))
        decode(Base64.decode(url.substring(comma+1),Base64.DEFAULT),maximum,maxPixels=maxPixels)
    }.getOrNull()
    fun notificationCover(url: String?): String? {
        val bitmap=bitmap(url,128) ?: return null
        return try { jpeg(bitmap,72).takeIf { it.length<80_000 } } finally { bitmap.recycle() }
    }
    private fun readBounded(input: InputStream,limit: Int): ByteArray {
        val out=ByteArrayOutputStream();val buffer=ByteArray(8192)
        while(true) { val count=input.read(buffer);if(count<0)break;require(out.size()+count<=limit) { "图片超过 ${limit/(1024*1024)} MB，请选择较小的图片" };out.write(buffer,0,count) }
        require(out.size()>0) { "图片为空，请重新选择" };return out.toByteArray()
    }
    internal fun legacyBitmap(bytes: ByteArray,maximum: Int): Bitmap=decode(bytes,maximum,true)
    private fun decode(bytes: ByteArray,maximum: Int,legacy: Boolean=false,maxPixels: Long=80_000_000): Bitmap {
        require(maximum in 1..1920)
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
        require(bounds.outWidth>0 && bounds.outHeight>0 && bounds.outWidth.toLong()*bounds.outHeight<=maxPixels) { "无法读取图片，或图片像素过大，请选择较小的图片" }
        if(Build.VERSION.SDK_INT>=28 && !legacy)return ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder,info,_ ->
            decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
            val scale=minOf(1f,maximum.toFloat()/max(info.size.width,info.size.height))
            decoder.setTargetSize(max(1,(info.size.width*scale).roundToInt()),max(1,(info.size.height*scale).roundToInt()))
        }
        var sample=1;while(max(bounds.outWidth,bounds.outHeight)/sample>maximum*2)sample*=2
        val raw=BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply { inSampleSize=sample;inPreferredConfig=Bitmap.Config.ARGB_8888 }) ?: error("无法读取图片")
        val matrix=Matrix().apply { when(orientation(bytes)) {
            2->setScale(-1f,1f);3->setRotate(180f);4->setScale(1f,-1f)
            5->setValues(floatArrayOf(0f,1f,0f,1f,0f,0f,0f,0f,1f))
            6->setRotate(90f)
            7->setValues(floatArrayOf(0f,-1f,0f,-1f,0f,0f,0f,0f,1f))
            8->setRotate(270f)
        } }
        val oriented=Bitmap.createBitmap(raw,0,0,raw.width,raw.height,matrix,true)
        if(oriented!==raw)raw.recycle()
        val scale=minOf(1f,maximum.toFloat()/max(oriented.width,oriented.height))
        val sized=Bitmap.createScaledBitmap(oriented,max(1,(oriented.width*scale).roundToInt()),max(1,(oriented.height*scale).roundToInt()),true)
        if(sized!==oriented)oriented.recycle();return sized
    }
    private fun orientation(bytes: ByteArray)=runCatching { ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION,1) }.getOrDefault(1)
    private fun jpeg(image: Bitmap,quality: Int): String {
        val opaque=Bitmap.createBitmap(image.width,image.height,Bitmap.Config.ARGB_8888)
        return try {
            Canvas(opaque).apply { drawColor(android.graphics.Color.WHITE);drawBitmap(image,0f,0f,null) }
            val bytes=ByteArrayOutputStream();check(opaque.compress(Bitmap.CompressFormat.JPEG,quality,bytes)) { "图片压缩未完成" }
            "data:image/jpeg;base64,"+Base64.encodeToString(bytes.toByteArray(),Base64.NO_WRAP)
        } finally { opaque.recycle() }
    }
}
