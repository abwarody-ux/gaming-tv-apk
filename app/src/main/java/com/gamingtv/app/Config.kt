package com.gamingtv.app

object Config {
    const val BACKEND_URL = "https://gaming-tv-backend-production.up.railway.app"
    const val TOKEN = "gaming-tv-test-2025"

    // Timings
    const val OFFLINE_PAUSE_DELAY_MS = 60_000L
    const val SYNC_INTERVAL_MS = 10_000L
    const val ALERT_BLINK_THRESHOLD = 300
    const val RECONNECT_DELAY_MS = 3_000L

    // SharedPreferences
    const val PREFS_NAME = "kasmok_prefs"
    const val KEY_TV_ID = "tv_id"
    const val KEY_TV_STATUS = "tv_status"
    const val KEY_MAC_ADDRESS = "mac_address"
}