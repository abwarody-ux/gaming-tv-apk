package com.gamingtv.app

data class SessionState(
    val id: String = "",
    val tvId: String = "",
    val ticketNumber: String = "",
    val durationMinutes: Int = 0,
    val timeRemainingSeconds: Int = 0,
    val status: String = "WAITING",
    val consoleType: String = "PS4",
    val startedAt: String = ""
)

data class AlertMessage(
    val type: String = "INFO",
    val message: String = ""
)