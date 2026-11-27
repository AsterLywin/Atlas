package com.atlas.parsers

import java.io.IOException
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Engine
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.Source
import org.graalvm.polyglot.Value
import org.graalvm.polyglot.proxy.ProxyArray
import org.graalvm.polyglot.proxy.ProxyExecutable
import org.graalvm.polyglot.proxy.ProxyObject
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Tag

/**
 * The JavaScript engine behind [AtlasMangaLoaderContext.evaluateJs].
 *
 * Engine choice (probed 2026-11-27, all three fetched through the local
 * Maven mirror at 127.0.0.1:18080):
 * - **GraalVM JS 25.6.5.1 — chosen.** Full modern JavaScript (ES2024:
 *   async/await, classes, optional chaining), maintained, pure-Java jars
 *   (no native binary, so the same artifact runs on Windows x64 and
 *   Linux x64), runs on stock JDK 21, license UPL-1.0 + MIT (permissive,
 *   compatible with the project's GPL-3.0). Probe: Promise + setTimeout,
 *   async/await sleep, setInterval polling, rejection, JSON, and
 *   infinite-loop cancellation via `Context.close(true)` all pass.
 *   Cost: ~69 MB of jars (js-language + truffle-api + icu4j + regex)
 *   and interpreter-only speed on a stock JDK (no JVMCI).
 * - **Rhino 1.9.2 — rejected.** Pure Java and small (1.7 MB, MPL-2.0),
 *   but it only implements ES6: it cannot even *parse* async/await,
 *   which the ReadComicOnline and ComixParser scripts need, and the
 *   instruction-observer timeout did not abort a probe infinite loop.
 * - **J2V8 6.3.4 — rejected.** The current main jar and the platform
 *   native jars both 404 on the mirror, and the project is effectively
 *   unmaintained; a native V8 would also need a Windows x64 binary
 *   shipped alongside the Linux one.
 *
 * How it works: [evaluateJs] fetches the page at `baseUrl` with the
 * context HTTP client, parses it with Jsoup, and runs the parser's
 * script in a GraalJS context that exposes a small browser shim —
 * `document`/`window`/`location`/`localStorage`/`navigator`/
 * `performance`, `setTimeout`/`setInterval`, `fetch` and a synchronous
 * `XMLHttpRequest` — backed by the real page HTML, the cookie jar and
 * OkHttp. Scripts that only read the DOM or compute values (BatCave,
 * ComickFun, MangaGeko, Comikuro, ComX, LikeManga, LunarAnime,
 * MangaReader's deobfuscation, ReadComicOnline, Westmanga, YaoiMangaOnline,
 * YomuMangas) work. Scripts that need a live browser do not: page
 * scripts are never executed (so guard/captcha cookies set by page JS
 * never appear), dynamic `import()` of a site's own modules has nothing
 * to load (ComixParser), and navigation/`click()` goes nowhere
 * (AllManga). Those paths return null exactly like a failed load, never
 * throw: callers treat null as "page did not load".
 *
 * Sessions: one GraalJS context is kept per origin, so `window`
 * properties a script stores (`window.__kotatsuReader`, Webnex's polling
 * state) survive across calls for that origin, the way a WebView keeps
 * them. The document is re-fetched on every call. A hung script is
 * cancelled with `Context.close(true)` and its session is dropped, so
 * the next call starts from a fresh context.
 *
 * Results follow the WebView convention the parsers expect, minus the
 * JSON quoting: a JS string comes back as the string itself (parsers
 * that strip quotes treat an unquoted string as a no-op, while the
 * Jsoup/JSONTokener consumers need the raw value), `null`/`undefined`
 * come back as null, numbers/booleans as their literal text, and
 * objects/arrays as `JSON.stringify` of the value.
 */
