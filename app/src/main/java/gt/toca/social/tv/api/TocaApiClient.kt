package gt.toca.social.tv.api

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class ApiScore(
    val nickname: String? = null,
    val name: String? = null,
    val score: Int? = null,
    val difficulty: String? = null,
    val date: String? = null
)

data class TodayApiResponse(
    val promo: Any? = null,
    val players: List<ApiScore>? = null
)

class TocaApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    companion object {
        private const val TAG = "TocaApiClient"
        private const val BASE_URL = "https://scoring.api.gt-gu-gua.toca.social/scores"
    }

    /**
     * Obtiene los puntajes de hoy para un juego dado.
     * Endpoint: https://scoring.api.gt-gu-gua.toca.social/scores?gameType={GAME}&date={YYYY-MM-DD}
     */
    suspend fun fetchTodayScores(gameType: String, dateString: String): List<ApiScore> = withContext(Dispatchers.IO) {
        val url = "$BASE_URL?gameType=$gameType&date=$dateString"
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Failed to fetch today scores for $gameType: HTTP ${response.code}")
                    return@withContext emptyList()
                }
                val body = response.body?.string() ?: return@withContext emptyList()
                parseScoresResponse(body)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching today scores for $gameType: ${e.message}")
            emptyList()
        }
    }

    /**
     * Obtiene el Salón de la Fama histórico (All-Time) para un juego dado.
     * Endpoint: https://scoring.api.gt-gu-gua.toca.social/scores?gameType={GAME}
     */
    suspend fun fetchAllTimeScores(gameType: String): List<ApiScore> = withContext(Dispatchers.IO) {
        val url = "$BASE_URL?gameType=$gameType"
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Failed to fetch all-time scores for $gameType: HTTP ${response.code}")
                    return@withContext emptyList()
                }
                val body = response.body?.string() ?: return@withContext emptyList()
                parseScoresResponse(body)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching all-time scores for $gameType: ${e.message}")
            emptyList()
        }
    }

    private fun parseScoresResponse(json: String): List<ApiScore> {
        val trimmed = json.trim()
        if (trimmed.startsWith("{")) {
            // Objeto con estructura: { "promo": null, "players": [...] }
            val parsed = gson.fromJson(trimmed, TodayApiResponse::class.java)
            return parsed.players ?: emptyList()
        } else if (trimmed.startsWith("[")) {
            // Array directo: [ { ... }, { ... } ]
            val listType = object : TypeToken<List<ApiScore>>() {}.type
            return gson.fromJson(trimmed, listType) ?: emptyList()
        }
        return emptyList()
    }
}
