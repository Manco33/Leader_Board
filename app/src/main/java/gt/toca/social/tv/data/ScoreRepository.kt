package gt.toca.social.tv.data

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import gt.toca.social.tv.api.ApiScore
import gt.toca.social.tv.data.entity.AllTimeRecord
import gt.toca.social.tv.data.entity.DailyScore
import gt.toca.social.tv.data.entity.RecordBreak
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class ScrapedScore(
    val gameType: String? = null,
    val playerName: String? = null,
    val score: Int? = null
)

data class RecordBreakEvent(
    val gameType: String,
    val playerName: String,
    val newScore: Int,
    val previousRecord: Int?,
    val previousHolder: String?
)

class ScoreRepository(private val dao: ScoreDao) {

    private val gson = Gson()
    private val guatemalaTz = TimeZone.getTimeZone("America/Guatemala")
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = guatemalaTz
    }

    init {
        // Limpiar datos de prueba / seeds falsos y nombres corruptos
        try {
            dao.clearFakeSeeds()
            dao.clearDailyFakeSeeds()
            dao.deleteAllTimeByPlayer("JENNY")
            dao.deleteAllTimeByPlayer("Jenny")
            dao.deleteDailyByPlayer("JENNY")
            dao.deleteDailyByPlayer("Jenny")
            dao.clearDateLikeAllTime()
            dao.clearDateLikeDaily()
            dao.clearNumericOnlyAllTime()
            dao.clearNumericOnlyDaily()
        } catch (e: Exception) {
            Log.e("ScoreRepo", "Error clearing test data: ${e.message}")
        }
    }

    /** Borra TODOS los datos — usar solo para reset completo en desarrollo */
    fun nukeAllData() {
        dao.deleteAllTimeRecords()
        dao.deleteAllDailyScores()
        dao.deleteAllRecordBreaks()
        Log.w("ScoreRepo", "⚠️ TODOS los datos fueron eliminados")
    }

    fun getTodayDate(): String = dateFormat.format(Date())

    /**
     * Sincroniza directamente los puntajes obtenidos de la API REST oficial de Toca Social.
     * Garantiza nombres limpios, puntajes exactos y actualización inmediata de SQLite.
     */
    fun syncGameFromApi(
        gameType: String,
        todayScores: List<ApiScore>,
        allTimeScores: List<ApiScore>
    ): RecordBreakEvent? {
        val today = getTodayDate()
        var breakEvent: RecordBreakEvent? = null

        // 1. Guardar o actualizar Salón de la Fama histórico (All-Time)
        val previousTop1 = dao.getCurrentRecord(gameType)
        if (allTimeScores.isNotEmpty()) {
            dao.deleteAllTimeRecordsForGame(gameType)
            for (item in allTimeScores.take(10)) {
                val rawName = item.nickname?.trim()?.ifBlank { null } ?: item.name?.trim() ?: continue
                val score = item.score ?: continue
                if (score <= 0) continue
                val dateStr = item.date?.substringBefore("T") ?: today
                try {
                    dao.insertAllTimeRecord(
                        AllTimeRecord(
                            gameType = gameType,
                            playerName = rawName,
                            score = score,
                            achievedDate = dateStr
                        )
                    )
                } catch (e: Exception) {
                    Log.e("ScoreRepo", "Error saving all-time score: ${e.message}")
                }
            }
        }

        // 2. Guardar o actualizar Campeones de Hoy
        if (todayScores.isNotEmpty()) {
            dao.deleteDailyScoresForGameAndDate(gameType, today)
            for (item in todayScores.take(10)) {
                val rawName = item.nickname?.trim()?.ifBlank { null } ?: item.name?.trim() ?: continue
                val score = item.score ?: continue
                if (score <= 0) continue
                try {
                    dao.insertDailyScore(
                        DailyScore(
                            gameType = gameType,
                            playerName = rawName,
                            score = score,
                            playedDate = today
                        )
                    )
                } catch (e: Exception) {
                    Log.e("ScoreRepo", "Error saving daily score: ${e.message}")
                }

                // Si este puntaje de hoy rompió el récord histórico anterior
                if (previousTop1 != null && score > previousTop1.score && rawName != previousTop1.playerName) {
                    breakEvent = RecordBreakEvent(
                        gameType = gameType,
                        playerName = rawName,
                        newScore = score,
                        previousRecord = previousTop1.score,
                        previousHolder = previousTop1.playerName
                    )
                    dao.insertRecordBreak(
                        RecordBreak(
                            gameType = gameType,
                            playerName = rawName,
                            newScore = score,
                            previousRecord = previousTop1.score,
                            previousHolder = previousTop1.playerName
                        )
                    )
                }
            }
        }

        return breakEvent
    }

    /**
     * Procesa una lista de puntajes extraídos en vivo desde el DOM del WebView (fallback).
     */
    fun processScrapedScores(json: String): RecordBreakEvent? {
        if (json.isBlank()) return null
        val today = getTodayDate()

        val listType = object : TypeToken<List<ScrapedScore>>() {}.type
        val scores: List<ScrapedScore> = try {
            gson.fromJson(json, listType) ?: emptyList()
        } catch (e: Exception) {
            Log.e("ScoreRepo", "Error parsing scraped scores: ${e.message}")
            emptyList()
        }

        var newBreakEvent: RecordBreakEvent? = null

        for (item in scores) {
            val gameType = item.gameType?.trim()?.uppercase() ?: continue
            val playerName = item.playerName?.trim() ?: continue
            val score = item.score ?: 0

            if (score <= 0 || playerName.isBlank()) continue

            try {
                dao.insertDailyScore(
                    DailyScore(
                        gameType = gameType,
                        playerName = playerName,
                        score = score,
                        playedDate = today
                    )
                )
            } catch (e: Exception) {}

            val currentTop1 = dao.getCurrentRecord(gameType)
            val isNewAllTimeRecord = (currentTop1 == null || score > currentTop1.score)

            val count = dao.getAllTimeCount(gameType)
            val minScore = dao.getMinAllTimeScore(gameType, 10) ?: 0
            val qualifiesForTop10 = count < 10 || score > minScore

            if (qualifiesForTop10) {
                dao.insertAllTimeRecord(
                    AllTimeRecord(
                        gameType = gameType,
                        playerName = playerName,
                        score = score,
                        achievedDate = today
                    )
                )
                dao.trimAllTimeRecords(gameType, 10)
            }

            if (isNewAllTimeRecord) {
                val event = RecordBreakEvent(
                    gameType = gameType,
                    playerName = playerName,
                    newScore = score,
                    previousRecord = currentTop1?.score,
                    previousHolder = currentTop1?.playerName
                )
                dao.insertRecordBreak(
                    RecordBreak(
                        gameType = gameType,
                        playerName = playerName,
                        newScore = score,
                        previousRecord = currentTop1?.score,
                        previousHolder = currentTop1?.playerName
                    )
                )
                newBreakEvent = event
                Log.i("ScoreRepo", "🏆 ¡NUEVO RÉCORD HISTÓRICO! $playerName -> $score pts en $gameType")
            }
        }

        return newBreakEvent
    }

    fun getAllTimeHallOfFameJson(): String {
        val records = dao.getAllTimeRecords()
        return gson.toJson(records)
    }

    fun getAllTimeRecords(gameType: String): List<AllTimeRecord> =
        dao.getTopAllTimeRecords(gameType, 10)

    fun getTodayScores(gameType: String): List<DailyScore> {
        val today = getTodayDate()
        val scores = dao.getTopDailyScores(gameType, today, 10)
        if (scores.isNotEmpty()) return scores
        return dao.getLatestDailyScores(gameType, 10)
    }
}
