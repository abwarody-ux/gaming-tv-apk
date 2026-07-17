const fs = require("fs");
const path = "app/src/main/java/com/gamingtv/app/SocketManager.kt";
let raw = fs.readFileSync(path, "utf8");
const usesCRLF = raw.includes("\r\n");
let content = raw.replace(/\r\n/g, "\n");

const anchor = `    fun emitSessionEndedLocal(timeRemaining: Int) {
        socket?.emit("SESSION_ENDED_LOCAL", JSONObject().apply {
            put("tvId", tvId)
            put("timeRemaining", timeRemaining)
        })
    }`;

const addition = anchor + `

    fun emitSessionStartedLocal(startedAtIso: String, offlineTriggered: Boolean) {
        socket?.emit("SESSION_STARTED_LOCAL", JSONObject().apply {
            put("startedAt", startedAtIso)
            put("offlineTriggered", offlineTriggered)
        })
    }`;

if (!content.includes(anchor)) throw new Error("Ancre emitSessionEndedLocal introuvable");
content = content.replace(anchor, addition);

if (usesCRLF) content = content.replace(/\n/g, "\r\n");
fs.writeFileSync(path, content, "utf8");
console.log("SocketManager.kt: emitSessionStartedLocal ajoute.");