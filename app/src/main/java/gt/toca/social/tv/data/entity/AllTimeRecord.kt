package gt.toca.social.tv.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "all_time_records",
    indices = [
        Index(value = ["gameType", "score"]),
        Index(
            value = ["gameType", "playerName", "score", "achievedDate"],
            unique = true
        )
    ]
)
data class AllTimeRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gameType: String,
    val playerName: String,
    val score: Int,
    val achievedDate: String,
    val recordedAt: Long = System.currentTimeMillis()
)
