package com.performance.tracker.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import android.widget.ImageView
import com.performance.tracker.R
import java.io.ByteArrayOutputStream
import java.io.InputStream

object ImageUtils {

    /**
     * Converts a content Uri to a scaled, compressed Base64 string for lightweight Firestore storage.
     */
    fun uriToCompressedBase64(context: Context, uri: Uri, maxDimension: Int = 300, quality: Int = 80): String? {
        return try {
            val contentResolver = context.contentResolver

            // 1. Check bounds and orientation
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            var inSampleSize = 1
            if (options.outHeight > maxDimension || options.outWidth > maxDimension) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while ((halfHeight / inSampleSize) >= maxDimension && (halfWidth / inSampleSize) >= maxDimension) {
                    inSampleSize *= 2
                }
            }

            // 2. Decode bitmap with inSampleSize
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            var bitmap: Bitmap? = contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null

            // 3. Handle EXIF rotation
            try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                    val rotationDegrees = when (orientation) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                    if (rotationDegrees != 0f) {
                        val matrix = Matrix().apply { postRotate(rotationDegrees) }
                        val rotated = Bitmap.createBitmap(bitmap!!, 0, 0, bitmap!!.width, bitmap!!.height, matrix, true)
                        if (rotated != bitmap) {
                            bitmap?.recycle()
                            bitmap = rotated
                        }
                    }
                }
            } catch (_: Exception) {}

            // 4. Scale down if still larger than maxDimension
            val srcWidth = bitmap!!.width
            val srcHeight = bitmap!!.height
            if (srcWidth > maxDimension || srcHeight > maxDimension) {
                val ratio = minOf(maxDimension.toFloat() / srcWidth, maxDimension.toFloat() / srcHeight)
                val targetW = (srcWidth * ratio).toInt().coerceAtLeast(1)
                val targetH = (srcHeight * ratio).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(bitmap!!, targetW, targetH, true)
                if (scaled != bitmap) {
                    bitmap?.recycle()
                    bitmap = scaled
                }
            }

            // 5. Compress to JPEG and Base64
            val outputStream = ByteArrayOutputStream()
            bitmap!!.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            val bytes = outputStream.toByteArray()
            bitmap?.recycle()

            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Decodes a Base64 string to a Bitmap.
     */
    fun base64ToBitmap(base64Str: String?): Bitmap? {
        if (base64Str.isNullOrBlank()) return null
        return try {
            val decodedBytes = Base64.decode(base64Str, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Loads a profile image into an ImageView, or falls back to a vector icon.
     */
    fun loadProfileImage(base64Str: String?, imageView: ImageView, defaultIconRes: Int = R.drawable.ic_person) {
        val bitmap = base64ToBitmap(base64Str)
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap)
            imageView.imageTintList = null
            imageView.setPadding(0, 0, 0, 0)
            imageView.scaleType = ImageView.ScaleType.CENTER_CROP
        } else {
            imageView.setImageResource(defaultIconRes)
            imageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
    }
}
