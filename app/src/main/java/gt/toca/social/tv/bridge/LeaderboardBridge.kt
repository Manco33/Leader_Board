package gt.toca.social.tv.bridge

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import com.google.gson.Gson
import gt.toca.social.tv.data.RecordBreakEvent
import gt.toca.social.tv.data.ScoreRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class LeaderboardBridge(
    private val repository: ScoreRepository,
    private val onRecordBroken: (RecordBreakEvent) -> Unit
) {
    private val gson = Gson()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val guatemalaTz = TimeZone.getTimeZone("America/Guatemala")
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).apply {
        timeZone = guatemalaTz
    }
    private val dateFormat = SimpleDateFormat("EEE, d MMM yyyy", Locale("es", "GT")).apply {
        timeZone = guatemalaTz
    }

    @JavascriptInterface
    fun sendScoresFromPage(json: String) {
        val breakEvent = repository.processScrapedScores(json)
        if (breakEvent != null) {
            mainHandler.post {
                onRecordBroken(breakEvent)
            }
        }
    }

    @JavascriptInterface
    fun getTodayScores(gameType: String): String {
        return try {
            gson.toJson(repository.getTodayScores(gameType))
        } catch (e: Exception) {
            "[]"
        }
    }

    @JavascriptInterface
    fun getAllTimeRecords(gameType: String): String {
        return try {
            gson.toJson(repository.getAllTimeRecords(gameType))
        } catch (e: Exception) {
            "[]"
        }
    }

    @JavascriptInterface
    fun getHallOfFame(): String {
        return try {
            repository.getAllTimeHallOfFameJson()
        } catch (e: Exception) {
            "[]"
        }
    }

    @JavascriptInterface
    fun getTodayDate(): String {
        return repository.getTodayDate()
    }

    @JavascriptInterface
    fun getCurrentTimeString(): String {
        return timeFormat.format(Date())
    }

    @JavascriptInterface
    fun getCurrentDateString(): String {
        return dateFormat.format(Date()).replaceFirstChar { it.uppercase() }
    }

    @JavascriptInterface
    fun log(msg: String) {
        android.util.Log.d("TVLeaderboardJS", msg)
    }
}
