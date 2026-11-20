package com.pdfimage.cropper.ui

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pdfimage.cropper.crop.CropGeometry
import com.pdfimage.cropper.crop.CropRegion
import com.pdfimage.cropper.crop.ExportOptions
import com.pdfimage.cropper.crop.NRect
import com.pdfimage.cropper.crop.PdfInfo
import com.pdfimage.cropper.data.AppPrefs
import com.pdfimage.cropper.data.CropJson
import com.pdfimage.cropper.data.RecentPdf
import com.pdfimage.cropper.data.SavedImage
import com.pdfimage.cropper.data.ThemeMode
import com.pdfimage.cropper.image.BatchExporter
import com.pdfimage.cropper.image.GallerySaver
import com.pdfimage.cropper.image.SavedFile
import com.pdfimage.cropper.pdf.PdfOpenException
import com.pdfimage.cropper.pdf.PdfSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class Screen { HOME, PAGES, EDITOR, REVIEW, EXPORT, PROGRESS, RESULT, GALLERY, SETTINGS, HELP }

class EditorPage(val bitmap: Bitmap, val wPts: Int, val hPts: Int)

class ExportSummary(
    val saved: List<SavedFile>,
    val failed: Int,
    val errors: List<String>,
    val bytes: Long,
    val durationMs: Long,
    val cancelled: Boolean
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = AppPrefs(app)

    // ---- navigation / global ----
    var screen by mutableStateOf(Screen.HOME); private set
    var loading by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null)

    // ---- document ----
    var doc by mutableStateOf<PdfInfo?>(null); private set
    private var source: PdfSource? = null
    var crops by mutableStateOf<List<CropRegion>>(emptyList()); private set
    var selectedPages by mutableStateOf<Set<Int>>(emptySet()); private set
    var editorPage by mutableIntStateOf(0); private set
    var activeId by mutableStateOf<String?>(null)
    var canUndo by mutableStateOf(false); private set
    var canRedo by mutableStateOf(false); private set

    private val undoStack = ArrayDeque<List<CropRegion>>()
    private val redoStack = ArrayDeque<List<CropRegion>>()
    private var snapshot: List<CropRegion>? = null

    private val thumbs = object : LruCache<Int, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: Int, value: Bitmap) = value.byteCount
    }

    // ---- settings ----
    var options by mutableStateOf(prefs.defaultOptions())
    var theme by mutableStateOf(prefs.theme); private set
    var haptics by mutableStateOf(prefs.haptics); private set
    var keepSessions by mutableStateOf(prefs.keepSessions); private set
    var folder by mutableStateOf(prefs.folder); private set

    // ---- home data ----
    var recentPdfs by mutableStateOf(prefs.recents()); private set
    var recentOutputs by mutableStateOf(prefs.outputs()); private set
    var totalConverted by mutableIntStateOf(prefs.totalConverted); private set

    // ---- export ----
    var progressDone by mutableIntStateOf(0); private set
    var progressTotal by mutableIntStateOf(0); private set
    var progressName by mutableStateOf(""); private set
    var result by mutableStateOf<ExportSummary?>(null); private set
    private var exportJob: Job? = null

    // ================= navigation =================
    fun navigate(s: Screen) { screen = s }

    fun openEditor(page: Int, cropId: String? = null) {
        editorPage = page.coerceIn(0, ((doc?.pageCount ?: 1) - 1).coerceAtLeast(0))
        activeId = cropId
        screen = Screen.EDITOR
    }

    fun back() {
        when (screen) {
            Screen.HOME, Screen.PROGRESS -> Unit
            Screen.PAGES -> { closeDocument(); screen = Screen.HOME }
            Screen.EDITOR, Screen.REVIEW -> screen = Screen.PAGES
            Screen.EXPORT, Screen.RESULT -> screen = Screen.REVIEW
            else -> screen = Screen.HOME
        }
    }

    fun finishAndHome() { closeDocument(); screen = Screen.HOME }

    // ================= document =================
    private fun displayName(uri: Uri): String = runCatching {
        getApplication<Application>().contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull() ?: "document.pdf"

    fun openPdf(uri: Uri) {
        if (loading) return
        loading = true
        viewModelScope.launch {
            val app = getApplication<Application>()
            try {
                val (src, name) = withContext(Dispatchers.IO) { PdfSource.open(app, uri) to displayName(uri) }
                runCatching {
                    app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                closeDocument()
                source = src
                val info = PdfInfo(uri.toString(), name, src.pageCount)
                doc = info
                val session = CropJson.decode(prefs.loadSession(info.uri))
                crops = session.crops.filter { it.page < src.pageCount }
                selectedPages = session.pages.filter { it < src.pageCount }.toSet()
                prefs.addRecent(RecentPdf(info.uri, name, src.pageCount, System.currentTimeMillis()))
                recentPdfs = prefs.recents()
                screen = Screen.PAGES
            } catch (e: PdfOpenException) {
                message = when (e.kind) {
                    PdfOpenException.Kind.PASSWORD -> "This PDF is password-protected and can't be opened."
                    PdfOpenException.Kind.CORRUPT -> "This PDF looks corrupted or isn't supported."
                    PdfOpenException.Kind.EMPTY -> "This PDF has no pages."
                    PdfOpenException.Kind.UNREADABLE -> "Couldn't read this file. Try importing it again."
                }
                if (e.kind == PdfOpenException.Kind.UNREADABLE) {
                    prefs.removeRecent(uri.toString()); recentPdfs = prefs.recents()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = "Couldn't open this file."
            } finally {
                loading = false
            }
        }
    }

    private fun closeDocument() {
        source?.close(); source = null
        thumbs.evictAll()
        doc = null; crops = emptyList(); selectedPages = emptySet(); activeId = null
        undoStack.clear(); redoStack.clear(); snapshot = null
        updateFlags()
    }

    private fun persist() {
        val d = doc ?: return
        prefs.saveSession(d.uri, CropJson.encode(crops, selectedPages))
    }

    // ================= rendering =================
    suspend fun thumbnail(page: Int): Bitmap? {
        thumbs.get(page)?.let { return it }
        return withContext(Dispatchers.IO) {
            thumbs.get(page) ?: try {
                source?.renderFit(page, 240)?.also { thumbs.put(page, it) }
            } catch (e: Exception) { null } catch (e: OutOfMemoryError) { null }
        }
    }

    suspend fun renderEditorPage(page: Int): EditorPage? = withContext(Dispatchers.IO) {
        val s = source ?: return@withContext null
        try {
            val (w, h) = s.pageSize(page)
            val target = (getApplication<Application>().resources.displayMetrics.widthPixels * 2).coerceIn(1200, 2000)
            EditorPage(s.renderFit(page, target), w, h)
        } catch (e: Exception) {
            message = "Couldn't render page ${page + 1}"; null
        } catch (e: OutOfMemoryError) {
            message = "Not enough memory to render page ${page + 1}"; null
        }
    }

    suspend fun cropPreview(crop: CropRegion, dpi: Int): Bitmap? = withContext(Dispatchers.IO) {
        try { source?.renderRegion(crop.page, crop.rect, dpi) }
        catch (e: Exception) { null } catch (e: OutOfMemoryError) { null }
    }

    // ================= crop editing =================
    fun cropsOnPage(page: Int) = crops.filter { it.page == page }.sortedBy { it.order }
    fun cropById(id: String) = crops.firstOrNull { it.id == id }
    private fun nextOrder() = (crops.maxOfOrNull { it.order } ?: 0L) + 1

    /** Keeps page selection in sync: pages that gain their first crop get selected, pages that lose their last are deselected. */
    private fun replaceCrops(new: List<CropRegion>) {
        val before = crops.mapTo(HashSet()) { it.page }
        val after = new.mapTo(HashSet()) { it.page }
        crops = new
        if (before != after) selectedPages = (selectedPages + (after - before)) - (before - after)
    }

    private fun updateFlags() { canUndo = undoStack.isNotEmpty(); canRedo = redoStack.isNotEmpty() }
    private fun pushUndo(s: List<CropRegion>) {
        undoStack.addLast(s)
        if (undoStack.size > 100) undoStack.removeFirst()
    }
    private fun fixActive() { if (activeId != null && cropById(activeId!!) == null) activeId = null }

    /** Gesture-based edits: beginEdit() -> many liveUpdate() -> commitEdit() produces ONE undo step. */
    fun beginEdit() { snapshot = crops }

    fun newCrop(page: Int, x: Float, y: Float): String {
        val id = UUID.randomUUID().toString()
        replaceCrops(crops + CropRegion(id, page, NRect(x, y, x, y), nextOrder()))
        activeId = id
        return id
    }

    fun liveUpdate(id: String, rect: NRect) {
        replaceCrops(crops.map { if (it.id == id) it.copy(rect = rect) else it })
    }

    fun commitEdit() {
        val snap = snapshot ?: return
        snapshot = null
        val cleaned = crops.filter {
            it.rect.width >= CropGeometry.DISCARD_SIZE && it.rect.height >= CropGeometry.DISCARD_SIZE
        }
        if (cleaned.size != crops.size) { replaceCrops(cleaned); fixActive() }
        if (crops != snap) {
            pushUndo(snap); redoStack.clear(); persist()
        }
        updateFlags()
    }

    private fun applyEdit(new: List<CropRegion>) {
        pushUndo(crops); redoStack.clear()
        replaceCrops(new)
        fixActive(); persist(); updateFlags()
    }

    fun deleteCrop(id: String) = applyEdit(crops.filter { it.id != id })

    fun duplicateCrop(id: String) {
        val c = cropById(id) ?: return
        val copy = c.copy(id = UUID.randomUUID().toString(), rect = CropGeometry.move(c.rect, 0.03f, 0.03f), order = nextOrder())
        applyEdit(crops + copy)
        activeId = copy.id
    }

    fun resetPage(page: Int) { applyEdit(crops.filter { it.page != page }); activeId = null }

    fun toggleCrop(id: String) = applyEdit(crops.map { if (it.id == id) it.copy(selected = !it.selected) else it })
    fun setAllCropsSelected(value: Boolean) = applyEdit(crops.map { it.copy(selected = value) })

    /** Swaps the order of a crop with its neighbour on the same page (changes export numbering). */
    fun moveCrop(id: String, delta: Int) {
        val c = cropById(id) ?: return
        val list = cropsOnPage(c.page)
        val i = list.indexOfFirst { it.id == id }
        val j = i + delta
        if (j !in list.indices) return
        val other = list[j]
        applyEdit(crops.map {
            when (it.id) {
                c.id -> it.copy(order = other.order)
                other.id -> it.copy(order = c.order)
                else -> it
            }
        })
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.addLast(crops)
        replaceCrops(undoStack.removeLast())
        fixActive(); persist(); updateFlags()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.addLast(crops)
        replaceCrops(redoStack.removeLast())
        fixActive(); persist(); updateFlags()
    }

    // ================= page selection =================
    fun togglePage(page: Int) {
        if (page in selectedPages) {
            selectedPages = selectedPages - page
            crops = crops.map { if (it.page == page) it.copy(selected = false) else it }
        } else {
            selectedPages = selectedPages + page
            crops = crops.map { if (it.page == page) it.copy(selected = true) else it }
        }
        persist()
    }

    fun selectAllPages() {
        selectedPages = (0 until (doc?.pageCount ?: 0)).toSet()
        crops = crops.map { it.copy(selected = true) }
        persist()
    }

    fun deselectAllPages() {
        selectedPages = emptySet()
        crops = crops.map { it.copy(selected = false) }
        persist()
    }

    /**
     * What CONVERT ALL will produce: for every selected page, its selected crops - or the whole page
     * when that page has no crops at all. One entry == one output file.
     */
    fun exportItems(): List<CropRegion> = selectedPages.sorted().flatMap { p ->
        val onPage = cropsOnPage(p)
        if (onPage.isEmpty()) listOf(CropRegion("full-$p", p, NRect.FULL, 0L))
        else onPage.filter { it.selected }
    }

    // ================= export =================
    fun startExport() {
        val src = source ?: return
        val items = exportItems()
        if (items.isEmpty()) { message = "Nothing is selected to convert."; return }
        val d = doc
        val saved = ArrayList<SavedFile>()
        val errors = ArrayList<String>()
        var failed = 0
        var bytes = 0L
        progressTotal = items.size; progressDone = 0; progressName = ""
        screen = Screen.PROGRESS
        val t0 = SystemClock.elapsedRealtime()
        val exporter = BatchExporter(GallerySaver(getApplication(), AppPrefs.sanitizeFolder(folder)))
        exportJob = viewModelScope.launch {
            var cancelled = false
            try {
                exporter.run(src, items, options,
                    onStart = { _, _, name -> progressName = name },
                    onItem = { r ->
                        val s = r.saved
                        if (s != null) { saved += s; bytes += s.bytes }
                        else { failed++; errors += "${r.name}: ${r.error}" }
                        progressDone++
                    })
            } catch (e: CancellationException) {
                cancelled = true
            }
            if (saved.isNotEmpty()) {
                prefs.addOutputs(saved.map { SavedImage(it.uri.toString(), it.name) })
                prefs.totalConverted = prefs.totalConverted + saved.size
                totalConverted = prefs.totalConverted
                recentOutputs = prefs.outputs()
            }
            if (!cancelled && failed == 0 && !prefs.keepSessions && d != null) prefs.clearSession(d.uri)
            result = ExportSummary(saved, failed, errors, bytes, SystemClock.elapsedRealtime() - t0, cancelled)
            screen = Screen.RESULT
        }
    }

    fun cancelExport() { exportJob?.cancel() }

    // ================= settings =================
    fun setDefaults(o: ExportOptions) {
        options = o
        prefs.format = o.format; prefs.quality = o.quality; prefs.resolution = o.resolution
    }
    fun updateTheme(t: ThemeMode) { theme = t; prefs.theme = t }
    fun updateHaptics(v: Boolean) { haptics = v; prefs.haptics = v }
    fun updateKeepSessions(v: Boolean) { keepSessions = v; prefs.keepSessions = v }
    fun updateFolder(v: String) { folder = v; prefs.folder = v }

    fun clearTemporaryFiles() {
        thumbs.evictAll()
        runCatching { getApplication<Application>().cacheDir.listFiles()?.forEach { it.deleteRecursively() } }
        message = "Temporary files cleared"
    }

    override fun onCleared() {
        source?.close()
        super.onCleared()
    }
}
