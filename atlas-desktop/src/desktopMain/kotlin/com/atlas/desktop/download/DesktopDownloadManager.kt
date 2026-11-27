package com.atlas.desktop.download

import com.atlas.desktop.buildParserClient
import com.atlas.parsers.AtlasMangaLoaderContext
import com.atlas.parsers.KotatsuParserRegistry
import com.atlas.ui.data.AtlasRepository
import com.atlas.ui.model.UiBook
import com.atlas.ui.model.UiChapter
import com.atlas.ui.state.DownloadManager
import com.atlas.ui.state.UiDownloadState
import java.io.File
import java.io.IOException
import java.util.ArrayDeque
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Chapter downloads on desktop: pages come from [AtlasRepository.loadPages]
 * (the source's own page list), each page is fetched with the parser's
 * own HTTP client ([buildParserClient] — same interceptor chain, cookie
 * jar and headers as the covers), and stored under [downloadsDir]:
 *
 *   `<data>/downloads/<base64url(book id)>/<base64url(chapter id)>/001.jpg`
 *
 * A `.complete` marker file in the chapter dir means the chapter
 * finished; a chapter without it is not downloaded, so a crash
 * mid-download never leaves a half chapter behind — the next launch
 * reports it as not downloaded. Progress is reported per finished
 * page; cancel and failure delete the chapter dir.
 *
 * One download runs at a time; the rest wait in a FIFO queue.
 * The core [com.atlas.core.download.DownloadState] is mapped onto the
 * UI's [UiDownloadState] here, at the platform seam.
 */
class DesktopDownloadManager(
    private val loaderContext: AtlasMangaLoaderContext,
    private val registry: KotatsuParserRegistry,
    private val repository: AtlasRepository,
    private val downloadsDir: File,
) : DownloadManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val states = ConcurrentHashMap<String, MutableStateFlow<UiDownloadState>>()

    private val mutex = Mutex()
    private val pending = ArrayDeque<DownloadRequest>()
    private var activeRequest: DownloadRequest? = null
    private var activeJob: Job? = null

    override fun state(book: UiBook, chapter: UiChapter): StateFlow<UiDownloadState> =
        flowFor(book, chapter)

    override fun download(book: UiBook, chapter: UiChapter) {
        scope.launch {
            var queued = false
            mutex.withLock {
                val flow = flowFor(book, chapter)
                when (flow.value) {
                    is UiDownloadState.Downloaded,
                    is UiDownloadState.Downloading,
                    is UiDownloadState.Queued,
                    -> Unit
                    else -> {
                        flow.value = UiDownloadState.Queued
                        pending.addLast(DownloadRequest(book, chapter))
                        queued = true
                    }
                }
            }
            if (queued) schedulePump()
        }
    }

    override fun cancel(book: UiBook, chapter: UiChapter) {
        scope.launch {
            val jobToCancel = mutex.withLock {
                pending.removeIf { it.chapter.id == chapter.id }
                val flow = flowFor(book, chapter)
                if (activeRequest?.chapter?.id == chapter.id) {
                    activeJob
                } else {
                    if (flow.value is UiDownloadState.Queued) {
                        flow.value = UiDownloadState.NotDownloaded
                    }
                    null
                }
            }
            jobToCancel?.cancel()
        }
    }

    /** The chapter's directory on disk (public for the offline reader). */
    fun downloadedDir(book: UiBook, chapter: UiChapter): File =
        chapterDir(book.id, chapter.id)

    /**
     * Local page locations of a fully downloaded chapter, in reading
     * order — `file:` URIs of the stored page files — or null when
     * the chapter is not fully downloaded (no `.complete` marker) or
     * the chapter dir holds no pages. Files are named `001.jpg`,
     * `002.png`, …, so sorting by name is reading order even when
     * pages have mixed extensions. Verified against the real Coil
     * loader: a `file://` URI string loads on the JVM exactly like a
     * remote URL does, so the reader needs no model change.
     */
    override fun localPages(book: UiBook, chapter: UiChapter): List<String>? {
        val dir = chapterDir(book.id, chapter.id)
        if (!dir.resolve(COMPLETE_MARKER).exists()) return null
        val files = dir.listFiles { file ->
            file.isFile && file.name != COMPLETE_MARKER
        }?.sortedBy { it.name }.orEmpty()
        if (files.isEmpty()) return null
        return files.map { it.toURI().toString() }
    }

    /** Stops the worker; in-flight downloads are cancelled and cleaned up. */
    fun close() {
        scope.cancel()
    }

    private fun flowFor(book: UiBook, chapter: UiChapter): MutableStateFlow<UiDownloadState> =
        states.getOrPut(chapter.id) {
            val downloaded =
                chapterDir(book.id, chapter.id).resolve(COMPLETE_MARKER).exists()
            MutableStateFlow(
                if (downloaded) UiDownloadState.Downloaded else UiDownloadState.NotDownloaded,
            )
        }

    private fun chapterDir(bookId: String, chapterId: String): File =
        downloadsDir.resolve(dirName(bookId)).resolve(dirName(chapterId))

    private fun dirName(id: String): String =
        Base64.getUrlEncoder().withoutPadding()
            .encodeToString(id.toByteArray(Charsets.UTF_8))

    private fun schedulePump() {
        scope.launch { pump() }
    }

    private suspend fun pump() {
        mutex.withLock {
            if (activeJob != null) return
            val next = pending.pollFirst() ?: return
            activeRequest = next
            activeJob = scope.launch {
                try {
                    runDownload(next)
                } finally {
                    mutex.withLock {
                        activeJob = null
                        activeRequest = null
                    }
                    schedulePump()
                }
            }
        }
    }

    private suspend fun runDownload(request: DownloadRequest) {
        val (book, chapter) = request
        val flow = flowFor(book, chapter)
        val dir = chapterDir(book.id, chapter.id)
        try {
            flow.value = UiDownloadState.Downloading(0f)
            val pages = withContext(Dispatchers.IO) { repository.loadPages(chapter.id) }
            if (pages.isEmpty()) throw IOException("No pages for chapter ${chapter.id}")
            val client = clientFor(book.sourceId)
            dir.mkdirs()
            pages.forEachIndexed { index, url ->
                coroutineContext.ensureActive()
                downloadPage(client, url, dir, index)
                flow.value = UiDownloadState.Downloading((index + 1f) / pages.size)
            }
            dir.resolve(COMPLETE_MARKER).createNewFile()
            flow.value = UiDownloadState.Downloaded
        } catch (e: CancellationException) {
            dir.deleteRecursively()
            flow.value = UiDownloadState.NotDownloaded
            throw e
        } catch (e: Exception) {
            // A cancelled OkHttp call throws IOException ("Canceled"),
            // not CancellationException — still a cancel, not a failure.
            dir.deleteRecursively()
            if (!coroutineContext.isActive) {
                flow.value = UiDownloadState.NotDownloaded
            } else {
                flow.value = UiDownloadState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private fun clientFor(sourceId: String): OkHttpClient {
        val parser = sourceId.removePrefix("kotatsu:").let { name ->
            registry.findSource(name)?.let { registry.newParser(it, loaderContext) }
        }
        return buildParserClient(loaderContext, parser)
    }

    private suspend fun downloadPage(client: OkHttpClient, url: String, dir: File, index: Int) {
        val call = client.newCall(Request.Builder().url(url).build())
        val response = call.executeSuspend()
        response.use {
            if (!it.isSuccessful) throw IOException("HTTP ${it.code} for $url")
            val file = dir.resolve(pageFileName(index, url, it.header("Content-Type")))
            val tmp = dir.resolve("${file.name}.part")
            it.body.byteStream().use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
            if (!tmp.renameTo(file)) throw IOException("Could not store $url")
        }
    }

    private fun pageFileName(index: Int, url: String, contentType: String?): String {
        val ext = extensionFromUrl(url)
            ?: extensionFromContentType(contentType)
            ?: "bin"
        return "%03d.%s".format(index + 1, ext)
    }

    private fun extensionFromUrl(url: String): String? {
        val path = url.substringBefore('?').substringBefore('#')
        val ext = path.substringAfterLast('.', "").lowercase()
        return if (ext.length in 2..5 && ext.all { it.isLetterOrDigit() } && ext in KNOWN_IMAGE_EXTENSIONS) {
            ext
        } else {
            null
        }
    }

    private fun extensionFromContentType(contentType: String?): String? =
        when (contentType?.substringBefore(';')?.trim()?.lowercase()) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "image/avif" -> "avif"
            else -> null
        }

    private data class DownloadRequest(val book: UiBook, val chapter: UiChapter)

    companion object {
        /** Present in a chapter dir only when every page landed. */
        const val COMPLETE_MARKER = ".complete"

        private val KNOWN_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "avif")
    }
}

/** Blocking OkHttp execute that honours coroutine cancellation (cancels the call). */
private suspend fun Call.executeSuspend(): Response =
    suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cancel() }
        try {
            cont.resume(execute())
        } catch (e: Exception) {
            if (!cont.isCompleted) cont.resumeWithException(e)
        }
    }
