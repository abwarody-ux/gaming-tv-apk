const fs = require("fs");
const path = "app/src/main/java/com/gamingtv/app/MainActivity.kt";
let raw = fs.readFileSync(path, "utf8");
const usesCRLF = raw.includes("\r\n");
let content = raw.replace(/\r\n/g, "\n");

const oldRequest = [
    "    private fun loadKioskSettings() {",
    "        val client = OkHttpClient()",
    "        val request = Request.Builder()",
    "            .url(\"${Config.BACKEND_URL}/settings\")",
    "            .addHeader(\"Authorization\", \"Bearer ${Config.TOKEN}\")",
    "            .build()"
].join("\n");

const newRequest = [
    "    private fun loadKioskSettings() {",
    "        val client = OkHttpClient()",
    "        val request = Request.Builder()",
    "            .url(\"${Config.BACKEND_URL}/kasmok/tv-registry/$tvId/settings\")",
    "            .build()"
].join("\n");

if (!content.includes(oldRequest)) throw new Error("Bloc requete loadKioskSettings introuvable");
content = content.replace(oldRequest, newRequest);

if (usesCRLF) content = content.replace(/\n/g, "\r\n");
fs.writeFileSync(path, content, "utf8");
console.log("loadKioskSettings() pointe maintenant vers l'endpoint public par TV.");