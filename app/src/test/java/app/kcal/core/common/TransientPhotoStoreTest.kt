package app.kcal.core.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kcal.testing.resetFileProviderRoots
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
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

    @Before
    fun setUp() {
        resetFileProviderRoots()
    }

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
    fun `every exif orientation, mirrored ones included, is applied to the pixels`() = runTest {
        // The source marks its top-left quadrant black and its top-right quadrant grey, so each
        // transform lands the two marks in a different pair of quadrants.
        val expected =
            mapOf(
                ExifInterface.ORIENTATION_NORMAL to (Quadrant.TOP_LEFT to Quadrant.TOP_RIGHT),
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL to (Quadrant.TOP_RIGHT to Quadrant.TOP_LEFT),
                ExifInterface.ORIENTATION_ROTATE_180 to (Quadrant.BOTTOM_RIGHT to Quadrant.BOTTOM_LEFT),
                ExifInterface.ORIENTATION_FLIP_VERTICAL to (Quadrant.BOTTOM_LEFT to Quadrant.BOTTOM_RIGHT),
                ExifInterface.ORIENTATION_TRANSPOSE to (Quadrant.TOP_LEFT to Quadrant.BOTTOM_LEFT),
                ExifInterface.ORIENTATION_ROTATE_90 to (Quadrant.TOP_RIGHT to Quadrant.BOTTOM_RIGHT),
                ExifInterface.ORIENTATION_TRANSVERSE to (Quadrant.BOTTOM_RIGHT to Quadrant.TOP_RIGHT),
                ExifInterface.ORIENTATION_ROTATE_270 to (Quadrant.BOTTOM_LEFT to Quadrant.TOP_LEFT),
            )

        expected.forEach { (orientation, marks) ->
            val output = File(assertNotNull(store.prepareForUpload(markedSourceImage(orientation))))

            assertEquals(marks, marksOf(decoded(output)), "orientation $orientation")
        }
    }

    @Test
    fun `a photo that finishes encoding after the flow closed is discarded`() = runTest {
        val encoding = StandardTestDispatcher(testScheduler)
        val store = TransientPhotoStore(context, DispatcherProvider(encoding))
        // UNDISPATCHED runs up to the first suspension, so the call is mid-flight when the flow
        // closes and the encoding only finishes afterwards.
        val pending = async(start = CoroutineStart.UNDISPATCHED) { store.prepareForUpload(sourceImage(600, 600)) }

        store.clear()
        runCurrent()

        assertNull(pending.await())
        assertTrue(photoDirectory.listFiles().orEmpty().isEmpty())
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
    fun `several items keep their own candidate and discard removes just one of them`() = runTest {
        val first = assertNotNull(store.prepareForUpload(sourceImage(width = 600, height = 600)))

        val second = assertNotNull(store.prepareForUpload(sourceImage(width = 600, height = 600)))

        assertTrue(File(first).exists())
        assertTrue(File(second).exists())

        store.discard(first)

        assertFalse(File(first).exists())
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
    fun `a capture target is served by the app file provider and can be discarded`() {
        val capture = store.newCapture()
        val written = File(capture.path).apply { writeText("camera output") }

        assertEquals("content", capture.uri.scheme)
        assertEquals("${context.packageName}.photos", capture.uri.authority)
        assertTrue(capture.path.endsWith(".jpg"), capture.path)

        store.discard(capture.path)

        assertFalse(written.exists())
    }

    private enum class Quadrant { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    /** Which quadrant holds the black mark and which holds the grey one. */
    private fun marksOf(bitmap: Bitmap): Pair<Quadrant, Quadrant> {
        val samples =
            Quadrant.entries.associateWith { quadrant ->
                val x = bitmap.width / 4 + if (quadrant == Quadrant.TOP_RIGHT || quadrant == Quadrant.BOTTOM_RIGHT) {
                    bitmap.width / 2
                } else {
                    0
                }
                val y = bitmap.height / 4 + if (quadrant == Quadrant.BOTTOM_LEFT || quadrant == Quadrant.BOTTOM_RIGHT) {
                    bitmap.height / 2
                } else {
                    0
                }
                Color.red(bitmap.getPixel(x, y))
            }
        val black = assertNotNull(samples.entries.minByOrNull { it.value }).key
        val grey = assertNotNull(samples.entries.filter { it.key != black }.minByOrNull { it.value }).key
        return black to grey
    }

    private fun markedSourceImage(orientation: Int): Uri {
        val bitmap = Bitmap.createBitmap(MARKED_SIZE, MARKED_SIZE, Bitmap.Config.ARGB_8888)
        val half = MARKED_SIZE / 2f
        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            drawRect(0f, 0f, half, half, Paint().apply { color = Color.BLACK })
            drawRect(half, 0f, MARKED_SIZE.toFloat(), half, Paint().apply { color = Color.GRAY })
        }
        return writeJpeg(bitmap, orientation, emptyMap())
    }

    private fun sourceImage(
        width: Int,
        height: Int,
        orientation: Int? = null,
        tags: Map<String, String> = emptyMap(),
    ): Uri = writeJpeg(Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888), orientation, tags)

    private fun writeJpeg(bitmap: Bitmap, orientation: Int?, tags: Map<String, String>): Uri {
        val file = File(context.filesDir, "${UUID.randomUUID()}.jpg")
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

    private companion object {
        const val MARKED_SIZE = 400
    }
}
