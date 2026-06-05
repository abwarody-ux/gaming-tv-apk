package com.gamingtv.app

object Config {
    // ── IMPORTANT : Modifier ces valeurs avant de compiler ──
    const val BACKEND_URL = "https://gaming-tv-backend-production.up.railway.app"
    const val TOKEN = "gaming-tv-test-2025"
    const val TV_ID = "TV01"  // Changer pour chaque TV : TV01, TV02, etc.

    // Timings
    const val OFFLINE_PAUSE_DELAY_MS = 60_000L  // 1 min hors ligne → pause
    const val SYNC_INTERVAL_MS = 10_000L         // Sync backend toutes les 10s
    const val ALERT_BLINK_THRESHOLD = 300        // 5 min → alerte clignotante
    const val RECONNECT_DELAY_MS = 3_000L        // Délai reconnexion
}
