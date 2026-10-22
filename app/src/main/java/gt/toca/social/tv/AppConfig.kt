package gt.toca.social.tv

/**
 * Central configuration for the leaderboard system.
 * Change BASE_API_URL to match your local corporate API endpoint.
 */
object AppConfig {
    /** Corporate API base URL (LAN address) */
    const val BASE_API_URL = "http://192.168.1.100:8080"

    /** How often to poll the corporate API (milliseconds) */
    const val POLL_INTERVAL_MS = 15_000L

    /** Number of top scores to keep per game type */
    const val TOP_N = 10

    /** Supported game types */
    val GAME_TYPES = listOf("STRIKER", "ATOMSPLITTER", "ZOMBIES", "TOCACHALLENGE")
}
