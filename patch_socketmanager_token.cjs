const fs = require("fs");
const path = "app/src/main/java/com/gamingtv/app/SocketManager.kt";
let raw = fs.readFileSync(path, "utf8");
const usesCRLF = raw.includes("\r\n");
let content = raw.replace(/\r\n/g, "\n");

// 1. Constructeur — ajouter tvToken
const oldCtor = "class SocketManager(\n    private val tvId: String,";
const newCtor = "class SocketManager(\n    private val tvId: String,\n    private val tvToken: String,";
if (!content.includes(oldCtor)) throw new Error("Constructeur introuvable");
content = content.replace(oldCtor, newCtor);

// 2. Query string — utiliser tvToken au lieu de Config.TOKEN
const oldQuery = 'query = "role=tv&tvId=${tvId}&token=${Config.TOKEN}"';
const newQuery = 'query = "role=tv&tvId=${tvId}&token=${tvToken}"';
if (!content.includes(oldQuery)) throw new Error("Query string introuvable");
content = content.replace(oldQuery, newQuery);

if (usesCRLF) content = content.replace(/\n/g, "\r\n");
fs.writeFileSync(path, content, "utf8");
console.log("SocketManager.kt: utilise desormais le vrai tv_token.");