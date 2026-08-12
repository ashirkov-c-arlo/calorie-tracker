@file:Suppress("ExifInterface")

package app.kcal.core.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/** A capture target handed to the camera app, kept together with the file it will write. */
data class TransientCapture(val uri: Uri, val path: String)

/**
 * Meal photos exist only as cache files, only while one entry flow needs them. Nothing here
 * ever reaches Room or a meal record: the flow uploads the re-encoded JPEG and the file is
 * deleted after a final success, when the flow ends, and when a later flow starts on leftovers
 * from a crash.
 *
 * Re-encoding is also what strips metadata: the output is written from decoded pixels, so no
 * EXIF block, orientation tag, or GPS tag from the original survives.
 *
 * The orientation tag is read with the platform `ExifInterface`. Lint prefers
 * `androidx.exifinterface`, whose advantage is a backport for platform versions below the
 * rewritten Java implementation of API 24, while this app starts at API 26. Adding the library
 * for one attribute needs approval, so the platform class is used and the check is suppressed
 * for this file only.
 */
@Singleton
class TransientPhotoStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) {

    /**
     * Bumped by every [clear], so an encoding that was already running when the flow closed
     * knows that its output is no longer wanted.
     */
    private val generation = AtomicInteger()

    /**
     * Destination for `TakePicture`. Only the content URI is built here; the file itself is
     * created by the camera app through `FileProvider`, so this stays cheap enough to call from
     * a click handler. The caller keeps the value to [discard] it when the capture does not
     * arrive.
     */
    fun newCapture(): TransientCapture {
        val file = newFile()
        return TransientCapture(
            uri = FileProvider.getUriForFile(context, "${context.packageName}$AUTHORITY_SUFFIX", file),
            path = file.path,
        )
    }

    /** Removes a capture target the camera app may or may not have written to. */
    fun discard(capture: TransientCapture) {
        File(capture.path).delete()
    }

    /**
     * Path of a freshly encoded upload candidate, or null when the source cannot be decoded or
     * the flow was closed while it was encoding. Only that one file survives the call, so a
     * cancelled capture, a replaced photo, and stale leftovers are all gone by the time a
     * request can use them.
     */
    suspend fun prepareForUpload(source: Uri): String? {
        val ownedGeneration = generation.get()
        return withContext(dispatchers.io) {
            val output = newFile()
            val encoded =
                try {
                    encode(source, output)
                } catch (unreadable: IOException) {
                    false
                } catch (revoked: SecurityException) {
                    // A picker URI can lose its grant before it is read; treated as unreadable.
                    false
                }
            // A flow that closed mid-encoding must not end up owning a file afterwards.
            if (!encoded || generation.get() != ownedGeneration) {
                output.delete()
                return@withContext null
            }
            keepOnly(output)
            output.path
        }
    }

    /** Deletes every temporary photo, including one that is still being encoded. */
    fun clear() {
        generation.incrementAndGet()
        keepOnly(null)
    }

    private fun encode(source: Uri, output: File): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        decode(source, bounds)
        val longEdge = max(bounds.outWidth, bounds.outHeight)
        // Zero bounds mean the source is not a decodable image, whatever its name says.
        if (longEdge <= 0) return false
        // Sampling keeps the decode small; the exact scale below is what enforces the long edge.
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(longEdge) }
        val decoded = decode(source, options) ?: return false
        val orientation = openStream(source)?.use(::orientationTag) ?: ExifInterface.ORIENTATION_NORMAL
        return output.outputStream().use { stream ->
            decoded.uprightAndBounded(orientation).compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
        }
    }

    private fun decode(source: Uri, options: BitmapFactory.Options): Bitmap? =
        openStream(source)?.use { BitmapFactory.decodeStream(it, null, options) }

    private fun openStream(source: Uri): InputStream? = context.contentResolver.openInputStream(source)

    /** Only the orientation tag is read; everything else in the source metadata is dropped. */
    private fun orientationTag(stream: InputStream): Int =
        ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)

    /**
     * Downscales and applies all eight orientations, mirrored ones included: a selfie or an
     * imported image must reach the proxy the way the user saw it.
     */
    private fun Bitmap.uprightAndBounded(orientation: Int): Bitmap {
        val scale = (MAX_EDGE_PX.toFloat() / max(width, height)).coerceAtMost(1f)
        if (scale == 1f && orientation == ExifInterface.ORIENTATION_NORMAL) return this
        val matrix = Matrix().apply {
            postScale(scale, scale)
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)

                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)

                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)

                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    postRotate(90f)
                    postScale(-1f, 1f)
                }

                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)

                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    postRotate(-90f)
                    postScale(-1f, 1f)
                }

                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(-90f)

                else -> Unit
            }
        }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    private fun sampleSizeFor(longEdge: Int): Int {
        var sampleSize = 1
        while (longEdge / (sampleSize * 2) >= MAX_EDGE_PX) sampleSize *= 2
        return sampleSize
    }

    private fun keepOnly(survivor: File?) {
        directory().listFiles()?.forEach { file -> if (file != survivor) file.delete() }
    }

    private fun newFile(): File = File(directory(), "${UUID.randomUUID()}.jpg")

    private fun directory(): File = File(context.cacheDir, DIRECTORY_NAME).apply { mkdirs() }

    private companion object {
        /** Must match the `FileProvider` authority and `xml/file_paths` in the manifest. */
        const val AUTHORITY_SUFFIX = ".photos"
        const val DIRECTORY_NAME = "entry-photos"
        const val MAX_EDGE_PX = 1024
        const val JPEG_QUALITY = 80
    }
}
