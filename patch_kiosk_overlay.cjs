const fs = require("fs");
const path = "app/src/main/java/com/gamingtv/app/MainActivity.kt";
let raw = fs.readFileSync(path, "utf8");

const usesCRLF = raw.includes("\r\n");
let content = raw.replace(/\r\n/g, "\n");

// 1. Rendre showAlert() capable d'accepter une durée custom (défaut 4000ms inchangé)
const oldShowAlert = 'private fun showAlert(message: String, type: String = "INFO") {';
const newShowAlertSig = 'private fun showAlert(message: String, type: String = "INFO", durationMs: Long = 4000) {';
if (!content.includes(oldShowAlert)) throw new Error("Signature showAlert introuvable");
content = content.replace(oldShowAlert, newShowAlertSig);

const oldPostDelayed = "mainHandler.postDelayed(alertDismissJob!!, 4000)";
const newPostDelayed = "mainHandler.postDelayed(alertDismissJob!!, durationMs)";
if (!content.includes(oldPostDelayed)) throw new Error("postDelayed showAlert introuvable");
content = content.replace(oldPostDelayed, newPostDelayed);

// 2. Modifier onKeyDown pour afficher l'overlay 3 sec quand le kiosk bloque une touche
const oldOnKeyDown = [
    "    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {",
    "        if (kioskMode) {",
    "            if (keyCode == KeyEvent.KEYCODE_BACK ||",
    "                keyCode == KeyEvent.KEYCODE_HOME ||",
    "                keyCode == KeyEvent.KEYCODE_APP_SWITCH ||",
    "                keyCode == KeyEvent.KEYCODE_MENU) return true",
    "        }",
    "        return super.onKeyDown(keyCode, event)",
    "    }"
].join("\n");

const newOnKeyDown = [
    "    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {",
    "        if (kioskMode) {",
    "            if (keyCode == KeyEvent.KEYCODE_BACK ||",
    "                keyCode == KeyEvent.KEYCODE_HOME ||",
    "                keyCode == KeyEvent.KEYCODE_APP_SWITCH ||",
    "                keyCode == KeyEvent.KEYCODE_MENU) {",
    "                showAlert(\"Veuillez contacter KASMOK DIGITAL pour cette action\", \"WARNING\", 3000)",
    "                return true",
    "            }",
    "        }",
    "        return super.onKeyDown(keyCode, event)",
    "    }"
].join("\n");

if (!content.includes(oldOnKeyDown)) throw new Error("Bloc onKeyDown introuvable");
content = content.replace(oldOnKeyDown, newOnKeyDown);

if (usesCRLF) content = content.replace(/\n/g, "\r\n");

fs.writeFileSync(path, content, "utf8");
console.log("Overlay kiosk mode ajouté avec succès.");