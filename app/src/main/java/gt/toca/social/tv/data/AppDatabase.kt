package gt.toca.social.tv.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import gt.toca.social.tv.data.entity.AllTimeRecord
import gt.toca.social.tv.data.entity.DailyScore
import gt.toca.social.tv.data.entity.RecordBreak

@Database(
    entities = [DailyScore::class, AllTimeRecord::class, RecordBreak::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun scoreDao(): ScoreDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "scores.db"
                )
                    .allowMainThreadQueries() // Safe for kiosk app — simple reads from JS bridge
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
