package com.atlas.reader.source.kotatsu

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import com.atlas.parsers.AtlasMangaLoaderContext
import com.atlas.parsers.InMemoryCookieJar
import okhttp3.CookieJar
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.koitharu.kotatsu.parsers.bitmap.Rect
import java.io.ByteArrayOutputStream
import android.graphics.Rect as AndroidRect
import org.koitharu.kotatsu.parsers.bitmap.Bitmap as KotatsuBitmap

/**
 * The Android side of the Kotatsu parser contract.
 *
 * The JVM base class ([AtlasMangaLoaderContext]) implements the image hooks
 * with `java.awt`, which does not exist on Android. This subclass supplies
 * the same hooks backed by `android.graphics.Bitmap`, so parsers that
 * descramble images (e.g. AsuraScans' tiled pages) work on the phone.
 *
 * Everything else — HTTP client, cookie jar, per-source config, and the
 * WebView hooks that throw [UnsupportedOperationException] — is
 * inherited unchanged. JavaScript evaluation is the exception: it is
 * disabled here (see [evaluateJs]), because the GraalJS engine behind
 * the JVM implementation is excluded from the Android build.
 */
class AndroidMangaLoaderContext(
    httpClient: OkHttpClient = AtlasMangaLoaderContext.defaultHttpClient(),
    cookieJar: CookieJar = InMemoryCookieJar(),
    defaultUserAgent: String = AtlasMangaLoaderContext.DEFAULT_USER_AGENT,
    configOverrides: Map<String, Map<String, Any?>> = emptyMap(),
) : AtlasMangaLoaderContext(
    httpClient = httpClient,
    cookieJar = cookieJar,
    defaultUserAgent = defaultUserAgent,
    configOverrides = configOverrides,
) {

    /**
     * JavaScript evaluation is unavailable on Android: the JVM base
     * class runs it on GraalJS, and the Graal jars are excluded from
     * the Android build (see app/build.gradle.kts) — they would only
     * bloat the APK, the engine never loads on the phone. Returning
     * null is the same signal the JVM engine gives for "script
     * unavailable", so parsers that need JS fail the same way they
     * would on a fetch failure instead of crashing on a missing class.
     */
    override suspend fun evaluateJs(script: String): String? = null

    override suspend fun evaluateJs(baseUrl: String, script: String, timeout: Long): String? = null

    override fun redrawImageResponse(
        response: Response,
        redraw: (image: KotatsuBitmap) -> KotatsuBitmap,
    ): Response {
        val body = response.body
        val bytes = body.bytes()
        val contentType = body.contentType()
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: return response.newBuilder()
                .body(bytes.toResponseBody(contentType))
                .build()
        val result = redraw(AndroidBitmap(decoded))
        val image = (result as? AndroidBitmap)?.image
            ?: throw IllegalArgumentException("redraw must return an AndroidBitmap on Android")
        val out = ByteArrayOutputStream()
        if (!image.compress(Bitmap.CompressFormat.PNG, 100, out)) {
            return response.newBuilder()
                .body(bytes.toResponseBody(contentType))
                .build()
        }
        return response.newBuilder()
            .body(out.toByteArray().toResponseBody("image/png".toMediaType()))
            .build()
    }

    override fun createBitmap(width: Int, height: Int): KotatsuBitmap =
        AndroidBitmap(Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888))
}

/** A [KotatsuBitmap] backed by [android.graphics.Bitmap]. */
class AndroidBitmap(val image: Bitmap) : KotatsuBitmap {
    override val width: Int get() = image.width
    override val height: Int get() = image.height

    override fun drawBitmap(sourceBitmap: KotatsuBitmap, src: Rect, dst: Rect) {
        val srcImage = (sourceBitmap as? AndroidBitmap)?.image
            ?: throw IllegalArgumentException("source must be an AndroidBitmap on Android")
        Canvas(image).drawBitmap(
            srcImage,
            AndroidRect(src.left, src.top, src.right, src.bottom),
            AndroidRect(dst.left, dst.top, dst.right, dst.bottom),
            null,
        )
    }
}
