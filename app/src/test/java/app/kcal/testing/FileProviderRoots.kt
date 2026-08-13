package app.kcal.testing

import androidx.core.content.FileProvider

/**
 * `FileProvider` caches the roots it parsed from `xml/file_paths` in a static map, while
 * Robolectric hands every test method a fresh data directory. Without dropping that cache the
 * second test to build a capture URI fails on roots that point at the previous temporary
 * directory. Fails loudly if the field is ever renamed, which is the point.
 */
internal fun resetFileProviderRoots() {
    val cache = FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }
    (cache.get(null) as MutableMap<*, *>).clear()
}