internal class AtlasJsEngine(
    private val httpClient: OkHttpClient,
    private val cookieJar: CookieJar,
    private val userAgent: String,
) {

    private val engine: Engine by lazy {
        Engine.newBuilder().option("engine.WarnInterpreterOnly", "false").build()
    }

    /** Origin (scheme + host) -> live JS session, capped so contexts cannot pile up. */
    private val sessions = ConcurrentHashMap<String, JsSession>()

    /** Origin -> localStorage contents, kept for the engine's lifetime. */
    private val storages = ConcurrentHashMap<String, MutableMap<String, String>>()

    /** Worker pool for fetch/XHR network calls; never the JS thread. */
    private val networkPool: ExecutorService = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "atlas-js-fetch").apply { isDaemon = true }
    }

    /**
     * Evaluate [script] in the context of the page at [baseUrl].
     * [baseUrl] null means a blank page (the deprecated single-argument
     * overload: pure computation, no DOM). Returns the result string,
     * or null when the page cannot be fetched, the script fails, the
     * promise never settles, or [timeoutMs] runs out. Never throws.
     */
    suspend fun evaluateJs(baseUrl: String?, script: String, timeoutMs: Long): String? {
        val timeout = timeoutMs.coerceIn(1L, MAX_JS_TIMEOUT_MS)
        val origin = baseUrl?.let { originOf(it) } ?: BLANK_ORIGIN
        // One-time warm-up OUTSIDE the call budget (see [warmUp]): the
        // first GraalJS context creation pays the whole Truffle language
        // init (~6s warm, more under load). Charging that to a single
        // call's budget made the first call time out spuriously under
        // load — the Westmanga flake (first test, first call, null).
        warmUp(origin)
        val deadline = System.currentTimeMillis() + timeout
        val html = if (baseUrl == null) {
            ""
        } else {
            fetchPage(baseUrl, timeout) ?: return null
        }
        val remaining = deadline - System.currentTimeMillis()
        if (remaining <= 0L) return null
        val session = getOrCreateSession(origin)
        return runSession(session, origin, html, baseUrl, script, remaining)
    }

    /** Set once the engine's language is warmed up (first context built). */
    private val warmedUp = AtomicBoolean(false)
    private val warmUpMutex = Mutex()

    /**
     * One-time warm-up, deliberately OUTSIDE any call's timeout budget.
     * Builds this origin's session context on its own thread and waits
     * for it to be ready — a completion signal, not a fixed sleep.
     * The first context creation pays the whole Truffle language init
     * (engine + first context + prelude: ~6s warm, more under load);
     * after it, every context costs ~10ms. Best-effort: if the warm-up
     * itself fails or times out, the call proceeds and pays init inside
     * its own budget exactly as before (no worse than today).
     */
    private suspend fun warmUp(origin: String) {
        if (warmedUp.get()) return
        warmUpMutex.withLock {
            if (warmedUp.get()) return
            val session = try {
                getOrCreateSession(origin)
            } catch (e: Exception) {
                return
            }
            val ready = try {
                withContext(Dispatchers.IO) {
                    session.warmUp().get(WARM_UP_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                }
            } catch (e: Exception) {
                false
            }
            if (ready == true) warmedUp.set(true)
        }
    }

    /** Drop every session. Only used by tests; production keeps the engine for the process. */
    fun close() {
        synchronized(sessions) {
            sessions.values.forEach { it.shutdown() }
            sessions.clear()
        }
        networkPool.shutdownNow()
    }

    private fun getOrCreateSession(origin: String): JsSession {
        sessions[origin]?.let { return it }
        synchronized(sessions) {
            sessions[origin]?.let { return it }
            if (sessions.size >= MAX_SESSIONS) {
                sessions.keys.firstOrNull { it != origin }?.let { oldest ->
                    sessions.remove(oldest)?.shutdown()
                }
            }
            return JsSession(origin).also { sessions[origin] = it }
        }
    }

    private fun dropSession(origin: String) {
        synchronized(sessions) {
            sessions.remove(origin)?.shutdown()
        }
    }

    private suspend fun runSession(
        session: JsSession,
        origin: String,
        html: String,
        baseUrl: String?,
        script: String,
        timeoutMs: Long,
    ): String? {
        val future = session.submit(html, baseUrl, script, timeoutMs)
        return try {
            withContext(Dispatchers.IO) {
                future.get(timeoutMs, TimeUnit.MILLISECONDS)
            }
        } catch (e: TimeoutException) {
            // The script itself never returned (infinite loop or a hung
            // native call): kill the whole context so its thread is freed.
            dropSession(origin)
            null
        } catch (e: ExecutionException) {
            null
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            null
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun fetchPage(url: String, timeoutMs: Long): String? =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()
            try {
                httpClient.newBuilder()
                    .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .build()
                    .newCall(request)
                    .execute()
                    .use { response -> response.body.string().takeIf { it.isNotBlank() } }
            } catch (e: IOException) {
                null
            } catch (e: IllegalArgumentException) {
                null
            }
        }

    private fun originOf(url: String): String = try {
        url.toHttpUrl().let { "${it.scheme}://${it.host}" }
    } catch (e: IllegalArgumentException) {
        url
    }

    private fun storageFor(origin: String): MutableMap<String, String> =
        storages.getOrPut(origin) { ConcurrentHashMap() }

    // ------------------------------------------------------------------
    // One browsing context: a single GraalJS Context owned by one thread.
    // ------------------------------------------------------------------

    private inner class JsSession(val origin: String) {
        private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "atlas-js-$origin").apply { isDaemon = true }
        }
        private var context: Context? = null
        private var deferFn: Value? = null
        private val drainSource: Source = Source.newBuilder("js", "void 0", "atlas-drain").build()

        private val document = JsDocument()
        private val location = JsLocation()
        private val window = JsWindow()
        private val storage = JsStorage(storageFor(origin))
        private val navigator = JsNavigator()
        private val performance = JsPerformance()
        private val console = JsConsole()
        private var fetchFn: Value? = null
        private var xhrClass: Value? = null

        private val setTimeoutFn = ProxyExecutable { args -> scheduleTimer(args, repeat = false) }
        private val setIntervalFn = ProxyExecutable { args -> scheduleTimer(args, repeat = true) }
        private val clearTimeoutFn = ProxyExecutable { args -> clearTimer(args) }
        private val clearIntervalFn = ProxyExecutable { args -> clearTimer(args) }
        private val atobFn = ProxyExecutable { args -> atob(args.getOrNull(0)) }
        private val btoaFn = ProxyExecutable { args -> btoa(args.getOrNull(0)) }

        /** Timers live and fire only on this thread; no locking needed. */
        private val timers = mutableListOf<TimerTask>()
        private val nextTimerId = AtomicLong(0)

        /** Host callbacks (fetch/XHR completions) queued from worker threads. */
        private val taskLock = Any()
        private val taskQueue = ArrayDeque<() -> Unit>()

        /** In-flight fetch/XHR calls whose completion task has not arrived yet. */
        private val pendingAsync = AtomicInteger(0)

        @Volatile
        private var budgetDeadlineMs: Long = 0

        @Volatile
        private var currentBaseUrl: String? = null

        fun submit(html: String, baseUrl: String?, script: String, timeoutMs: Long): Future<String?> =
            executor.submit<String?> { runOnce(html, baseUrl, script, timeoutMs) }

        /** Build this session's context and report whether it is ready. */
        fun warmUp(): Future<Boolean> = executor.submit<Boolean> {
            try {
                ensureContext()
                true
            } catch (e: Throwable) {
                false
            }
        }

        fun shutdown() {
            try {
                context?.close(true)
            } catch (e: Throwable) {
                // Closing a poisoned context must never throw.
            }
            executor.shutdownNow()
        }

        fun enqueue(task: () -> Unit) {
            synchronized(taskLock) { taskQueue.addLast(task) }
        }

        fun remainingMs(): Long = budgetDeadlineMs - System.currentTimeMillis()

        private fun runOnce(html: String, baseUrl: String?, script: String, timeoutMs: Long): String? {
            val ctx = try {
                ensureContext()
            } catch (e: Throwable) {
                return null
            }
            // Enter for this call only and always leave: close(true) from
            // another thread waits until no thread is entered, so a context
            // left entered on an idle session thread can never be closed.
            ctx.enter()
            try {
                return try {
                    val deadline = System.currentTimeMillis() + timeoutMs
                    budgetDeadlineMs = deadline
                    currentBaseUrl = baseUrl
                    document.setPage(html, baseUrl)
                    location.setHref(baseUrl)
                    timers.clear()
                    // Settle the previous call's async work first: a fetch the
                    // last script fired may have completed since, and its
                    // `.then` callback is what stores the answer on `window`
                    // (the Webnex polling pattern). Stale timers are dropped
                    // above; queued host tasks are not.
                    settlePendingAsync(ctx, minOf(deadline, System.currentTimeMillis() + ASYNC_GRACE_MS))
                    val result = try {
                        ctx.eval("js", script)
                    } catch (e: Throwable) {
                        return null
                    }
                    pump(ctx, result, deadline)
                } catch (e: Throwable) {
                    null
                }
            } finally {
                try {
                    ctx.leave()
                } catch (e: Throwable) {
                    // The context may already be closed by a concurrent cancel.
                }
            }
        }

        private fun ensureContext(): Context {
            context?.let { return it }
            val ctx = Context.newBuilder("js")
                .engine(engine)
                .allowHostAccess(HostAccess.NONE)
                .option("js.ecmascript-version", "2024")
                .build()
            installBindings(ctx)
            ctx.eval("js", JS_PRELUDE)
            val bindings = ctx.getBindings("js")
            deferFn = bindings.getMember("__atlasDefer")
            fetchFn = bindings.getMember("fetch")
            xhrClass = bindings.getMember("XMLHttpRequest")
            context = ctx
            return ctx
        }

        private fun installBindings(ctx: Context) {
            val bindings = ctx.getBindings("js")
            bindings.putMember("document", document)
            bindings.putMember("window", window)
            bindings.putMember("location", location)
            bindings.putMember("localStorage", storage)
            bindings.putMember("navigator", navigator)
            bindings.putMember("performance", performance)
            bindings.putMember("console", console)
            bindings.putMember("setTimeout", setTimeoutFn)
            bindings.putMember("setInterval", setIntervalFn)
            bindings.putMember("clearTimeout", clearTimeoutFn)
            bindings.putMember("clearInterval", clearIntervalFn)
            bindings.putMember("atob", atobFn)
            bindings.putMember("btoa", btoaFn)
            bindings.putMember("__atlasFetch", ProxyExecutable { args -> hostFetch(args) })
            bindings.putMember("__atlasXhrSync", ProxyExecutable { args -> hostXhrSync(args) })
            bindings.putMember("__atlasXhrAsync", ProxyExecutable { args -> hostXhrAsync(args) })
        }

        // --------------------------------------------------------------
        // Timers + the promise pump. Everything here runs on this thread.
        // --------------------------------------------------------------

        private fun scheduleTimer(args: Array<out Value>, repeat: Boolean): Any? {
            val fn = timerFnArg(args.getOrNull(0)) ?: return null
            val delay = timerDelayArg(args.getOrNull(1))
            val extra = args.drop(2)
            val id = nextTimerId.incrementAndGet()
            timers.add(
                TimerTask(
                    id = id,
                    dueMs = System.currentTimeMillis() + delay,
                    intervalMs = if (repeat) delay else 0L,
                    fn = fn,
                    args = extra,
                ),
            )
            return id
        }

        private fun clearTimer(args: Array<out Value>): Any? {
            val id = args.getOrNull(0)?.takeIf { it.isNumber }?.asLong() ?: return null
            timers.removeAll { it.id == id }
            return null
        }

        /** A timer callback: a JS function, or a string of code (legacy form). */
        private fun timerFnArg(value: Value?): Any? = when {
            value == null || value.isNull -> null
            value.isString -> value.asString()
            value.canExecute() -> value
            else -> null
        }

        private fun timerDelayArg(value: Value?): Long =
            if (value != null && value.isNumber) value.asLong().coerceAtLeast(0L) else 0L

        private fun fireTimer(ctx: Context, timer: TimerTask) {
            when (val fn = timer.fn) {
                is String -> ctx.eval("js", fn)
                is Value -> if (fn.canExecute()) fn.executeVoid(*timer.args.toTypedArray())
            }
        }

        /** Run the microtask queue: a no-op eval is the drain point. */
        private fun drainMicrotasks(ctx: Context) {
            try {
                ctx.eval(drainSource)
            } catch (e: Throwable) {
                // A drain failure means the context is gone; the pump ends below.
            }
        }

        private fun pump(ctx: Context, result: Value?, deadline: Long): String? {
            val promise = asPromise(result) ?: run {
                // A synchronous result does not wait for anything, but a
                // fetch/XHR this script fired may still land: give it a
                // brief grace so its `.then` callback can store the answer
                // on `window` before the next call reads it.
                settlePendingAsync(ctx, minOf(deadline, System.currentTimeMillis() + ASYNC_GRACE_MS))
                return valueToResultString(ctx, result)
            }
            val settled = arrayOfNulls<Value>(1)
            var settledFlag = false
            val onOk = ProxyExecutable { args ->
                settled[0] = args.getOrNull(0)
                settledFlag = true
                null
            }
            val onErr = ProxyExecutable {
                settledFlag = true
                null
            }
            try {
                promise.invokeMember("then", onOk, onErr)
            } catch (e: Throwable) {
                return null
            }
            drainMicrotasks(ctx)
            while (!settledFlag && System.currentTimeMillis() < deadline) {
                var didWork = false
                while (true) {
                    val task = synchronized(taskLock) { taskQueue.removeFirstOrNull() } ?: break
                    try {
                        task()
                    } catch (e: Throwable) {
                        // A host callback failure must not kill the pump.
                    }
                    didWork = true
                    drainMicrotasks(ctx)
                    if (settledFlag) break
                }
                if (settledFlag) break
                val now = System.currentTimeMillis()
                val due = timers.filter { it.dueMs <= now }
                timers.removeAll(due.toSet())
                for (timer in due) {
                    if (timer.intervalMs > 0L) {
                        timers.add(timer.copy(dueMs = System.currentTimeMillis() + timer.intervalMs))
                    }
                    try {
                        fireTimer(ctx, timer)
                    } catch (e: Throwable) {
                        // A throwing timer callback is a script error, not ours.
                    }
                    didWork = true
                    drainMicrotasks(ctx)
                    if (settledFlag) break
                }
                if (settledFlag) break
                if (!didWork) {
                    val nextDue = timers.minOfOrNull { it.dueMs }
                    val sleepMs = when {
                        nextDue != null -> (nextDue - System.currentTimeMillis()).coerceIn(0L, 100L)
                        pendingAsync.get() > 0 -> 20L
                        else -> break
                    }
                    if (sleepMs > 0L) {
                        try {
                            Thread.sleep(sleepMs)
                        } catch (e: InterruptedException) {
                            return null
                        }
                    }
                }
            }
            timers.clear()
            if (!settledFlag) return null
            return valueToResultString(ctx, settled[0])
        }

        /**
         * Run queued host tasks (fetch/XHR completions) until none are
         * in flight or [untilMs] is reached. Lets a previous call's async
         * work store its answer on `window` before the next script reads it.
         */
        private fun settlePendingAsync(ctx: Context, untilMs: Long) {
            // Flush any microtasks left over from the previous call first:
            // a promise it resolved may still have reactions to run.
            drainMicrotasks(ctx)
            while (System.currentTimeMillis() < untilMs) {
                var didWork = false
                while (true) {
                    val task = synchronized(taskLock) { taskQueue.removeFirstOrNull() } ?: break
                    try {
                        task()
                    } catch (e: Throwable) {
                        // A host callback failure must not kill the drain.
                    }
                    didWork = true
                    drainMicrotasks(ctx)
                }
                val queueEmpty = synchronized(taskLock) { taskQueue.isEmpty() }
                if (pendingAsync.get() == 0 && queueEmpty) break
                if (!didWork) {
                    try {
                        Thread.sleep(10)
                    } catch (e: InterruptedException) {
                        return
                    }
                }
            }
        }

        private fun asPromise(value: Value?): Value? {
            if (value == null) return null
            return try {
                val then = value.getMember("then")
                if (then != null && then.canExecute()) value else null
            } catch (e: Throwable) {
                null
            }
        }

        private fun valueToResultString(ctx: Context, value: Value?): String? {
            if (value == null || value.isNull) return null
            if (value.isString) return value.asString()
            if (value.isNumber || value.isBoolean) return value.toString()
            if (value.toString() == "undefined") return null
            return try {
                ctx.getBindings("js").putMember("__atlasResult", value)
                val json = ctx.eval("js", "JSON.stringify(__atlasResult)")
                if (json.isNull || json.toString() == "undefined") null else json.asString()
            } catch (e: Throwable) {
                null
            }
        }

        private fun newDefer(): JsDefer {
            val defer = checkNotNull(deferFn).execute()
            return JsDefer(
                promise = checkNotNull(defer.getMember("promise")),
                resolve = checkNotNull(defer.getMember("resolve")),
                reject = checkNotNull(defer.getMember("reject")),
            )
        }

        // --------------------------------------------------------------
        // Host bridges: fetch and XMLHttpRequest, called from the prelude.
        // --------------------------------------------------------------

        /** fetch(resolve, reject, url, optionsJson) -> the promise is made by the prelude. */
        private fun hostFetch(args: Array<out Value>): Any? {
            val resolve = args.getOrNull(0) ?: return null
            val reject = args.getOrNull(1) ?: return null
            val rawUrl = args.getOrNull(2)?.asStringOrNull().orEmpty()
            val optionsJson = args.getOrNull(3)?.asStringOrNull().orEmpty()
            pendingAsync.incrementAndGet()
            networkPool.execute {
                val outcome = runFetch(rawUrl, optionsJson)
                enqueue {
                    try {
                        if (outcome != null) {
                            resolve.execute(JsFetchResponse(outcome))
                        } else {
                            reject.execute(typeError("Failed to fetch"))
                        }
                    } catch (e: Throwable) {
                        // The context is gone; nothing to resolve into.
                    } finally {
                        pendingAsync.decrementAndGet()
                    }
                }
            }
            return null
        }

        private fun typeError(message: String): Value {
            val ctx = checkNotNull(context)
            return ctx.eval("js", "new TypeError(" + JSONObject.quote(message) + ")")
        }

        private inner class FetchOutcome(
            val status: Int,
            val statusText: String,
            val url: String,
            val body: String,
            val headers: Map<String, String>,
        ) {
            val allHeaders: String
                get() = headers.entries.joinToString("\r\n") { "${it.key}: ${it.value}" }
        }

        private fun runFetch(rawUrl: String, optionsJson: String): FetchOutcome? {
            val url = resolveUrl(rawUrl) ?: return null
            val options = try {
                JSONObject(optionsJson)
            } catch (e: Exception) {
                JSONObject()
            }
            val method = options.optString("method", "GET").ifEmpty { "GET" }
            val bodyText = if (options.has("body")) options.optString("body") else null
            val requestBuilder = Request.Builder().url(url).header("User-Agent", userAgent)
            val headers = options.optJSONObject("headers")
            if (headers != null) {
                for (key in headers.keys()) {
                    requestBuilder.header(key, headers.optString(key))
                }
            }
            val body = if (bodyText != null && methodAllowsBody(method)) {
                bodyText.toRequestBody(null)
            } else {
                null
            }
            requestBuilder.method(method, body)
            return try {
                httpClient.newBuilder()
                    .callTimeout(remainingMs().coerceAtLeast(1L), TimeUnit.MILLISECONDS)
                    .build()
                    .newCall(requestBuilder.build())
                    .execute()
                    .use { response ->
                        FetchOutcome(
                            status = response.code,
                            statusText = response.message,
                            url = response.request.url.toString(),
                            body = response.body.string(),
                            headers = headersMap(response),
                        )
                    }
            } catch (e: IOException) {
                null
            } catch (e: IllegalArgumentException) {
                null
            }
        }

        private fun methodAllowsBody(method: String): Boolean =
            method.equals("POST", ignoreCase = true) ||
                method.equals("PUT", ignoreCase = true) ||
                method.equals("PATCH", ignoreCase = true)

        /** XHR sync: __atlasXhrSync(xhr, method, url, headersJson, body) -> status. */
        private fun hostXhrSync(args: Array<out Value>): Any? {
            val xhr = args.getOrNull(0) ?: return 0
            val outcome = runXhr(args) ?: return 0
            applyXhrOutcome(xhr, outcome)
            return outcome.status
        }

        /** XHR async: __atlasXhrAsync(xhr, method, url, headersJson, body). */
        private fun hostXhrAsync(args: Array<out Value>): Any? {
            val xhr = args.getOrNull(0) ?: return null
            pendingAsync.incrementAndGet()
            networkPool.execute {
                val outcome = runXhr(args)
                enqueue {
                    try {
                        if (outcome != null) {
                            xhr.invokeMember(
                                "__atlasComplete",
                                outcome.status,
                                outcome.statusText,
                                outcome.body,
                                outcome.url,
                                JSONObject(outcome.headers).toString(),
                                outcome.allHeaders,
                            )
                        } else {
                            xhr.invokeMember(
                                "__atlasComplete",
                                0,
                                "",
                                "",
                                "",
                                "{}",
                                "",
                            )
                        }
                    } catch (e: Throwable) {
                        // The context is gone; nothing to complete into.
                    } finally {
                        pendingAsync.decrementAndGet()
                    }
                }
            }
            return null
        }

        private fun runXhr(args: Array<out Value>): FetchOutcome? {
            val method = args.getOrNull(1)?.asStringOrNull() ?: "GET"
            val rawUrl = args.getOrNull(2)?.asStringOrNull().orEmpty()
            val headersJson = args.getOrNull(3)?.asStringOrNull().orEmpty()
            val bodyText = args.getOrNull(4)?.let { if (it.isNull) null else it.asStringOrNull() }
            val url = resolveUrl(rawUrl) ?: return null
            val requestBuilder = Request.Builder().url(url).header("User-Agent", userAgent)
            try {
                val headers = JSONObject(headersJson)
                for (key in headers.keys()) {
                    requestBuilder.header(key, headers.optString(key))
                }
            } catch (e: Exception) {
                // Malformed headers JSON: send without them.
            }
            val body = if (bodyText != null && methodAllowsBody(method)) {
                bodyText.toRequestBody("text/plain".toMediaTypeOrNull())
            } else {
                null
            }
            requestBuilder.method(method, body)
            return try {
                httpClient.newBuilder()
                    .callTimeout(remainingMs().coerceAtLeast(1L), TimeUnit.MILLISECONDS)
                    .build()
                    .newCall(requestBuilder.build())
                    .execute()
                    .use { response ->
                        FetchOutcome(
                            status = response.code,
                            statusText = response.message,
                            url = response.request.url.toString(),
                            body = response.body.string(),
                            headers = headersMap(response),
                        )
                    }
            } catch (e: IOException) {
                null
            } catch (e: IllegalArgumentException) {
                null
            }
        }

        private fun applyXhrOutcome(xhr: Value, outcome: FetchOutcome) {
            try {
                xhr.putMember("status", outcome.status)
                xhr.putMember("statusText", outcome.statusText)
                xhr.putMember("responseText", outcome.body)
                xhr.putMember("responseURL", outcome.url)
                xhr.putMember("_responseHeadersJson", JSONObject(outcome.headers).toString())
                xhr.putMember("_allResponseHeaders", outcome.allHeaders)
            } catch (e: Throwable) {
                // The XHR object is gone; nothing to fill in.
            }
        }

        private fun resolveUrl(rawUrl: String): String? {
            val base = currentBaseUrl?.toHttpUrlOrNull()
            val resolved = base?.resolve(rawUrl) ?: rawUrl.toHttpUrlOrNull()
            return resolved?.toString()
        }

        private fun atob(value: Value?): Any? {
            val input = value?.asStringOrNull() ?: return null
            val bytes = try {
                Base64.getDecoder().decode(input)
            } catch (e: IllegalArgumentException) {
                return null
            }
            return buildString(bytes.size) {
                for (b in bytes) append((b.toInt() and 0xFF).toChar())
            }
        }

        private fun btoa(value: Value?): Any? {
            val input = value?.asStringOrNull() ?: return null
            val bytes = ByteArray(input.length) { (input[it].code and 0xFF).toByte() }
            return Base64.getEncoder().encodeToString(bytes)
        }

        // --------------------------------------------------------------
        // The browser shim: document, window, location, storage, ...
        // --------------------------------------------------------------

        private fun elementsArray(elements: List<Element>): ProxyArray = object : ProxyArray {
            override fun get(index: Long): Any? = JsElement(elements[index.toInt()])
            override fun set(index: Long, value: Value) = throw UnsupportedOperationException()
            override fun getSize(): Long = elements.size.toLong()
        }

        private fun selectFirst(selector: String): Element? = try {
            document.doc.selectFirst(selector)
        } catch (e: Exception) {
            null
        }

        private fun selectAll(selector: String): List<Element> = try {
            document.doc.select(selector)
        } catch (e: Exception) {
            emptyList()
        }

        /** One element of the fetched page. Wraps a Jsoup element. */
        private inner class JsElement(private val element: Element) : ProxyObject {
            private val expando = mutableMapOf<String, Any?>()

            override fun getMember(key: String): Any? {
                when (key) {
                    "outerHTML" -> return element.outerHtml()
                    "innerHTML" -> return element.html()
                    "textContent", "innerText" -> return element.text()
                    "tagName", "nodeName" -> return element.tagName().uppercase()
                    "id" -> return element.id()
                    "className" -> return element.className()
                    "src" -> return element.attr("src")
                    "href" -> return element.attr("href")
                    "parentElement" -> return element.parent()?.let { JsElement(it) }
                    "children" -> return elementsArray(element.children())
                    "firstElementChild" -> return element.firstElementChild()?.let { JsElement(it) }
                    "lastElementChild" -> return element.lastElementChild()?.let { JsElement(it) }
                    "nextElementSibling" -> return element.nextElementSibling()?.let { JsElement(it) }
                    "previousElementSibling" -> return element.previousElementSibling()?.let { JsElement(it) }
                    "dataset" -> return JsDataset(element)
                    "getAttribute" -> return ProxyExecutable { args ->
                        val name = args.getOrNull(0)?.asStringOrNull() ?: return@ProxyExecutable null
                        if (element.hasAttr(name)) element.attr(name) else null
                    }
                    "setAttribute" -> return ProxyExecutable { args ->
                        val name = args.getOrNull(0)?.asStringOrNull() ?: return@ProxyExecutable null
                        element.attr(name, args.getOrNull(1)?.asStringOrNull().orEmpty())
                        null
                    }
                    "hasAttribute" -> return ProxyExecutable { args ->
                        args.getOrNull(0)?.asStringOrNull()?.let { element.hasAttr(it) } ?: false
                    }
                    "removeAttribute" -> return ProxyExecutable { args ->
                        args.getOrNull(0)?.asStringOrNull()?.let { element.removeAttr(it) }
                        null
                    }
                    "querySelector" -> return ProxyExecutable { args ->
                        val selector = args.getOrNull(0)?.asStringOrNull() ?: return@ProxyExecutable null
                        try {
                            element.selectFirst(selector)?.let { JsElement(it) }
                        } catch (e: Exception) {
                            null
                        }
                    }
                    "querySelectorAll" -> return ProxyExecutable { args ->
                        val selector = args.getOrNull(0)?.asStringOrNull() ?: return@ProxyExecutable elementsArray(emptyList())
                        try {
                            elementsArray(element.select(selector))
                        } catch (e: Exception) {
                            elementsArray(emptyList())
                        }
                    }
                    "getElementsByTagName" -> return ProxyExecutable { args ->
                        val name = args.getOrNull(0)?.asStringOrNull() ?: return@ProxyExecutable elementsArray(emptyList())
                        elementsArray(element.getElementsByTag(name))
                    }
                    "getElementsByClassName" -> return ProxyExecutable { args ->
                        val name = args.getOrNull(0)?.asStringOrNull() ?: return@ProxyExecutable elementsArray(emptyList())
                        elementsArray(element.getElementsByClass(name))
                    }
                    "remove" -> return ProxyExecutable {
                        element.remove()
                        null
                    }
                    "appendChild" -> return ProxyExecutable { args ->
                        val child = (args.getOrNull(0)?.asJsElement()) ?: return@ProxyExecutable null
                        element.appendChild(child.element)
                        args[0]
                    }
                    "click" -> return ProxyExecutable { null }
                    "addEventListener", "removeEventListener" -> return ProxyExecutable { null }
                }
                return expando[key]
            }

            override fun getMemberKeys(): Any = arrayOf(
                "outerHTML", "innerHTML", "textContent", "innerText", "tagName", "nodeName",
                "id", "className", "src", "href", "parentElement", "children",
                "firstElementChild", "lastElementChild", "nextElementSibling",
                "previousElementSibling", "dataset", "getAttribute", "setAttribute",
                "hasAttribute", "removeAttribute", "querySelector", "querySelectorAll",
                "getElementsByTagName", "getElementsByClassName", "remove", "appendChild",
                "click", "addEventListener", "removeEventListener",
            )

            @Suppress("UNCHECKED_CAST")
            override fun hasMember(key: String): Boolean =
                key in (getMemberKeys() as Array<String>) || key in expando

            override fun putMember(key: String, value: Value) {
                when (key) {
                    "innerHTML" -> element.html(value.asStringOrNull().orEmpty())
                    "textContent", "innerText" -> element.text(value.asStringOrNull().orEmpty())
                    "src", "href", "id", "className" -> element.attr(key, value.asStringOrNull().orEmpty())
                    else -> expando[key] = value
                }
            }
        }

        /** `element.dataset`: data-* attributes under camelCase names. */
        private inner class JsDataset(private val element: Element) : ProxyObject {
            override fun getMember(key: String): Any? {
                val attr = "data-" + key.replace(Regex("([A-Z])")) { "-${it.value.lowercase()}" }
                return if (element.hasAttr(attr)) element.attr(attr) else null
            }

            override fun getMemberKeys(): Any = element.attributes()
                .filter { it.key.startsWith("data-") }
                .map { it.key.removePrefix("data-").split("-").mapIndexed { i, part -> if (i == 0) part else part.replaceFirstChar(Char::uppercase) }.joinToString("") }
                .toTypedArray()

            @Suppress("UNCHECKED_CAST")
            override fun hasMember(key: String): Boolean = getMember(key) != null

            override fun putMember(key: String, value: Value) {
                val attr = "data-" + key.replace(Regex("([A-Z])")) { "-${it.value.lowercase()}" }
                element.attr(attr, value.asStringOrNull().orEmpty())
            }
        }

        /** The fetched page itself. The Jsoup document is swapped on every call. */
        private inner class JsDocument : ProxyObject {
            var doc: Document = Jsoup.parse("", "")
            private var baseUrl: String? = null

            fun setPage(html: String, url: String?) {
                doc = Jsoup.parse(html, url.orEmpty())
                baseUrl = url
            }

            private fun cookieString(): String {
                val url = baseUrl?.toHttpUrlOrNull() ?: return ""
                return try {
                    cookieJar.loadForRequest(url).joinToString("; ") { "${it.name}=${it.value}" }
                } catch (e: Exception) {
                    ""
                }
            }

            private fun setCookie(value: String) {
                val url = baseUrl?.toHttpUrlOrNull() ?: return
                val pair = value.substringBefore(";").trim()
                if (pair.isEmpty()) return
                try {
                    Cookie.parse(url, pair)?.let { cookieJar.saveFromResponse(url, listOf(it)) }
                } catch (e: Exception) {
                    // A malformed cookie assignment is a script error, not ours.
                }
            }

            override fun getMember(key: String): Any? = when (key) {
                "documentElement" -> doc.children().firstOrNull()?.let { JsElement(it) }
                "body" -> JsElement(doc.body())
                "head" -> JsElement(doc.head())
                "title" -> doc.title()
                "cookie" -> cookieString()
                "readyState" -> "complete"
                "location" -> location
                "querySelector" -> ProxyExecutable { args ->
                    val selector = args.getOrNull(0)?.asStringOrNull() ?: return@ProxyExecutable null
                    selectFirst(selector)?.let { JsElement(it) }
                }
                "querySelectorAll" -> ProxyExecutable { args ->
                    val selector = args.getOrNull(0)?.asStringOrNull()
                        ?: return@ProxyExecutable elementsArray(emptyList())
                    elementsArray(selectAll(selector))
                }
                "getElementById" -> ProxyExecutable { args ->
                    val id = args.getOrNull(0)?.asStringOrNull() ?: return@ProxyExecutable null
                    try {
                        doc.getElementById(id)?.let { JsElement(it) }
                    } catch (e: Exception) {
                        null
                    }
                }
                "getElementsByTagName" -> ProxyExecutable { args ->
                    val name = args.getOrNull(0)?.asStringOrNull()
                        ?: return@ProxyExecutable elementsArray(emptyList())
                    elementsArray(doc.getElementsByTag(name))
                }
                "getElementsByClassName" -> ProxyExecutable { args ->
                    val name = args.getOrNull(0)?.asStringOrNull()
                        ?: return@ProxyExecutable elementsArray(emptyList())
                    elementsArray(doc.getElementsByClass(name))
                }
                "createElement" -> ProxyExecutable { args ->
                    val tag = args.getOrNull(0)?.asStringOrNull() ?: return@ProxyExecutable null
                    try {
                        JsElement(Element(Tag.valueOf(tag), ""))
                    } catch (e: Exception) {
                        JsElement(Element("div"))
                    }
                }
                "addEventListener" -> ProxyExecutable { args ->
                    val type = args.getOrNull(0)?.asStringOrNull().orEmpty()
                    val listener = args.getOrNull(1)
                    if (type == "load" && listener != null && listener.canExecute()) {
                        scheduleTimer(arrayOf(listener, Value.asValue(0L)), repeat = false)
                    }
                    null
                }
                "removeEventListener" -> ProxyExecutable { null }
                else -> null
            }

            override fun getMemberKeys(): Any = arrayOf(
                "documentElement", "body", "head", "title", "cookie", "readyState",
                "location", "querySelector", "querySelectorAll", "getElementById",
                "getElementsByTagName", "getElementsByClassName", "createElement",
                "addEventListener", "removeEventListener",
            )

            @Suppress("UNCHECKED_CAST")
            override fun hasMember(key: String): Boolean = key in (getMemberKeys() as Array<String>)

            override fun putMember(key: String, value: Value) {
                when (key) {
                    "cookie" -> value.asStringOrNull()?.let { setCookie(it) }
                    "title" -> doc.title(value.asStringOrNull().orEmpty())
                }
            }
        }

        /** `location`: the fetched page's URL. Assignments are recorded, never followed. */
        private inner class JsLocation : ProxyObject {
            private var href: String = ""

            fun setHref(url: String?) {
                href = url.orEmpty()
            }

            override fun getMember(key: String): Any? {
                val url = href.toHttpUrlOrNull()
                return when (key) {
                    "href" -> href
                    "protocol" -> url?.let { "${it.scheme}:" }.orEmpty()
                    "host" -> url?.host.orEmpty()
                    "hostname" -> url?.host.orEmpty()
                    "port" -> url?.let { if (it.port == HttpUrlDefaultPort(it.scheme)) "" else it.port.toString() }.orEmpty()
                    "pathname" -> url?.encodedPath?.ifEmpty { "/" }.orEmpty()
                    "search" -> url?.encodedQuery?.let { "?$it" }.orEmpty()
                    "hash" -> url?.fragment?.let { "#$it" }.orEmpty()
                    "origin" -> url?.let { "${it.scheme}://${it.host}" }.orEmpty()
                    else -> null
                }
            }

            override fun getMemberKeys(): Any = arrayOf(
                "href", "protocol", "host", "hostname", "port", "pathname",
                "search", "hash", "origin",
            )

            @Suppress("UNCHECKED_CAST")
            override fun hasMember(key: String): Boolean = key in (getMemberKeys() as Array<String>)

            override fun putMember(key: String, value: Value) {
                if (key == "href") href = value.asStringOrNull().orEmpty()
            }
        }

        /** `window`: the global object. Assignments are kept and mirrored to the globals. */
        private inner class JsWindow : ProxyObject {
            private val expando = mutableMapOf<String, Any?>()

            override fun getMember(key: String): Any? {
                when (key) {
                    "document" -> return document
                    "location" -> return location
                    "localStorage" -> return storage
                    "navigator" -> return navigator
                    "performance" -> return performance
                    "console" -> return console
                    "fetch" -> return fetchFn ?: globalMember("fetch")
                    "XMLHttpRequest" -> return xhrClass ?: globalMember("XMLHttpRequest")
                    "setTimeout" -> return setTimeoutFn
                    "setInterval" -> return setIntervalFn
                    "clearTimeout" -> return clearTimeoutFn
                    "clearInterval" -> return clearIntervalFn
                    "atob" -> return atobFn
                    "btoa" -> return btoaFn
                    "stop" -> return ProxyExecutable { null }
                    "scrollTo" -> return ProxyExecutable { null }
                    "addEventListener" -> return ProxyExecutable { args ->
                        val type = args.getOrNull(0)?.asStringOrNull().orEmpty()
                        val listener = args.getOrNull(1)
                        if (type == "load" && listener != null && listener.canExecute()) {
                            scheduleTimer(arrayOf(listener, Value.asValue(0L)), repeat = false)
                        }
                        null
                    }
                    "removeEventListener" -> return ProxyExecutable { null }
                    "alert" -> return ProxyExecutable { null }
                    "confirm" -> return ProxyExecutable { false }
                    "innerHeight" -> return 800
                    "innerWidth" -> return 1280
                    "window", "self" -> return this
                }
                return expando[key] ?: globalMember(key)
            }

            override fun getMemberKeys(): Any = arrayOf(
                "document", "location", "localStorage", "navigator", "performance",
                "console", "fetch", "XMLHttpRequest", "setTimeout", "setInterval",
                "clearTimeout", "clearInterval", "stop", "scrollTo", "addEventListener",
                "removeEventListener", "alert", "confirm", "innerHeight", "innerWidth",
                "window", "self", "atob", "btoa",
            )

            @Suppress("UNCHECKED_CAST")
            override fun hasMember(key: String): Boolean =
                key in (getMemberKeys() as Array<String>) || key in expando || globalMember(key) != null

            override fun putMember(key: String, value: Value) {
                expando[key] = value
                try {
                    context?.getBindings("js")?.putMember(key, value)
                } catch (e: Throwable) {
                    // Mirroring a window property is best-effort.
                }
            }
        }

        /** `localStorage`, backed by an in-memory map per origin. */
        private inner class JsStorage(private val map: MutableMap<String, String>) : ProxyObject {
            override fun getMember(key: String): Any? = when (key) {
                "getItem" -> ProxyExecutable { args ->
                    map[args.getOrNull(0)?.asStringOrNull().orEmpty()]
                }
                "setItem" -> ProxyExecutable { args ->
                    map[args.getOrNull(0)?.asStringOrNull().orEmpty()] =
                        args.getOrNull(1)?.asStringOrNull().orEmpty()
                    null
                }
                "removeItem" -> ProxyExecutable { args ->
                    map.remove(args.getOrNull(0)?.asStringOrNull().orEmpty())
                    null
                }
                "clear" -> ProxyExecutable {
                    map.clear()
                    null
                }
                "length" -> map.size
                "key" -> ProxyExecutable { args ->
                    val index = args.getOrNull(0)?.takeIf { it.isNumber }?.asInt() ?: return@ProxyExecutable null
                    map.keys.elementAtOrNull(index)
                }
                else -> null
            }

            override fun getMemberKeys(): Any = arrayOf("getItem", "setItem", "removeItem", "clear", "length", "key")

            @Suppress("UNCHECKED_CAST")
            override fun hasMember(key: String): Boolean = key in (getMemberKeys() as Array<String>)

            override fun putMember(key: String, value: Value) = Unit
        }

        /** `navigator`: only what parser scripts read. */
        private inner class JsNavigator : ProxyObject {
            override fun getMember(key: String): Any? = when (key) {
                "userAgent" -> userAgent
                "language" -> "en-US"
                "languages" -> object : ProxyArray {
                    private val languages = listOf("en-US", "en")
                    override fun get(index: Long): Any? = languages[index.toInt()]
                    override fun set(index: Long, value: Value) = throw UnsupportedOperationException()
                    override fun getSize(): Long = languages.size.toLong()
                }
                "onLine" -> true
                else -> null
            }

            override fun getMemberKeys(): Any = arrayOf("userAgent", "language", "languages", "onLine")

            @Suppress("UNCHECKED_CAST")
            override fun hasMember(key: String): Boolean = key in (getMemberKeys() as Array<String>)

            override fun putMember(key: String, value: Value) = Unit
        }

        /** `performance`: resource timing is always empty — no page scripts run. */
        private inner class JsPerformance : ProxyObject {
            override fun getMember(key: String): Any? = when (key) {
                "now" -> ProxyExecutable { System.currentTimeMillis().toDouble() }
                "getEntriesByType" -> ProxyExecutable { elementsArray(emptyList()) }
                "timeOrigin" -> 0.0
                else -> null
            }

            override fun getMemberKeys(): Any = arrayOf("now", "getEntriesByType", "timeOrigin")

            @Suppress("UNCHECKED_CAST")
            override fun hasMember(key: String): Boolean = key in (getMemberKeys() as Array<String>)

            override fun putMember(key: String, value: Value) = Unit
        }

        /** `console`: parser scripts log into the void. */
        private inner class JsConsole : ProxyObject {
            override fun getMember(key: String): Any? = when (key) {
                "log", "info", "warn", "error", "debug", "time", "timeEnd" ->
                    ProxyExecutable { null }
                else -> null
            }

            override fun getMemberKeys(): Any =
                arrayOf("log", "info", "warn", "error", "debug", "time", "timeEnd")

            @Suppress("UNCHECKED_CAST")
            override fun hasMember(key: String): Boolean = key in (getMemberKeys() as Array<String>)

            override fun putMember(key: String, value: Value) = Unit
        }

        /** A `fetch()` response. */
        private inner class JsFetchResponse(private val outcome: FetchOutcome) : ProxyObject {
            override fun getMember(key: String): Any? = when (key) {
                "ok" -> outcome.status in 200..299
                "status" -> outcome.status
                "statusText" -> outcome.statusText
                "url" -> outcome.url
                "headers" -> JsFetchHeaders(outcome.headers)
                "bodyUsed" -> false
                "text" -> ProxyExecutable {
                    val defer = newDefer()
                    try {
                        defer.resolve.execute(outcome.body)
                    } catch (e: Throwable) {
                        // The context is gone; the promise stays pending.
                    }
                    defer.promise
                }
                "json" -> ProxyExecutable {
                    val defer = newDefer()
                    try {
                        val parsed = checkNotNull(context)
                            .eval("js", "JSON.parse(" + JSONObject.quote(outcome.body) + ")")
                        defer.resolve.execute(parsed)
                    } catch (e: Throwable) {
                        try {
                            defer.reject.execute("SyntaxError: Unexpected token")
                        } catch (e2: Throwable) {
                            // The context is gone; the promise stays pending.
                        }
                    }
                    defer.promise
                }
                "clone" -> ProxyExecutable { this@JsFetchResponse }
                else -> null
            }

            override fun getMemberKeys(): Any =
                arrayOf("ok", "status", "statusText", "url", "headers", "bodyUsed", "text", "json", "clone")

            @Suppress("UNCHECKED_CAST")
            override fun hasMember(key: String): Boolean = key in (getMemberKeys() as Array<String>)

            override fun putMember(key: String, value: Value) = Unit
        }

        /** `response.headers`. */
        private inner class JsFetchHeaders(private val headers: Map<String, String>) : ProxyObject {
            override fun getMember(key: String): Any? = when (key) {
                "get" -> ProxyExecutable { args ->
                    headers[args.getOrNull(0)?.asStringOrNull()?.lowercase()]
                }
                "has" -> ProxyExecutable { args ->
                    args.getOrNull(0)?.asStringOrNull()?.lowercase() in headers
                }
                else -> null
            }

            override fun getMemberKeys(): Any = arrayOf("get", "has")

            @Suppress("UNCHECKED_CAST")
            override fun hasMember(key: String): Boolean = key in (getMemberKeys() as Array<String>)

            override fun putMember(key: String, value: Value) = Unit
        }

        private fun globalMember(key: String): Any? = try {
            context?.getBindings("js")?.getMember(key)
        } catch (e: Throwable) {
            null
        }

        private fun Value.asJsElement(): JsElement? = try {
            if (isProxyObject) asProxyObject<ProxyObject>() as? JsElement else null
        } catch (e: Throwable) {
            null
        }

        private fun headersMap(response: okhttp3.Response): Map<String, String> {
            val map = LinkedHashMap<String, String>()
            for (i in 0 until response.headers.size) {
                map[response.headers.name(i).lowercase()] = response.headers.value(i)
            }
            return map
        }
    }

    private data class TimerTask(
        val id: Long,
        val dueMs: Long,
        val intervalMs: Long,
        val fn: Any,
        val args: List<Any?>,
    )

    private class JsDefer(val promise: Value, val resolve: Value, val reject: Value)

    companion object {
        /** Blank-page session key for the single-argument overload. */
        private const val BLANK_ORIGIN = "about:blank"

        /** Sessions are capped: each holds a GraalJS context. */
        private const val MAX_SESSIONS = 8

        /** Hard ceiling for one evaluation, whatever the caller asked for. */
        private const val MAX_JS_TIMEOUT_MS = 120_000L

        /** Grace for a fired fetch/XHR to land before the next script reads `window`. */
        private const val ASYNC_GRACE_MS = 2_000L

        /** Bound for the one-time warm-up wait; a pathological init must not hang the caller. */
        private const val WARM_UP_TIMEOUT_MS = 60_000L
    }
}

