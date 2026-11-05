package app.orchard.tv.apps

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Online 16:9 tiles. Phone-selected custom images take precedence. */
object IconLibrary {
    private const val TAG = "AerialIcons"
    private const val BASE = "https://aerial.qust.me/icons/"
    private const val CHECK_EVERY_MS = 12 * 60 * 60 * 1000L
    private const val RETRY_MS = 10 * 60 * 1000L

    /** Bumped when pictures arrive or the library is turned on or off, so Home redraws its tiles. */
    var version by mutableIntStateOf(0); private set

    private lateinit var dir: File
    private lateinit var prefs: SharedPreferences

    /** Package → path on the site ("p/<package>-<hash>.jpg"), from the last index read. */
    @Volatile private var index: Map<String, String> = emptyMap()
    private val refreshing = Mutex()
    @Volatile private var lastAttempt = 0L
    /** Pictures that failed to download, and when: not tried again on every reload. */
    private val failedAt = HashMap<String, Long>()

    fun init(context: Context) {
        if (::dir.isInitialized) return
        dir = File(context.filesDir, "library").apply { mkdirs() }
        prefs = context.applicationContext.getSharedPreferences("icon_library", Context.MODE_PRIVATE)
        index = parse(File(dir, "index.json").takeIf { it.exists() }?.readText())
    }

    private fun fileFor(pkg: String): File? {
        val path = index[pkg] ?: return null
        return File(dir, path.substringAfterLast('/'))
    }

    /** Whether the online library picture is cached on this TV. */
    fun has(pkg: String, label: String? = null): Boolean = ::dir.isInitialized && fileFor(pkg)?.exists() == true

    /** The library's picture of [pkg] as stored, for the phone page; null without one. */
    fun file(pkg: String): File? = if (::dir.isInitialized) fileFor(pkg)?.takeIf { it.exists() } else null

    /** Whether [pkg]'s tile uses the library: unless the phone page picked the app's own art. */
    fun uses(pkg: String): Boolean = when (pick(pkg)) {
        Pick.Library -> true
        Pick.Own -> false
        null -> true
    }

    enum class Pick { Library, Own }

    /** What the phone page picked for [pkg]; null for the library. */
    fun pick(pkg: String): Pick? = when {
        !::prefs.isInitialized -> null
        pkg in prefs.getStringSet("use_library", emptySet())!! -> Pick.Library
        pkg in prefs.getStringSet("use_own", emptySet())!! -> Pick.Own
        else -> null
    }

    /** From the phone page; the caller calls [changed] on the main thread after. */
    fun choose(pkg: String, pick: Pick?) {
        val library = prefs.getStringSet("use_library", emptySet())!! - pkg
        val own = prefs.getStringSet("use_own", emptySet())!! - pkg
        prefs.edit {
            putStringSet("use_library", if (pick == Pick.Library) library + pkg else library)
            putStringSet("use_own", if (pick == Pick.Own) own + pkg else own)
        }
    }

    /** When the app's picture arrived; 0 without one. Part of the tile cache's key. */
    fun stamp(pkg: String, label: String? = null): Long {
        if (!uses(pkg)) return 0L
        return file(pkg)?.lastModified() ?: 0L
    }

    /** The app's picture, or null. Off the main thread. */
    fun load(pkg: String, label: String? = null): Bitmap? {
        if (!uses(pkg)) return null
        return file(pkg)?.let { BitmapFactory.decodeFile(it.path) }
    }

    /** Icon bytes for the phone page, whether supplied by this APK or fetched later. */
    fun pictureBytes(pkg: String, label: String? = null): Pair<String, ByteArray>? {
        val f = file(pkg) ?: return null
        return runCatching { f.inputStream().use { (if (f.name.endsWith(".png")) "image/png" else "image/jpeg") to it.readBytes() } }.getOrNull()
    }

    /** Called on the main thread when a pick changed or [refresh] brought something new. */
    fun changed() {
        version++
    }

    /**
     * Reads the index when it is due and fetches the pictures of [installed] apps that are not
     * here yet. True when a tile's picture changed. Off the main thread; a failure is only logged
     * and tried again on a later reload. Fetched with the library off too: the phone page shows
     * them, and can pick one for a single app.
     */
    suspend fun refresh(installed: Set<String>): Boolean = withContext(Dispatchers.IO) {
        if (!::dir.isInitialized) return@withContext false
        if (!refreshing.tryLock()) return@withContext false
        try {
            val now = System.currentTimeMillis()
            var changed = false
            if (now - prefs.getLong("checked_at", 0) > CHECK_EVERY_MS && now - lastAttempt > RETRY_MS) {
                lastAttempt = now
                runCatching { read(BASE + "index.json") }
                    .onFailure { Log.w(TAG, "could not read the icon index", it) }
                    .getOrNull()
                    ?.let { text ->
                        val fresh = parse(text)
                        if (fresh.isNotEmpty() || text.contains("\"icons\"")) {
                            File(dir, "index.json").writeText(text)
                            changed = fresh != index
                            index = fresh
                            prefs.edit { putLong("checked_at", now) }
                        }
                    }
            }
            val keep = HashSet<String>()
            for (pkg in installed) {
                val path = index[pkg] ?: continue
                val file = fileFor(pkg) ?: continue
                keep += file.name
                if (file.exists() || now - (failedAt[pkg] ?: 0L) < RETRY_MS) continue
                runCatching { download(BASE + path, file) }
                    .onSuccess { changed = true; failedAt -= pkg }
                    .onFailure { failedAt[pkg] = now; Log.w(TAG, "could not fetch the picture of $pkg", it) }
            }
            // Pictures replaced by newer ones, or of apps no longer installed.
            dir.listFiles()?.forEach { if (it.name != "index.json" && it.name !in keep) it.delete() }
            changed
        } finally {
            refreshing.unlock()
        }
    }

    private fun parse(text: String?): Map<String, String> = runCatching {
        val icons = JSONObject(text ?: return emptyMap()).getJSONObject("icons")
        buildMap { icons.keys().forEach { pkg -> if (CustomIcons.isValidKey(pkg)) put(pkg, icons.getString(pkg)) } }
    }.getOrDefault(emptyMap())

    private fun connect(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 8_000
        readTimeout = 15_000
    }

    private fun read(url: String): String {
        val c = connect(url)
        return try {
            c.inputStream.use { it.readBytes().decodeToString() }
        } finally {
            c.disconnect()
        }
    }

    /** Into a temporary file first, so a broken download never stands as the picture. */
    private fun download(url: String, to: File) {
        val tmp = File(dir, to.name + ".part")
        val c = connect(url)
        try {
            c.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
        } finally {
            c.disconnect()
        }
        val ok = BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeFile(tmp.path, it) }.outWidth > 0
        if (!ok || !tmp.renameTo(to)) {
            tmp.delete()
            error("not a picture")
        }
    }
}
