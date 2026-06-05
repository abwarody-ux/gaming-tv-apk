package com.gamingtv.app

data class SessionState(
    val id: String = "",
    val tvId: String = "",
    val ticketNumber: String = "",
    val durationMinutes: Int = 0,
    val timeRemainingSeconds: Int = 0,
    val status: SessionStatus = SessionStatus.WAITING,
    val consoleType: String = "PS4",
    val startedAt: String = ""
)

enum class SessionStatus {
    WAITING,    // En attente de session
    ACTIVE,     // Session en cours
    PAUSED,     // Session en pause
    ENDED       // Session terminée
}

data class AlertMessage(
    val type: String = "INFO",
    val message: String = ""
)
