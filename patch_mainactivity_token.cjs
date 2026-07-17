const fs = require("fs");
const path = "app/src/main/java/com/gamingtv/app/MainActivity.kt";
let raw = fs.readFileSync(path, "utf8");
const usesCRLF = raw.includes("\r\n");
let content = raw.replace(/\r\n/g, "\n");

// 1. Imports supplementaires pour construire le POST token-by-mac
const oldImports = "import android.app.admin.DevicePolicyManager\nimport android.content.ComponentName\nimport android.content.Context";
const newImports = "import android.app.admin.DevicePolicyManager\nimport android.content.ComponentName\nimport android.content.Context";
// (deja presents depuis le patch kiosk — juste verification, pas de changement ici)
if (!content.includes(oldImports)) throw new Error("Imports existants introuvables");

const oldOkhttpUsage = 'import okhttp3.*';
const newOkhttpUsage = 'import okhttp3.*\nimport okhttp3.MediaType.Companion.toMediaType\nimport okhttp3.RequestBody.Companion.toRequestBody';
if (!content.includes(oldOkhttpUsage)) throw new Error("Import okhttp3 introuvable");
content = content.replace(oldOkhttpUsage, newOkhttpUsage);

// 2. Nouveau champ tvToken
const oldField = 'private var tvId: String = "TV00"';
const newField = 'private var tvId: String = "TV00"\n    private var tvToken: String = ""';
if (!content.includes(oldField)) throw new Error("Champ tvId introuvable");
content = content.replace(oldField, newField);

// 3. onCreate — lire le token, brancher vers connexion directe ou rattrapage
const oldOnCreate = `        // Lire le TV_ID depuis SharedPreferences
        val prefs = getSharedPreferences(Config.PREFS_NAME, Context.MODE_PRIVATE)
        tvId = prefs.getString(Config.KEY_TV_ID, "TV00") ?: "TV00"

        setupUI()
        setupSocketManager()
        setupTimerManager()
        socketManager.connect()
        loadKioskSettings()

        Log.d(TAG, "App started — TV_ID: $tvId")
    }`;

const newOnCreate = `        // Lire le TV_ID et le tv_token depuis SharedPreferences
        val prefs = getSharedPreferences(Config.PREFS_NAME, Context.MODE_PRIVATE)
        tvId = prefs.getString(Config.KEY_TV_ID, "TV00") ?: "TV00"
        tvToken = prefs.getString(Config.KEY_TV_TOKEN, "") ?: ""

        setupUI()
        setupTimerManager()
        loadKioskSettings()

        if (tvToken.isNotEmpty()) {
            setupSocketManager()
            socketManager.connect()
        } else {
            // TV activee avant le correctif de securite — rattrapage via mac_address deja connue
            ensureTvTokenThenConnect(prefs)
        }

        Log.d(TAG, "App started — TV_ID: \$tvId")
    }

    private fun ensureTvTokenThenConnect(prefs: android.content.SharedPreferences) {
        val mac = prefs.getString(Config.KEY_MAC_ADDRESS, null)
        if (mac.isNullOrEmpty()) {
            Log.e(TAG, "Impossible de recuperer un tv_token: mac_address inconnue localement")
            return
        }
        val json = JSONObject().apply { put("mac_address", mac) }
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("\${Config.BACKEND_URL}/kasmok/tv-registry/token-by-mac")
            .post(body)
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Rattrapage tv_token echoue (reseau): \${e.message}")
            }
            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string() ?: return
                if (!response.isSuccessful) {
                    Log.e(TAG, "Rattrapage tv_token echoue: \$bodyStr")
                    return
                }
                try {
                    val data = JSONObject(bodyStr)
                    val newToken = data.optString("tv_token", "")
                    if (newToken.isEmpty()) return
                    tvToken = newToken
                    prefs.edit().putString(Config.KEY_TV_TOKEN, newToken).apply()
                    mainHandler.post {
                        setupSocketManager()
                        socketManager.connect()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Erreur parsing rattrapage tv_token: \${e.message}")
                }
            }
        })
    }`;

if (!content.includes(oldOnCreate)) throw new Error("Bloc onCreate introuvable");
content = content.replace(oldOnCreate, newOnCreate);

// 4. setupSocketManager — passer le tvToken au constructeur
const oldSetup = "socketManager = SocketManager(\n            tvId = tvId,";
const newSetup = "socketManager = SocketManager(\n            tvId = tvId,\n            tvToken = tvToken,";
if (!content.includes(oldSetup)) throw new Error("Bloc setupSocketManager introuvable");
content = content.replace(oldSetup, newSetup);

if (usesCRLF) content = content.replace(/\n/g, "\r\n");
fs.writeFileSync(path, content, "utf8");
console.log("MainActivity.kt: gestion complete du tv_token en place.");