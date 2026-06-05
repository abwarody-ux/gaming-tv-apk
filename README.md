# Gaming TV APK — Instructions pour le développeur

## Configuration (IMPORTANT — à faire avant de compiler)

Ouvrir le fichier : `app/src/main/java/com/gamingtv/app/Config.kt`

Modifier ces valeurs si nécessaire :
```kotlin
const val BACKEND_URL = "https://gaming-tv-backend-production.up.railway.app"
const val TOKEN = "gaming-tv-test-2025"
const val TV_ID = "TV01"  // Changer pour chaque TV installée
```

## Comment compiler l'APK

1. Ouvrir Android Studio
2. File → Open → Sélectionner ce dossier
3. Attendre la synchronisation Gradle (~2 min)
4. Build → Build Bundle(s) / APK(s) → Build APK(s)
5. Le fichier APK sera dans : `app/build/outputs/apk/debug/app-debug.apk`

## Comment installer sur la TV Android

```bash
adb connect [IP_DE_LA_TV]
adb install app-debug.apk
```

Ou via clé USB : activer "Developer Options" → "USB Debugging" sur la TV.

## Ce que fait l'APK

- Se connecte automatiquement au backend via WebSocket
- Affiche un écran d'attente jusqu'à ce qu'une session soit démarrée
- Affiche le timer en bas d'écran pendant la session
- Reçoit les commandes du backend (START, PAUSE, RESUME, END)
- Timer local de secours si connexion perdue (pause après 1 min hors ligne)
- Alerte visuelle à 5 minutes et 1 minute restantes

## Architecture

```
Backend Railway (Node.js/NestJS)
         ↕ WebSocket
    APK TV (Kotlin/Android)
```

## Support

Token d'accès : `gaming-tv-test-2025`
URL Backend : `https://gaming-tv-backend-production.up.railway.app`
