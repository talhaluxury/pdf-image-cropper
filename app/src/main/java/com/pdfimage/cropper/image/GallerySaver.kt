package com.pdfimage.cropper.image

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.pdfimage.cropper.crop.ImageFormat
import java.io.File
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream

data class SavedFile(val uri: Uri, val name: String, val bytes: Long)

private class CountingOutputStream(out: OutputStream) : FilterOutputStream(out) {
    var count = 0L
    override fun write(b: Int) { out.write(b); count++ }
    override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len); count += len }
}

/** Saves bitmaps into Pictures/<folder>/ through MediaStore (scoped storage on Android 10+). */
class GallerySaver(context: Context, private val folder: String) {
    private val resolver = context.applicationContext.contentResolver

    fun save(bitmap: Bitmap, displayName: String, format: ImageFormat, quality: Int): SavedFile =
        if (Build.VERSION.SDK_INT >= 29) saveScoped(bitmap, displayName, format, quality)
        else saveLegacy(bitmap, displayName, format, quality)

    private fun encode(bitmap: Bitmap, format: ImageFormat, quality: Int, out: OutputStream): Long {
        val counting = CountingOutputStream(out)
        val fmt = if (format == ImageFormat.JPG) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG
        val ok = bitmap.compress(fmt, if (format == ImageFormat.JPG) quality else 100, counting)
        counting.flush()
        if (!ok) throw IOException("Image encoding failed")
        return counting.count
    }

    private fun saveScoped(bitmap: Bitmap, name: String, format: ImageFormat, quality: Int): SavedFile {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, format.mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$folder")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        // MediaStore picks a unique name itself if one already exists, so nothing is overwritten.
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Could not create the image entry")
        try {
            val out = resolver.openOutputStream(uri) ?: throw IOException("Could not open the output stream")
            val bytes = out.use { encode(bitmap, format, quality, it) }
            val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            return SavedFile(uri, name, bytes)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }   // never leave a half-written file behind
            throw e
        }
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(bitmap: Bitmap, name: String, format: ImageFormat, quality: Int): SavedFile {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), folder)
        if (!dir.exists() && !dir.mkdirs()) throw IOException("Could not create the output folder")
        val base = name.substringBeforeLast('.')
        var file = File(dir, name)
        var n = 1
        while (file.exists()) { file = File(dir, "${base}_$n.${format.ext}"); n++ }
        val bytes = try {
            FileOutputStream(file).use { encode(bitmap, format, quality, it) }
        } catch (e: Exception) {
            file.delete(); throw e
        }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Images.Media.MIME_TYPE, format.mime)
            put(MediaStore.Images.Media.DATA, file.absolutePath)
            put(MediaStore.Images.Media.SIZE, bytes)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Could not register the image in the gallery")
        return SavedFile(uri, file.name, bytes)
    }
}
