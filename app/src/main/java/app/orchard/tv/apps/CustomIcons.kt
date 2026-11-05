package app.orchard.tv.apps

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import app.orchard.tv.wallpaper.Images
import java.io.File
import java.io.InputStream

/**
 * Pictures the owner chose for app tiles, sent from the phone page. Each is cropped to the tile's
 * 16:9 and kept by package name in files/icons; a tile with one draws it instead of the app's own
 * artwork (AppCatalog). Only the owner's own uploads live here, and removing one only ever deletes
 * that app's file.
 */
object CustomIcons {
    /** 1280×720: sharp on a 4K UI's 704 px tile, small enough to decode on every app reload. */
    private const val W = 1280
    private const val H = 720

    /** Bumped on every change, so Home reloads its tiles. */
    var version by mutableIntStateOf(0); private set

    /**
     * Told of every change on the main thread, even while Home's Compose window is stopped (an
     * upload from the phone, PR #997). A plain callback: this file is shared with Lite, which has
     * no coroutines.
     */
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()

    fun onChange(listener: () -> Unit) { listeners += listener }

    fun removeOnChange(listener: () -> Unit) { listeners -= listener }

    private lateinit var dir: File

    fun init(context: Context) {
        if (::dir.isInitialized) return
        dir = File(context.filesDir, "icons").apply { mkdirs() }
    }

    /** By package name; a pinned input's key holds slashes, so it goes by a hash of the key. */
    private fun fileFor(pkg: String) = if (isValidKey(pkg)) File(dir, "$pkg.png") else File(dir, "input-${hash(pkg)}.png")

    private fun hash(key: String): String =
        java.security.MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }

    fun has(pkg: String): Boolean = ::dir.isInitialized && fileFor(pkg).exists()

    /** When the app's picture last changed; 0 without one. Part of the tile cache's key. */
    fun stamp(pkg: String): Long = if (::dir.isInitialized) fileFor(pkg).lastModified() else 0L

    /** The app's picture at a size sufficient for this TV's tile, or null. Off the main thread. */
    fun load(pkg: String): Bitmap? {
        if (!has(pkg)) return null
        // The saved 1280x720 image is useful on a 4K TV, but decoding all its pixels for a
        // 368px tile on the owner's 1080p TCL briefly costs 3.5 MiB per app during a reload.
        // A 640px sample still exceeds the focused tile's drawn width on this TV.
        val sample = if (TileArtFactory.W <= 400) 2 else 1
        return BitmapFactory.decodeFile(fileFor(pkg).path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** Stores [input] as the app's picture. Off the main thread; false if it is not an image. */
    fun save(pkg: String, input: InputStream, cacheDir: File): Boolean {
        val tmp = File.createTempFile("icon", ".bin", cacheDir)
        return try {
            tmp.outputStream().use { input.copyTo(it) }
            val bmp = Images.decodeCover(tmp, W, H) ?: return false
            fileFor(pkg).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bmp.recycle()
            true
        } finally {
            tmp.delete()
        }
    }

    fun clear(pkg: String) {
        fileFor(pkg).delete()
    }

    /** Called on the main thread after a save or clear. */
    fun changed() {
        version++
        for (listener in listeners) listener()
    }

    /** A package name, kept as a plain file name. */
    fun isValidKey(pkg: String): Boolean = Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+$").matches(pkg)
}
