package gt.toca.social.tv.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "record_breaks",
    indices = [
        Index(value = ["dismissed", "brokenAt"])
    ]
)
data class RecordBreak(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gameType: String,
    val playerName: String,
    val newScore: Int,
    val previousRecord: Int? = null,
    val previousHolder: String? = null,
    val brokenAt: Long = System.currentTimeMillis(),
    val dismissed: Boolean = false
)