private fun HttpUrlDefaultPort(scheme: String): Int = when (scheme) {
    "http" -> 80
    "https" -> 443
    else -> -1
}

private fun Value?.asStringOrNull(): String? = when {
    this == null || isNull -> null
    isString -> asString()
    else -> toString()
}

/**
 * Prelude evaluated once per session: host-created promises, the
 * TextEncoder/TextDecoder polyfills (GraalJS on a stock JDK does not
 * provide them), `console`/`alert`/`confirm`, `fetch`, and
 * `XMLHttpRequest` (a real JS class, so its prototype stays patchable).
 * The `__atlas*` functions are host bridges installed by the session.
 */
private const val JS_PRELUDE = """
globalThis.__atlasDefer = () => {
  let resolve, reject;
  const promise = new Promise((res, rej) => { resolve = res; reject = rej; });
  return { promise: promise, resolve: resolve, reject: reject };
};
if (typeof TextEncoder === 'undefined') {
  globalThis.TextEncoder = class TextEncoder {
    constructor() { this.encoding = 'utf-8'; }
    encode(input) {
      const s = String(input == null ? '' : input);
      const bytes = [];
      for (let i = 0; i < s.length; i++) {
        let cp = s.codePointAt(i);
        if (cp > 0xFFFF) i++;
        if (cp < 0x80) bytes.push(cp);
        else if (cp < 0x800) bytes.push(0xC0 | (cp >> 6), 0x80 | (cp & 0x3F));
        else if (cp < 0x10000) bytes.push(0xE0 | (cp >> 12), 0x80 | ((cp >> 6) & 0x3F), 0x80 | (cp & 0x3F));
        else bytes.push(0xF0 | (cp >> 18), 0x80 | ((cp >> 12) & 0x3F), 0x80 | ((cp >> 6) & 0x3F), 0x80 | (cp & 0x3F));
      }
      return Uint8Array.from(bytes);
    }
  };
}
if (typeof TextDecoder === 'undefined') {
  globalThis.TextDecoder = class TextDecoder {
    constructor() { this.encoding = 'utf-8'; }
    decode(input) {
      const bytes = input instanceof Uint8Array ? input : new Uint8Array(input || []);
      let out = '';
      let i = 0;
      while (i < bytes.length) {
        const b = bytes[i++];
        let cp, need;
        if (b < 0x80) { cp = b; need = 0; }
        else if ((b & 0xE0) === 0xC0) { cp = b & 0x1F; need = 1; }
        else if ((b & 0xF0) === 0xE0) { cp = b & 0x0F; need = 2; }
        else { cp = b & 0x07; need = 3; }
        for (let k = 0; k < need && i < bytes.length; k++) cp = (cp << 6) | (bytes[i++] & 0x3F);
        out += String.fromCodePoint(cp);
      }
      return out;
    }
  };
}
globalThis.console = { log: () => {}, info: () => {}, warn: () => {}, error: () => {}, debug: () => {}, time: () => {}, timeEnd: () => {} };
globalThis.alert = () => {};
globalThis.confirm = () => false;
globalThis.fetch = (url, options) => {
  const d = __atlasDefer();
  __atlasFetch(d.resolve, d.reject, String(url), options ? JSON.stringify(options) : '{}');
  return d.promise;
};
globalThis.XMLHttpRequest = class XMLHttpRequest {
  constructor() {
    this.readyState = 0;
    this.status = 0;
    this.statusText = '';
    this.responseText = '';
    this.responseURL = '';
    this._method = 'GET';
    this._url = '';
    this._async = true;
    this._headers = {};
    this._responseHeadersJson = '{}';
    this._allResponseHeaders = '';
    this._listeners = {};
    this.onreadystatechange = null;
    this.onload = null;
    this.onerror = null;
  }
  open(method, url, async) {
    this._method = String(method);
    this._url = String(url);
    this._async = async !== false;
    this.readyState = 1;
  }
  setRequestHeader(name, value) { this._headers[String(name)] = String(value); }
  getResponseHeader(name) {
    const h = JSON.parse(this._responseHeadersJson || '{}');
    return h[String(name).toLowerCase()] || null;
  }
  getAllResponseHeaders() { return this._allResponseHeaders || ''; }
  addEventListener(type, listener) {
    const t = String(type);
    if (!this._listeners[t]) this._listeners[t] = [];
    this._listeners[t].push(listener);
  }
  removeEventListener(type, listener) {
    const list = this._listeners[String(type)];
    if (list) { const i = list.indexOf(listener); if (i !== -1) list.splice(i, 1); }
  }
  abort() {}
  send(body) {
    const payload = body == null ? null : String(body);
    if (this._async === false) {
      const st = __atlasXhrSync(this, this._method, this._url, JSON.stringify(this._headers), payload);
      this.__atlasFire(st >= 200 && st < 300 ? 'load' : 'error');
    } else {
      __atlasXhrAsync(this, this._method, this._url, JSON.stringify(this._headers), payload);
    }
  }
  __atlasFire(type) {
    this.readyState = 4;
    if (typeof this.onreadystatechange === 'function') { try { this.onreadystatechange(); } catch (e) {} }
    const list = this._listeners[type] || [];
    for (const fn of list) { try { fn(); } catch (e) {} }
    if (type === 'load' && typeof this.onload === 'function') { try { this.onload(); } catch (e) {} }
    if (type === 'error' && typeof this.onerror === 'function') { try { this.onerror(); } catch (e) {} }
  }
  __atlasComplete(status, statusText, responseText, responseUrl, headersJson, allHeaders) {
    this.status = status;
    this.statusText = statusText;
    this.responseText = responseText;
    this.responseURL = responseUrl;
    this._responseHeadersJson = headersJson;
    this._allResponseHeaders = allHeaders;
    this.__atlasFire(status >= 200 && status < 300 ? 'load' : 'error');
  }
};
"""
