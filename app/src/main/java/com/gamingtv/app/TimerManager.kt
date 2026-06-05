package com.gamingtv.app

import kotlinx.coroutines.*

class TimerManager(
    private val onTick: (Int) -> Unit,
    private val onFinished: () -> Unit
) {
    private var timerJob: Job? = null
    private var timeRemainingSeconds: Int = 0
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isRunning = false

    fun start(seconds: Int) {
        timeRemainingSeconds = seconds
        resume()
    }

    fun resume() {
        if (isRunning) return
        isRunning = true
        timerJob = scope.launch {
            while (timeRemainingSeconds > 0 && isRunning) {
                delay(1000)
                timeRemainingSeconds--
                onTick(timeRemainingSeconds)
                if (timeRemainingSeconds <= 0) {
                    isRunning = false
                    onFinished()
                    break
                }
            }
        }
    }

    fun pause() {
        isRunning = false
        timerJob?.cancel()
        timerJob = null
    }

    fun stop() {
        isRunning = false
        timerJob?.cancel()
        timerJob = null
        timeRemainingSeconds = 0
    }

    fun syncTime(seconds: Int) {
        // Réconciliation avec le backend
        timeRemainingSeconds = seconds
    }

    fun getTimeRemaining() = timeRemainingSeconds
    fun isRunning() = isRunning

    fun destroy() {
        stop()
        scope.cancel()
    }
}
