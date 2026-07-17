const fs = require("fs");
const path = "app/src/main/java/com/gamingtv/app/QrScannerActivity.kt";
let raw = fs.readFileSync(path, "utf8");
const usesCRLF = raw.includes("\r\n");
let content = raw.replace(/\r\n/g, "\n");

const oldBlock = `                            val data = JSONObject(bodyStr)
                            val tvId = data.optString("tv_id", "")
                            val status = data.optString("status", "")

                            val prefs = getSharedPreferences(Config.PREFS_NAME, Context.MODE_PRIVATE)
                            prefs.edit()
                                .putString(Config.KEY_TV_ID, tvId)
                                .putString(Config.KEY_TV_STATUS, status)
                                .apply()`;

const newBlock = `                            val data = JSONObject(bodyStr)
                            val tvId = data.optString("tv_id", "")
                            val status = data.optString("status", "")
                            val tvToken = data.optString("tv_token", "")

                            val prefs = getSharedPreferences(Config.PREFS_NAME, Context.MODE_PRIVATE)
                            prefs.edit()
                                .putString(Config.KEY_TV_ID, tvId)
                                .putString(Config.KEY_TV_STATUS, status)
                                .putString(Config.KEY_TV_TOKEN, tvToken)
                                .apply()`;

if (!content.includes(oldBlock)) throw new Error("Bloc activation introuvable");
content = content.replace(oldBlock, newBlock);

if (usesCRLF) content = content.replace(/\n/g, "\r\n");
fs.writeFileSync(path, content, "utf8");
console.log("QrScannerActivity.kt: tv_token stocke a l'activation.");