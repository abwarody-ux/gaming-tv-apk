package com.gamingtv.app

import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URI

class SocketManager(
    private val tvId: String,
    private val tvToken: String,
    private val onSessionStart: (SessionState) -> Unit,
    private val onTimeSync: (Int, String) -> Unit,
    private val onSessionPause: () -> Unit,
    private val onSessionResume: () -> Unit,
    private val onSessionEnd: (String) -> Unit,
    private val onAlert: (AlertMessage) -> Unit,
    private val onTimeAdded: (Int, Int) -> Unit,
    private val onConnectionChange: (Boolean) -> Unit
) {
    private var socket: Socket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var offlineJob: Job? = null
    private var isConnected = false

    companion object {
        private const val TAG = "SocketManager"
    }

    fun connect() {
        try {
            val options = IO.Options().apply {
                transports = arrayOf("websocket", "polling")
                reconnection = true
                reconnectionDelay = Config.RECONNECT_DELAY_MS
                reconnectionAttempts = Int.MAX_VALUE
                query = "role=tv&tvId=${tvId}&token=${tvToken}"
            }

            socket = IO.socket(URI.create(Config.BACKEND_URL), options)

            socket?.apply {
                on(Socket.EVENT_CONNECT) {
                    Log.d(TAG, "Connected to backend")
                    isConnected = true
                    cancelOfflineTimer()
                    onConnectionChange(true)
                }

                on(Socket.EVENT_DISCONNECT) {
                    Log.d(TAG, "Disconnected from backend")
                    isConnected = false
                    onConnectionChange(false)
                    startOfflineTimer()
                }

                on(Socket.EVENT_CONNECT_ERROR) { args ->
                    Log.e(TAG, "Connection error: ${args[0]}")
                    isConnected = false
                    onConnectionChange(false)
                }

                on("SESSION_START") { args ->
                    try {
                        val data = args[0] as JSONObject
                        val session = parseSession(data)
                        Log.d(TAG, "Session started: ${session.ticketNumber} status=${session.status}")
                        onSessionStart(session)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing SESSION_START: ${e.message}")
                    }
                }

                on("TIME_SYNC") { args ->
                    try {
                        val data = args[0] as JSONObject
                        val remaining = data.getInt("timeRemainingSeconds")
                        val status = data.optString("status", "ACTIVE")
                        onTimeSync(remaining, status)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing TIME_SYNC: ${e.message}")
                    }
                }

                on("SESSION_PAUSE") { _ ->
                    Log.d(TAG, "Session paused by manager")
                    onSessionPause()
                }

                on("SESSION_RESUME") { _ ->
                    Log.d(TAG, "Session resumed by manager")
                    onSessionResume()
                }

                on("SESSION_END") { args ->
                    try {
                        val data = args[0] as JSONObject
                        val message = data.optString("message", "Session terminée")
                        Log.d(TAG, "Session ended: $message")
                        onSessionEnd(message)
                    } catch (e: Exception) {
                        onSessionEnd("Session terminée")
                    }
                }

                on("ALERT") { args ->
                    try {
                        val data = args[0] as JSONObject
                        val alert = AlertMessage(
                            type = data.optString("type", "INFO"),
                            message = data.optString("message", "")
                        )
                        Log.d(TAG, "Alert received: ${alert.message}")
                        onAlert(alert)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing ALERT: ${e.message}")
                    }
                }

                on("TIME_ADDED") { args ->
                    try {
                        val data = args[0] as JSONObject
                        val added = data.getInt("addedMinutes")
                        val remaining = data.getInt("timeRemainingSeconds")
                        onTimeAdded(added, remaining)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing TIME_ADDED: ${e.message}")
                    }
                }

                on("TV_READY") { _ ->
                    Log.d(TAG, "TV ready, waiting for session")
                }
            }

            socket?.connect()

        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect: ${e.message}")
        }
    }

    fun disconnect() {
        cancelOfflineTimer()
        socket?.disconnect()
        socket?.off()
        socket = null
        scope.cancel()
    }

    fun emitSessionPaused() {
        socket?.emit("SESSION_PAUSED", JSONObject().apply {
            put("tvId", tvId)
        })
    }

    fun emitSessionEndedLocal(timeRemaining: Int) {
        socket?.emit("SESSION_ENDED_LOCAL", JSONObject().apply {
            put("tvId", tvId)
            put("timeRemaining", timeRemaining)
        })
    }

    fun isConnected() = isConnected

    private fun startOfflineTimer() {
        offlineJob = scope.launch {
            delay(Config.OFFLINE_PAUSE_DELAY_MS)
            Log.w(TAG, "Offline for 1 minute — pausing session")
            emitSessionPaused()
            onSessionPause()
        }
    }

    private fun cancelOfflineTimer() {
        offlineJob?.cancel()
        offlineJob = null
    }

    private fun parseSession(data: JSONObject): SessionState {
        return SessionState(
            id = data.optString("id", ""),
            tvId = data.optString("tvId", tvId),
            ticketNumber = data.optString("ticketNumber", ""),
            durationMinutes = data.optInt("durationMinutes", 0),
            timeRemainingSeconds = data.optInt("timeRemainingSeconds", 0),
            status = data.optString("status", "ACTIVE"),
            consoleType = data.optString("consoleType", "PS4"),
            startedAt = data.optString("startedAt", "")
        )
    }
}