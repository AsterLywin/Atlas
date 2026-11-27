package com.atlas.parsers

import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaParserSource

/**
 * Offline tests for the GraalJS engine behind [AtlasMangaLoaderContext.evaluateJs].
 *
 * No live network: every page and API answer comes from a fake OkHttp
 * interceptor. Script fixtures are modeled on (and where short enough,
 * copied verbatim from) the real parser scripts that call `evaluateJs`,
 * so a green test means that parser's JS path works.
 *
 * One shared context (one engine) serves the whole class, with a distinct
 * fake host per test so the per-origin JS sessions cannot interfere.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AtlasJsEngineTest {

    private val pages = mutableMapOf<String, String>()

    private val fakeClient = OkHttpClient.Builder()
        .addInterceptor(Interceptor { chain ->
            val url = chain.request().url.toString()
            val body = pages[url]
            val code = if (body != null) 200 else 404
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(if (code == 200) "OK" else "Not Found")
                .body((body ?: "").toResponseBody("text/html".toMediaType()))
                .build()
        })
        .build()

    private val context = AtlasMangaLoaderContext(httpClient = fakeClient)

    private fun js(baseUrl: String, script: String, timeout: Long = 10_000L): String? =
        runBlocking { context.evaluateJs(baseUrl, script, timeout) }

    private fun js(script: String): String? =
        runBlocking { context.evaluateJs(script) }

    @Test
    fun `pure JS evaluates on a blank page`() {
        assertEquals("2", js("1+1"))
        assertEquals("ab", js("'a' + 'b'"))
        assertEquals("{\"a\":1,\"b\":[1,2]}", js("JSON.stringify({a:1, b:[1,2]})"))
    }

    @Test
    fun `modern syntax works - optional chaining, classes, template literals`() {
        assertEquals("5,none", js("const o = {a:{b:5}}; (o?.a?.b ?? 'none') + ',' + (o?.x?.y ?? 'none')"))
        assertEquals("7", js("(() => { class A { m() { return 7; } } return new A().m(); })()"))
        assertEquals("v3", js("((x) => `v\${x}`)(3)"))
    }

    @Test
    fun `deobfuscation fixture - atob and string ops like MangaReader`() {
        val script = """
            (() => {
                const s = atob('aGVsbG8gd29ybGQ=');
                return s.split('').reverse().join('');
            })()
        """.trimIndent()
        assertEquals("dlrow olleh", js(script))
    }

    @Test
    fun `TextEncoder polyfill round-trips UTF-8`() {
        assertEquals("2", js("new TextEncoder().encode('hi').length"))
        assertEquals("héllo", js("new TextDecoder().decode(new TextEncoder().encode('héllo'))"))
    }

    @Test
    fun `BatCave-style promise script returns the page HTML`() {
        pages["https://batcave.example/manga/1"] =
            "<html><head><title>BatCave</title></head><body><div class=\"content\">Hello</div></body></html>"
        val script = """
            (() => {
                return new Promise(resolve => {
                    const finish = () => {
                        resolve(document.documentElement ? document.documentElement.outerHTML : "");
                    };
                    if (document.readyState === "complete") {
                        setTimeout(finish, 200);
                    } else {
                        window.addEventListener("load", () => setTimeout(finish, 200), { once: true });
                    }
                    setTimeout(finish, 3000);
                });
            })();
        """.trimIndent()
        val result = js("https://batcave.example/manga/1", script)
        assertNotNull(result)
        assertTrue(result!!.contains("Hello"), "page HTML should come back, got: $result")
        assertTrue(result.contains("BatCave"), "title should come back, got: $result")
    }

    @Test
    fun `ComickFun-style IIFE removes scripts and returns outerHTML`() {
        pages["https://comick.example/search?q=x"] =
            "<html><head><title>Search</title><script>var secret = 1;</script>" +
                "<style>.x{}</style></head><body><div class=\"search-results\">" +
                "<div class=\"comic-card\">One</div><div class=\"manga-card\">Two</div>" +
                "</div></body></html>"
        val script = """
            (() => {
                const title = document.title;
                const hasBlockedTitle = title.includes('access denied') || title.includes('just a moment');
                const searchResults = document.querySelector('.search-results, .comics-grid');
                const comicCards = document.querySelectorAll('[class*="comic"], [class*="manga"]');
                if (hasBlockedTitle) {
                    return "CLOUDFLARE_BLOCKED";
                }
                if (searchResults && comicCards.length > 0) {
                    window.stop();
                    const elementsToRemove = document.querySelectorAll('script, iframe, object, embed, style');
                    elementsToRemove.forEach(el => el.remove());
                    return document.documentElement.outerHTML;
                }
                return null;
            })();
        """.trimIndent()
        val result = js("https://comick.example/search?q=x", script)
        assertNotNull(result)
        assertTrue(result!!.contains("One") && result.contains("Two"), "cards should come back, got: $result")
        assertTrue(!result.contains("secret"), "removed scripts must not come back, got: $result")
    }

    @Test
    fun `LikeManga-style querySelectorAll spread with atob`() {
        pages["https://likemanga-test.example/ch-1"] =
            "<html><body><div id=\"chapter-content\"><p>" +
                "<img data-src=\"https://cdn.example/p1.jpg\">" +
                "<canvas data-enc=\"aGVsbG8=\"></canvas>" +
                "</p></div></body></html>"
        // Verbatim READER_IMAGES_SCRIPT from LikeMangaParser.
        val script = """
            (() => {
                const images = [...document.querySelectorAll('#chapter-content img, #chapter-content canvas[data-enc]')]
                    .map((el) => el.tagName === 'CANVAS'
                        ? [...atob(el.getAttribute('data-enc'))].reverse().join('')
                        : el.getAttribute('data-original-src') || el.getAttribute('data-src') || el.getAttribute('src'))
                    .filter((src) => src && !src.startsWith('data:'));
                return images.length > 0 ? JSON.stringify(images) : null;
            })()
        """.trimIndent()
        assertEquals("[\"https://cdn.example/p1.jpg\",\"olleh\"]", js("https://likemanga-test.example/ch-1", script))
    }

    @Test
    fun `YomuMangas-style setInterval polling resolves`() {
        pages["https://yomu.example/ch-1"] =
            "<html><body><ul class=\"abc styles_Pages\">" +
                "<li data-type=\"page\"><img src=\"https://cdn.example/p1.jpg\"></li>" +
                "</ul></body></html>"
        // Verbatim polling script from YomuMangas.loadChapterHtmlViaJs.
        val script = """
            (() => {
                return new Promise(resolve => {
                    const challengeDetected = () => {
                        const hasChallengeScript = document.querySelector('script[src*="challenge-platform"]') !== null;
                        const hasChallengeTitle = document.getElementById('challenge-error-title') !== null;
                        const hasChallengeText = document.getElementById('challenge-error-text') !== null;
                        const hasChallengeForm = document.querySelector('form[action*="__cf_chl"]') !== null;
                        const root = document.documentElement;
                        const lower = ((root && root.innerText) || '').toLowerCase();
                        const hasChallengeTextSignal =
                            (lower.includes('just a moment') && lower.includes('cloudflare')) ||
                            (lower.includes('checking your browser') && lower.includes('cloudflare')) ||
                            lower.includes('cf-chl-opt');
                        return hasChallengeScript || hasChallengeTitle || hasChallengeText || hasChallengeForm || hasChallengeTextSignal;
                    };

                    const hasPages = () =>
                        document.querySelector('ul[class*=styles_Pages] li[data-type="page"] img[src], li[id^="page-"] img[src], [class*=reader_Pages] img[src]') !== null;

                    const finish = () => resolve(document.documentElement ? document.documentElement.outerHTML : "");

                    if (challengeDetected() || hasPages()) {
                        return finish();
                    }

                    let attempts = 0;
                    const maxAttempts = 80;
                    const timer = setInterval(() => {
                        attempts += 1;
                        if (challengeDetected() || hasPages() || attempts >= maxAttempts) {
                            clearInterval(timer);
                            finish();
                        }
                    }, 250);
                });
            })()
        """.trimIndent()
        val result = js("https://yomu.example/ch-1", script)
        assertNotNull(result)
        assertTrue(result!!.contains("p1.jpg"), "page image should come back, got: $result")
    }

    @Test
    fun `setInterval polling with a counter resolves like the fallback path`() {
        pages["https://poll.example/"] = "<html><body><div class=\"ready\">yes</div></body></html>"
        val script = """
            (() => {
                return new Promise(resolve => {
                    let n = 0;
                    const t = setInterval(() => {
                        n++;
                        if (document.querySelector('.ready') !== null || n >= 5) {
                            clearInterval(t);
                            resolve('n=' + n);
                        }
                    }, 10);
                });
            })()
        """.trimIndent()
        assertEquals("n=1", js("https://poll.example/", script))
    }

    @Test
    fun `ReadComicOnline-style async await sleep resolves`() {
        pages["https://rco.example/ch-1"] = "<html><body>ok</body></html>"
        val script = """
            (async () => {
                await new Promise(resolve => setTimeout(resolve, 100));
                await new Promise(resolve => setTimeout(resolve, 500));
                return 'done';
            })()
        """.trimIndent()
        assertEquals("done", js("https://rco.example/ch-1", script))
    }

    @Test
    fun `window state survives across calls for one origin - Webnex pattern`() {
        pages["https://reader-state.example/read/1"] = "<html><body>ok</body></html>"
        pages["https://other-state.example/read/1"] = "<html><body>ok</body></html>"
        val setScript = "(() => { window.__kotatsuReader = { answer: 'A1' }; return null; })()"
        val getScript = "(() => window.__kotatsuReader ? window.__kotatsuReader.answer : null)()"
        assertNull(js("https://reader-state.example/read/1", setScript))
        assertEquals("A1", js("https://reader-state.example/read/1", getScript))
        assertNull(js("https://other-state.example/read/1", getScript))
    }

    @Test
    fun `document cookie comes from the cookie jar - ComX guard pattern`() {
        val jar = InMemoryCookieJar()
        val url = "https://guard.example/".toHttpUrl()
        jar.saveFromResponse(
            url,
            listOf(Cookie.Builder().name("__guard_trust").value("abc123").domain("guard.example").build()),
        )
        val guardContext = AtlasMangaLoaderContext(httpClient = fakeClient, cookieJar = jar)
        pages["https://guard.example/manga/1"] = "<html><body>ok</body></html>"
        // Verbatim DLE_GUARD_SCRIPT from ComXParser.
        val script = """
            (() => {
                const cookies = document.cookie || "";
                return cookies
                    .split(';')
                    .some(cookie => cookie.trim().startsWith('__guard_trust='))
                    ? cookies
                    : null;
            })();
        """.trimIndent()
        val result = runBlocking { guardContext.evaluateJs("https://guard.example/manga/1", script, 10_000L) }
        assertEquals("__guard_trust=abc123", result)
    }

    @Test
    fun `document cookie assignment lands in the cookie jar`() {
        pages["https://cookieset.example/"] = "<html><body>ok</body></html>"
        val script = "(() => { document.cookie = 'x=1; Path=/'; return document.cookie; })()"
        assertEquals("x=1", js("https://cookieset.example/", script))
    }

    @Test
    fun `localStorage persists across calls - LunarAnime pattern`() {
        pages["https://lunar.example/"] = "<html><body>ok</body></html>"
        // Verbatim EXPORT_KEYS_JS from LunarAnime.
        val exportKeysJs = """
            (function() {
                try {
                    var stored = localStorage.getItem("lunar-device-key-jwk");
                    if (stored) return JSON.parse(stored);
                } catch(e) {}
                return null;
            })();
        """.trimIndent()
        assertNull(js("https://lunar.example/", exportKeysJs))
        assertEquals(
            "v1",
            js("https://lunar.example/", "localStorage.setItem('k', 'v1'); localStorage.getItem('k')"),
        )
        assertEquals("v1", js("https://lunar.example/", "localStorage.getItem('k')"))
        js("https://lunar.example/", "localStorage.setItem('lunar-device-key-jwk', '{\"kty\":\"EC\"}')")
        assertEquals("{\"kty\":\"EC\"}", js("https://lunar.example/", exportKeysJs))
    }

    @Test
    fun `Westmanga-style sync XHR reads a fake API`() {
        pages["https://api.example/robots.txt"] = "<html><head></head><body></body></html>"
        pages["https://api.example/v1/data"] = "{\"ok\":true}"
        // Modeled on BROWSER_FETCH_SCRIPT from WestmangaParser.
        val script = """
            (() => {
                if (location.hostname !== "api.example") return null;
                if (window.__wmResult) return window.__wmResult;
                const request = new XMLHttpRequest();
                let result;
                try {
                    request.open('GET', "https://api.example/v1/data", false);
                    request.setRequestHeader('Accept', 'application/json');
                    request.setRequestHeader('X-WM-Visit', "tok123");
                    request.send();
                    result = { status: request.status, body: request.responseText };
                } catch (e) {
                    result = { status: 0, body: String(e) };
                }
                window.__wmResult = JSON.stringify(result);
                return window.__wmResult;
            })()
        """.trimIndent()
        val first = js("https://api.example/robots.txt", script)
        assertNotNull(first)
        assertTrue(first!!.contains("\"status\":200"), "XHR status should come back, got: $first")
        assertTrue(first.contains("ok"), "XHR body should come back, got: $first")
        assertEquals(first, js("https://api.example/robots.txt", script))
    }

    @Test
    fun `Webnex-style fetch polling returns the answer on the second call`() {
        pages["https://reader.example/read/1"] =
            "<html><body><div class=\"reader-page\"><img src=\"cover.jpg\"></div>" +
                "<script>var pagesUrl\":\"/pages\";</script></body></html>"
        pages["https://reader.example/pages"] = "[\"p1.jpg\",\"p2.jpg\"]"
        // Modeled on READER_PAGES_SCRIPT from Webnex.
        val script = """
            (() => {
                if (!location.pathname.startsWith('/read/')) return null;
                const state = window.__kotatsuReader || (window.__kotatsuReader = { since: Date.now() });
                if (!state.asked && (document.querySelector('.reader-page img') || Date.now() - state.since > 10000)) {
                    state.asked = true;
                    const pagesUrl = document.documentElement.innerHTML.match(/pagesUrl\\?":\\?"([^"\\]+)/);
                    fetch(pagesUrl ? pagesUrl[1] : location.pathname + '/pages')
                        .then((response) => response.text()
                            .then((body) => JSON.stringify({ status: response.status, body: body })))
                        .catch((error) => JSON.stringify({ error: String(error) }))
                        .then((answer) => { state.answer = answer; });
                }
                return state.answer || null;
            })()
        """.trimIndent()
        assertNull(js("https://reader.example/read/1", script))
        val second = js("https://reader.example/read/1", script)
        assertNotNull(second)
        assertTrue(second!!.contains("\"status\":200"), "fetch status should come back, got: $second")
        assertTrue(second.contains("p1.jpg"), "fetch body should come back, got: $second")
    }

    @Test
    fun `script errors return null instead of throwing`() {
        assertNull(js("(() => { throw new Error('kaboom'); })()"))
        assertNull(js("(() => {"))
    }

    @Test
    fun `never-settling promise returns null fast`() {
        pages["https://hang.example/"] = "<html><body>ok</body></html>"
        val started = System.currentTimeMillis()
        val result = js("https://hang.example/", "new Promise(() => {})", timeout = 800L)
        assertNull(result)
        assertTrue(System.currentTimeMillis() - started < 3000L, "must not wait out the whole budget")
    }

    @Test
    fun `infinite loop returns null within budget`() {
        pages["https://loop.example/"] = "<html><body>ok</body></html>"
        val started = System.currentTimeMillis()
        val result = js("https://loop.example/", "while(true){}", timeout = 1500L)
        assertNull(result)
        assertTrue(
            System.currentTimeMillis() - started < 10_000L,
            "a hung script must be cancelled, not hang the caller",
        )
    }

    @Test
    fun `LikeManga getPages resolves through evaluateJs end to end`() {
        pages["https://mgread.io/manga/test/ch-1"] =
            "<html><head><title>Ch 1</title></head><body><div id=\"chapter-content\"><p>" +
                "<img data-src=\"https://cdn.example/p1.jpg\">" +
                "<img data-src=\"https://cdn.example/p2.jpg\">" +
                "</p></div></body></html>"
        val parser = KotatsuParserRegistry().newParser(MangaParserSource.LIKEMANGA, context)
        val chapter = MangaChapter(
            1L,
            "Ch 1",
            1f,
            0,
            "/manga/test/ch-1",
            null,
            0L,
            null,
            MangaParserSource.LIKEMANGA,
        )
        val pages = runBlocking { parser.getPages(chapter) }
        assertEquals(
            listOf("https://cdn.example/p1.jpg", "https://cdn.example/p2.jpg"),
            pages.map { it.url },
        )
    }

    @Test
    fun `missing page returns null instead of throwing`() {
        assertNull(js("https://nowhere.example/missing", "document.documentElement.outerHTML", timeout = 5_000L))
    }
}
