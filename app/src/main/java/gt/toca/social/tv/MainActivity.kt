package gt.toca.social.tv

import android.annotation.SuppressLint
import android.app.Activity
import android.net.http.SslError
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.webkit.*
import android.widget.FrameLayout
import com.google.gson.Gson
import gt.toca.social.tv.api.TocaApiClient
import gt.toca.social.tv.bridge.LeaderboardBridge
import gt.toca.social.tv.data.AppDatabase
import gt.toca.social.tv.data.RecordBreakEvent
import gt.toca.social.tv.data.ScoreRepository
import kotlinx.coroutines.*

class MainActivity : Activity() {

    /** Main display WebView — shows our beautiful local HTML always */
    private lateinit var displayWebView: WebView

    private lateinit var repository: ScoreRepository
    private val tocaApiClient = TocaApiClient()
    private val gson = Gson()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val gamesToSync = listOf("STRIKER", "ATOMSPLITTER", "ZOMBIES", "TOCACHALLENGE")

    companion object {
        const val LOCAL_ASSET_URL = "file:///android_asset/leaderboard/index.html"
        private const val TAG     = "TvTocaSocial"
        private const val SYNC_INTERVAL_MS   = 15_000L   // fetch API every 15 s
        private const val DISPLAY_REFRESH_MS =  5_000L   // refresh display every 5 s
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        // ── Database ──────────────────────────────────────
        val db = AppDatabase.getInstance(this)
        repository = ScoreRepository(db.scoreDao())
        Log.i(TAG, "SQLite inicializado")

        // ── Screen metrics ────────────────────────────────
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(dm)
        val sw = dm.widthPixels   // 1920 landscape
        val sh = dm.heightPixels  // 1080 landscape

        val container = FrameLayout(this)

        // ══════════════════════════════════════════════════
        //  1. DISPLAY WebView — rotated 270°, always shows
        //     our local beautiful retro UI
        // ══════════════════════════════════════════════════
        displayWebView = WebView(this)
        val displayParams = FrameLayout.LayoutParams(sh, sw)
        displayParams.leftMargin = (sw - sh) / 2
        displayParams.topMargin  = (sh - sw) / 2
        displayWebView.layoutParams = displayParams
        displayWebView.rotation = 270f

        configureWebViewSettings(displayWebView)
        WebView.setWebContentsDebuggingEnabled(true)

        // JS bridge so the local HTML can read SQLite
        val bridge = LeaderboardBridge(repository) { recordEvent ->
            triggerRecordCelebration(recordEvent)
        }
        displayWebView.addJavascriptInterface(bridge, "Android")

        displayWebView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(msg: ConsoleMessage?): Boolean {
                Log.d("DisplayWV", "${msg?.message()} [${msg?.lineNumber()}]")
                return true
            }
        }

        displayWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.i(TAG, "Display loaded: $url")
                scheduleDisplayRefresh()
            }
            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                handler?.proceed()
            }
        }

        displayWebView.loadUrl(LOCAL_ASSET_URL)
        container.addView(displayWebView)

        setContentView(container)

        // ══════════════════════════════════════════════════
        //  2. Direct REST API Synchronizer (Official Toca API)
        //     Fetches STRIKER, ATOMSPLITTER, ZOMBIES, TOCACHALLENGE
        //     cleanly with real nicknames and real scores
        // ══════════════════════════════════════════════════
        startApiSyncWorker()
    }

    // ═══════════════════════════════════════════════
    //  WebView settings helper
    // ═══════════════════════════════════════════════
    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebViewSettings(wv: WebView) {
        wv.settings.apply {
            javaScriptEnabled          = true
            domStorageEnabled          = true
            databaseEnabled            = true
            loadWithOverviewMode       = true
            useWideViewPort            = true
            builtInZoomControls        = false
            displayZoomControls        = false
            setSupportZoom(false)
            mediaPlaybackRequiresUserGesture = false
            textZoom                   = 100
            allowFileAccess            = true
            allowContentAccess         = true
            mixedContentMode           = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode                  = WebSettings.LOAD_DEFAULT
        }
    }

    // ═══════════════════════════════════════════════
    //  Background Coroutine API Sync Worker
    // ═══════════════════════════════════════════════
    private fun startApiSyncWorker() {
        activityScope.launch {
            Log.i(TAG, "Iniciando worker de sincronización con API oficial de Toca Social...")
            while (isActive) {
                val todayDate = repository.getTodayDate()
                for (game in gamesToSync) {
                    try {
                        var todayScores = tocaApiClient.fetchTodayScores(game, todayDate)
                        if (todayScores.isEmpty()) {
                            // Fallback por si la fecha en servidor aún no tiene registros o cambio de día
                            val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("America/Guatemala"))
                            cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
                            val yesterday = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply {
                                timeZone = java.util.TimeZone.getTimeZone("America/Guatemala")
                            }.format(cal.time)
                            val fallbackScores = tocaApiClient.fetchTodayScores(game, yesterday)
                            if (fallbackScores.isNotEmpty()) {
                                todayScores = fallbackScores
                            }
                        }

                        val allTimeScores = tocaApiClient.fetchAllTimeScores(game)

                        Log.d(TAG, "Sync $game -> Hoy: ${todayScores.size} jug., AllTime: ${allTimeScores.size} jug.")

                        val breakEvent = withContext(Dispatchers.IO) {
                            repository.syncGameFromApi(game, todayScores, allTimeScores)
                        }

                        if (breakEvent != null) {
                            triggerRecordCelebration(breakEvent)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error sincronizando $game: ${e.message}")
                    }
                }

                // Inmediatamente refrescar la UI con los nuevos datos
                displayWebView.evaluateJavascript(
                    "if (typeof loadAndRender === 'function') { loadAndRender(); renderFooter(); }",
                    null
                )

                delay(SYNC_INTERVAL_MS)
            }
        }
    }

    // ═══════════════════════════════════════════════
    //  Tell the display HTML to re-render from SQLite
    // ═══════════════════════════════════════════════
    private fun scheduleDisplayRefresh() {
        mainHandler.postDelayed(object : Runnable {
            override fun run() {
                displayWebView.evaluateJavascript(
                    "if (typeof loadAndRender === 'function') { loadAndRender(); renderFooter(); }",
                    null
                )
                mainHandler.postDelayed(this, DISPLAY_REFRESH_MS)
            }
        }, DISPLAY_REFRESH_MS)
    }

    // ═══════════════════════════════════════════════
    //  Record break celebration → show overlay in UI
    // ═══════════════════════════════════════════════
    private fun triggerRecordCelebration(event: RecordBreakEvent) {
        val json = gson.toJson(event)
        runOnUiThread {
            displayWebView.evaluateJavascript(
                "if (window.showRecordBreakCelebration) { window.showRecordBreakCelebration($json); }",
                null
            )
        }
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        return super.onKeyDown(keyCode, event)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Kiosk mode — block back button
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        displayWebView.destroy()
    }
}
