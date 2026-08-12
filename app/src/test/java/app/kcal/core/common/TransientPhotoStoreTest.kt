package app.kcal.core.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The transient photo pipeline: downscale, upright, re-encode, and leave nothing behind. No
 * assertion here touches Room, because a photo never gets that far.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class TransientPhotoStoreTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = TransientPhotoStore(context, DispatcherProvider(UnconfinedTestDispatcher()))
    private val photoDirectory = File(context.cacheDir, "entry-photos")

    @Test
    fun `a large photo is downscaled to the contract long edge and re-encoded as jpeg`() = runTest {
        val source = sourceImage(width = 3000, height = 1500)

        val path = assertNotNull(store.prepareForUpload(source))

        val output = File(path)
        assertEquals(1024, decoded(output).width)
        assertEquals(512, decoded(output).height)
        assertTrue(output.readBytes().take(2) == listOf(0xFF.toByte(), 0xD8.toByte()), "output is not JPEG")
    }

    @Test
    fun `a photo that already fits is not upscaled`() = runTest {
        val source = sourceImage(width = 300, height = 200)

        val output = File(assertNotNull(store.prepareForUpload(source)))

        assertEquals(300, decoded(output).width)
        assertEquals(200, decoded(output).height)
    }

    @Test
    fun `a rotated capture is uprighted before upload`() = runTest {
        val source = sourceImage(width = 800, height = 400, orientation = ExifInterface.ORIENTATION_ROTATE_90)

        val output = File(assertNotNull(store.prepareForUpload(source)))

        assertEquals(400, decoded(output).width)
        assertEquals(800, decoded(output).height)
    }

    @Test
    fun `metadata does not survive the re-encoding`() = runTest {
        val source =
            sourceImage(
                width = 1200,
                height = 900,
                orientation = ExifInterface.ORIENTATION_ROTATE_180,
                tags = mapOf(ExifInterface.TAG_MAKE to "Pixel", ExifInterface.TAG_DATETIME to "2026:03:15 10:00:00"),
            )

        val output = File(assertNotNull(store.prepareForUpload(source)))

        val exif = ExifInterface(output.path)
        assertEquals(
            ExifInterface.ORIENTATION_UNDEFINED,
            exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED),
        )
        assertNull(exif.getAttribute(ExifInterface.TAG_MAKE))
        assertNull(exif.getAttribute(ExifInterface.TAG_DATETIME))
    }

    @Test
    fun `an unreadable source is refused and leaves no file behind`() = runTest {
        val missing = Uri.fromFile(File(context.filesDir, "missing.jpg"))
        val notAnImage = File(context.filesDir, "notes.txt").apply { writeText("not a photo") }

        assertNull(store.prepareForUpload(missing))
        assertNull(store.prepareForUpload(Uri.fromFile(notAnImage)))

        assertTrue(photoDirectory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `only the newest upload candidate survives, so a cancelled capture is cleaned up`() = runTest {
        // What a cancelled or crashed capture leaves in the directory: a file nobody uploads.
        val abandonedCapture = File(photoDirectory.apply { mkdirs() }, "abandoned.jpg").apply { writeText("partial") }
        val first = assertNotNull(store.prepareForUpload(sourceImage(width = 600, height = 600)))

        val second = assertNotNull(store.prepareForUpload(sourceImage(width = 600, height = 600)))

        assertFalse(File(first).exists())
        assertFalse(abandonedCapture.exists())
        assertEquals(listOf(File(second).name), photoDirectory.listFiles().orEmpty().map { it.name })
    }

    @Test
    fun `clear removes every temporary photo`() = runTest {
        val path = assertNotNull(store.prepareForUpload(sourceImage(width = 600, height = 600)))

        store.clear()

        assertFalse(File(path).exists())
        assertTrue(photoDirectory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `a capture uri is served by the app file provider from the photo cache`() {
        val uri = store.newCaptureUri()

        assertEquals("content", uri.scheme)
        assertEquals("${context.packageName}.photos", uri.authority)
        assertTrue(uri.path.orEmpty().endsWith(".jpg"), uri.toString())
    }

    private fun sourceImage(
        width: Int,
        height: Int,
        orientation: Int? = null,
        tags: Map<String, String> = emptyMap(),
    ): Uri {
        val file = File(context.filesDir, "${UUID.randomUUID()}.jpg")
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        if (orientation != null || tags.isNotEmpty()) {
            ExifInterface(file.path).apply {
                orientation?.let { setAttribute(ExifInterface.TAG_ORIENTATION, it.toString()) }
                tags.forEach { (tag, value) -> setAttribute(tag, value) }
                saveAttributes()
            }
        }
        return Uri.fromFile(file)
    }

    private fun decoded(file: File): Bitmap = assertNotNull(BitmapFactory.decodeFile(file.path), "not decodable")
}
