const fs = require('fs');
let content = fs.readFileSync('./app/src/main/java/com/gamingtv/app/SplashActivity.kt', 'utf8');

content = content.replace(
  /private fun getMacAddress\(\): String \{[\s\S]*?return try \{[\s\S]*?\} catch \(e: Exception\) \{[\s\S]*?"02:00:00:00:00:00"[\s\S]*?\}\s*\}/,
  `private fun getMacAddress(): String {
        // Essai 1 : interfaces réseau
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val mac = iface.hardwareAddress
                if (mac != null && mac.size == 6) {
                    val macStr = mac.joinToString(":") { "%02X".format(it) }
                    if (macStr != "02:00:00:00:00:00" && macStr != "00:00:00:00:00:00") {
                        return macStr
                    }
                }
            }
        } catch (e: Exception) { }
        // Fallback : ANDROID_ID
        val androidId = android.provider.Settings.Secure.getString(
            contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        )
        if (!androidId.isNullOrEmpty() && androidId != "9774d56d682e549c") {
            return "ANDROID-" + androidId.uppercase()
        }
        return "UNKNOWN-" + System.currentTimeMillis()
    }`
);

fs.writeFileSync('./app/src/main/java/com/gamingtv/app/SplashActivity.kt', content, { encoding: 'utf8' });
console.log('ANDROID_ID occurrences:', (content.match(/ANDROID_ID/g) || []).length);