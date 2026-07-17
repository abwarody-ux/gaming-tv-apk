const fs = require("fs");
const path = "app/src/main/java/com/gamingtv/app/Config.kt";
let raw = fs.readFileSync(path, "utf8");
const usesCRLF = raw.includes("\r\n");
let content = raw.replace(/\r\n/g, "\n");

const anchor = `    const val KEY_MAC_ADDRESS = "mac_address"`;
const addition = anchor + `\n    const val KEY_TV_TOKEN = "tv_token"`;
if (!content.includes(anchor)) throw new Error("Ancre KEY_MAC_ADDRESS introuvable");
content = content.replace(anchor, addition);

if (usesCRLF) content = content.replace(/\n/g, "\r\n");
fs.writeFileSync(path, content, "utf8");
console.log("Config.kt: KEY_TV_TOKEN ajoute.");