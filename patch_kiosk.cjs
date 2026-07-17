const fs = require("fs");
const path = "app/src/main/java/com/gamingtv/app/MainActivity.kt";
let raw = fs.readFileSync(path, "utf8");

const usesCRLF = raw.includes("\r\n");
let content = raw.replace(/\r\n/g, "\n"); // normaliser en LF pour le matching

// 1. Imports
const oldImports = "import android.content.Context\nimport android.graphics.Color";
const newImports = "import android.app.admin.DevicePolicyManager\nimport android.content.ComponentName\nimport android.content.Context\nimport android.graphics.Color";
if (!content.includes(oldImports)) throw new Error("Bloc imports introuvable");
content = content.replace(oldImports, newImports);

// 2. Nouvelle fonction avant loadKioskSettings()
const anchor = "    private fun loadKioskSettings() {";
const kioskFunction = [
    "    private fun enableKioskModeIfPossible() {",
    "        try {",
    "            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager",
    "            val adminComponent = ComponentName(this, AdminReceiver::class.java)",
    "            if (dpm.isDeviceOwnerApp(packageName)) {",
    "                dpm.setLockTaskPackages(adminComponent, arrayOf(packageName))",
    "                startLockTask()",
    "                Log.d(TAG, \"Kiosk mode: Device Owner actif — lock task silencieux\")",
    "            } else {",
    "                startLockTask()",
    "                Log.d(TAG, \"Kiosk mode: pas Device Owner — screen pinning standard (confirmation utilisateur requise)\")",
    "            }",
    "        } catch (e: Exception) {",
    "            Log.e(TAG, \"Kiosk mode: échec activation — ${e.message}\")",
    "        }",
    "    }",
    "",
    anchor
].join("\n");
if (!content.includes(anchor)) throw new Error("Ancre loadKioskSettings introuvable");
content = content.replace(anchor, kioskFunction);

// 3. Remplacer les deux appels startLockTask()
const call1Old = "if (kioskMode) window.decorView.postDelayed({ startLockTask() }, 100)";
const call1New = "if (kioskMode) window.decorView.postDelayed({ enableKioskModeIfPossible() }, 100)";
if (!content.includes(call1Old)) throw new Error("Appel 1 introuvable");
content = content.replace(call1Old, call1New);

const call2Old = "if (kioskMode) { try { startLockTask() } catch (e: Exception) { } }";
const call2New = "if (kioskMode) enableKioskModeIfPossible()";
if (!content.includes(call2Old)) throw new Error("Appel 2 introuvable");
content = content.replace(call2Old, call2New);

if (usesCRLF) content = content.replace(/\n/g, "\r\n"); // réappliquer le style d'origine

fs.writeFileSync(path, content, "utf8");
console.log("MainActivity.kt patché avec succès.");