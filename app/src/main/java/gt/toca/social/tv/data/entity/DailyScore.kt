package gt.toca.social.tv.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "daily_scores",
    indices = [
        Index(value = ["playedDate", "gameType", "score"]),
        Index(
            value = ["gameType", "playerName", "score", "playedDate"],
            unique = true
        )
    ]
)
data class DailyScore(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val externalId: String? = null,
    val gameType: String,
    val playerName: String,
    val score: Int,
    val playedDate: String,
    val fetchedAt: Long = System.currentTimeMillis()
)
