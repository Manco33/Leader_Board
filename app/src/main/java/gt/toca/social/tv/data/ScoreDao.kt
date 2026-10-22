package gt.toca.social.tv.data

import android.content.Context
import androidx.room.*
import gt.toca.social.tv.data.entity.AllTimeRecord
import gt.toca.social.tv.data.entity.DailyScore
import gt.toca.social.tv.data.entity.RecordBreak

@Dao
interface ScoreDao {

    // ─── Daily Scores ────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertDailyScore(score: DailyScore): Long

    @Query("""
        SELECT * FROM daily_scores 
        WHERE playedDate = :date AND gameType = :gameType 
        ORDER BY score DESC 
        LIMIT :limit
    """)
    fun getTopDailyScores(gameType: String, date: String, limit: Int = 10): List<DailyScore>

    @Query("""
        SELECT * FROM daily_scores 
        WHERE gameType = :gameType 
        ORDER BY playedDate DESC, score DESC 
        LIMIT :limit
    """)
    fun getLatestDailyScores(gameType: String, limit: Int = 10): List<DailyScore>

    // ─── All-Time Records (Hall of Fame) ─────────────
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAllTimeRecord(record: AllTimeRecord): Long

    @Query("""
        SELECT * FROM all_time_records 
        WHERE gameType = :gameType 
        ORDER BY score DESC 
        LIMIT :limit
    """)
    fun getTopAllTimeRecords(gameType: String, limit: Int = 10): List<AllTimeRecord>

    @Query("SELECT * FROM all_time_records ORDER BY gameType, score DESC")
    fun getAllTimeRecords(): List<AllTimeRecord>

    @Query("""
        SELECT * FROM all_time_records 
        WHERE gameType = :gameType 
        ORDER BY score DESC 
        LIMIT 1
    """)
    fun getCurrentRecord(gameType: String): AllTimeRecord?

    @Query("SELECT COUNT(*) FROM all_time_records WHERE gameType = :gameType")
    fun getAllTimeCount(gameType: String): Int

    @Query("""
        SELECT MIN(s) FROM (
            SELECT score AS s FROM all_time_records 
            WHERE gameType = :gameType 
            ORDER BY score DESC 
            LIMIT :limit
        )
    """)
    fun getMinAllTimeScore(gameType: String, limit: Int = 10): Int?

    @Query("""
        DELETE FROM all_time_records 
        WHERE gameType = :gameType 
        AND id NOT IN (
            SELECT id FROM all_time_records 
            WHERE gameType = :gameType 
            ORDER BY score DESC 
            LIMIT :keepCount
        )
    """)
    fun trimAllTimeRecords(gameType: String, keepCount: Int = 10)

    @Query("DELETE FROM all_time_records WHERE playerName IN ('Mateo G.', 'Carlos R.', 'Lucas M.', 'Valeria P.', 'Diego S.', 'Fernando K.', 'Andrea L.', 'Sofia B.', 'Alejandro V.', 'JENNY', 'Jenny')")
    fun clearFakeSeeds()

    @Query("DELETE FROM daily_scores WHERE playerName IN ('Mateo G.', 'Carlos R.', 'Lucas M.', 'Valeria P.', 'Diego S.', 'Fernando K.', 'Andrea L.', 'Sofia B.', 'Alejandro V.', 'JENNY', 'Jenny')")
    fun clearDailyFakeSeeds()

    /** Nuclear option — wipe ALL data from all tables */
    @Query("DELETE FROM all_time_records")
    fun deleteAllTimeRecords()

    @Query("DELETE FROM daily_scores")
    fun deleteAllDailyScores()

    @Query("DELETE FROM record_breaks")
    fun deleteAllRecordBreaks()

    /** Remove a specific player by name from all tables */
    @Query("DELETE FROM all_time_records WHERE playerName = :name")
    fun deleteAllTimeByPlayer(name: String)

    @Query("DELETE FROM daily_scores WHERE playerName = :name")
    fun deleteDailyByPlayer(name: String)

    /** Delete rows where playerName looks like a date (07/09/2026, 7/9/2026, etc.) */
    @Query("DELETE FROM all_time_records WHERE playerName GLOB '[0-9][0-9]/[0-9][0-9]/[0-9][0-9][0-9][0-9]' OR playerName GLOB '[0-9]/[0-9]/[0-9][0-9][0-9][0-9]' OR playerName GLOB '[0-9][0-9]/[0-9]/[0-9][0-9][0-9][0-9]'")
    fun clearDateLikeAllTime()

    @Query("DELETE FROM daily_scores WHERE playerName GLOB '[0-9][0-9]/[0-9][0-9]/[0-9][0-9][0-9][0-9]' OR playerName GLOB '[0-9]/[0-9]/[0-9][0-9][0-9][0-9]' OR playerName GLOB '[0-9][0-9]/[0-9]/[0-9][0-9][0-9][0-9]'")
    fun clearDateLikeDaily()

    /** Delete rows where playerName contains fewer than 2 alphabetic characters */
    @Query("""
        DELETE FROM all_time_records
        WHERE (
            length(playerName)
            - length(replace(replace(replace(replace(replace(
              replace(replace(replace(replace(replace(replace(replace(
              replace(replace(replace(replace(replace(replace(replace(
              replace(replace(replace(replace(replace(replace(replace(
              playerName,
              'a',''),'b',''),'c',''),'d',''),'e',''),'f',''),'g',''),
              'h',''),'i',''),'j',''),'k',''),'l',''),'m',''),'n',''),
              'o',''),'p',''),'q',''),'r',''),'s',''),'t',''),'u',''),
              'v',''),'w',''),'x',''),'y',''),'z',''))
        ) < 2
    """)
    fun clearNumericOnlyAllTime()

    @Query("""
        DELETE FROM daily_scores
        WHERE (
            length(playerName)
            - length(replace(replace(replace(replace(replace(
              replace(replace(replace(replace(replace(replace(replace(
              replace(replace(replace(replace(replace(replace(replace(
              replace(replace(replace(replace(replace(replace(replace(
              playerName,
              'a',''),'b',''),'c',''),'d',''),'e',''),'f',''),'g',''),
              'h',''),'i',''),'j',''),'k',''),'l',''),'m',''),'n',''),
              'o',''),'p',''),'q',''),'r',''),'s',''),'t',''),'u',''),
              'v',''),'w',''),'x',''),'y',''),'z',''))
        ) < 2
    """)
    fun clearNumericOnlyDaily()

    // ─── Direct Game Sync Queries ──────────────────
    @Query("DELETE FROM daily_scores WHERE gameType = :gameType AND playedDate = :date")
    fun deleteDailyScoresForGameAndDate(gameType: String, date: String)

    @Query("DELETE FROM all_time_records WHERE gameType = :gameType")
    fun deleteAllTimeRecordsForGame(gameType: String)

    // ─── Record Breaks ──────────────────────────────
    @Insert
    fun insertRecordBreak(recordBreak: RecordBreak): Long

    @Query("""
        SELECT * FROM record_breaks 
        ORDER BY brokenAt DESC 
        LIMIT 10
    """)
    fun getRecentBreaks(): List<RecordBreak>
}

