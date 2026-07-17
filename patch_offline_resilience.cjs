const fs = require("fs");
const path = "app/src/main/java/com/gamingtv/app/MainActivity.kt";
let raw = fs.readFileSync(path, "utf8");
const usesCRLF = raw.includes("\r\n");
let content = raw.replace(/\r\n/g, "\n");

// A. Nouveaux champs
const fieldAnchor = "    private var waitingManagerCountdown: CountDownTimer? = null\n    private val MANAGER_WAIT_SECONDS = 90L";
const fieldAddition = fieldAnchor + "\n    private var offlineAutoStartCountdown: CountDownTimer? = null\n    private var startedOfflineLocally: Boolean = false\n    private var offlineStartedAtIso: String = \"\"";
if (!content.includes(fieldAnchor)) throw new Error("Ancre champs introuvable");
content = content.replace(fieldAnchor, fieldAddition);

// B. onFinish du countdown manager — declenche le second delai si hors-ligne
const finishAnchor = `            override fun onFinish() {
                mainHandler.post { binding.alertContainer.visibility = View.GONE }
            }
        }.start()`;
const finishAddition = `            override fun onFinish() {
                mainHandler.post {
                    binding.alertContainer.visibility = View.GONE
                    if (!socketManager.isConnected()) {
                        startOfflineAutoStartCountdown(session)
                    }
                }
            }
        }.start()`;
if (!content.includes(finishAnchor)) throw new Error("Ancre onFinish countdown introuvable");
content = content.replace(finishAnchor, finishAddition);

// C. Nouvelles fonctions — second delai + demarrage auto + annulation
const funcAnchor = "    private fun showPersistentManagerAlert(session: SessionState) {";
const funcAddition = `    private fun startOfflineAutoStartCountdown(session: SessionState) {
        cancelOfflineAutoStartCountdown()
        offlineAutoStartCountdown = object : CountDownTimer(MANAGER_WAIT_SECONDS * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                mainHandler.post {
                    if (!socketManager.isConnected() && currentSession?.status == "WAITING_MANAGER") {
                        val nowIso = java.time.Instant.now().toString()
                        offlineStartedAtIso = nowIso
                        startedOfflineLocally = true
                        Log.w(TAG, "Demarrage automatique hors-ligne — coupure reseau prolongee")
                        startActiveSession(session.copy(status = "ACTIVE"))
                    }
                }
            }
        }.start()
    }

    private fun cancelOfflineAutoStartCountdown() {
        offlineAutoStartCountdown?.cancel()
        offlineAutoStartCountdown = null
    }

${funcAnchor}`;
if (!content.includes(funcAnchor)) throw new Error("Ancre showPersistentManagerAlert introuvable");
content = content.replace(funcAnchor, funcAddition);

// D. updateConnectionStatus — annule le compte a rebours et reconcilie au retour de connexion
const connAnchor = `    private fun updateConnectionStatus(connected: Boolean) {
        binding.connectionDot.setBackgroundColor(
            if (connected) Color.parseColor(COLOR_GREEN) else Color.parseColor(COLOR_RED)
        )
        if (currentSession?.status != "WAITING_MANAGER") {
            binding.connectionText.text = if (connected) "EN LIGNE" else "HORS LIGNE"
        }
        if (!connected && currentSession != null) {
            showAlert("Connexion perdue — timer local actif", "WARNING")
        }
    }`;
const connAddition = `    private fun updateConnectionStatus(connected: Boolean) {
        binding.connectionDot.setBackgroundColor(
            if (connected) Color.parseColor(COLOR_GREEN) else Color.parseColor(COLOR_RED)
        )
        if (currentSession?.status != "WAITING_MANAGER") {
            binding.connectionText.text = if (connected) "EN LIGNE" else "HORS LIGNE"
        }
        if (!connected && currentSession != null) {
            showAlert("Connexion perdue — timer local actif", "WARNING")
        }
        if (connected) {
            cancelOfflineAutoStartCountdown()
            if (startedOfflineLocally) {
                socketManager.emitSessionStartedLocal(offlineStartedAtIso, true)
                startedOfflineLocally = false
            }
        }
    }`;
if (!content.includes(connAnchor)) throw new Error("Ancre updateConnectionStatus introuvable");
content = content.replace(connAnchor, connAddition);

// E. handleSessionStart — annule tout compte a rebours hors-ligne en attente
const startAnchor = `    private fun handleSessionStart(session: SessionState) {
        Log.d(TAG, "Session started: \${session.ticketNumber}")`;
const startAddition = `    private fun handleSessionStart(session: SessionState) {
        cancelOfflineAutoStartCountdown()
        Log.d(TAG, "Session started: \${session.ticketNumber}")`;
if (!content.includes(startAnchor)) throw new Error("Ancre handleSessionStart introuvable");
content = content.replace(startAnchor, startAddition);

// F. onDestroy — nettoyage
const destroyAnchor = `    override fun onDestroy() {
        super.onDestroy()
        cancelManagerCountdown()`;
const destroyAddition = `    override fun onDestroy() {
        super.onDestroy()
        cancelManagerCountdown()
        cancelOfflineAutoStartCountdown()`;
if (!content.includes(destroyAnchor)) throw new Error("Ancre onDestroy introuvable");
content = content.replace(destroyAnchor, destroyAddition);

if (usesCRLF) content = content.replace(/\n/g, "\r\n");
fs.writeFileSync(path, content, "utf8");
console.log("MainActivity.kt: mecanisme de resilience reseau complet en place.");