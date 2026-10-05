# OpenChat — E2E Encrypted Messaging Platform

> **Android + Web + WebRTC + T-Auth OAuth + Open-Hub landing** — one mono-repo, four runnable products backed by Firebase.

| Layer | Entry point | Port | Hosted where |
|-------|-------------|------|--------------|
| **Android App** | `app/` | — | APK via `open-hub/` |
| **Web Companion** | `web/` | Firebase Hosting | `firebase.json → public: web` |
| **T-Auth OAuth Server** | `t-auth-server/server.js` | `3000` | Any Node host |
| **WebRTC Signaling Server** | `webrtc-server/server.js` | `3001` | Any Node host |
| **Open-Hub Download Landing** | `open-hub/index.html` | — | Vercel (`open-hub/dist`) |

---

## 1. What this project is

OpenChat is a **Telegram/WhatsApp-class messenger clone**:

* **Android native** client (Kotlin + Jetpack Compose) with E2EE, calls, stories, scheduled messages.
* **Web companion** (`web/`) — QR-scan to link device like WhatsApp Web, then real-time chats via Firebase RTDB + Firestore.
* **T-Auth** (`t-auth-server/` + `app/t-auth/`) — *own Google-like OAuth 2.0* (Authorization Code + PKCE, JWT, refresh rotation) used for Android login.
* **WebRTC server** (`webrtc-server/`) — Socket.IO signaling for P2P voice/video calls.
* **Open-Hub** (`open-hub/`) — minimal landing page to distribute `openchat.apk` without Play Store.

---

## 2. Architecture

```
                ┌─────────────┐
     Android    │  OpenChat   │──┐
   (Compose)    │  App 1.0    │  │  FCM / REST / WebRTC
                └─────────────┘  │
                                 ▼
┌──────────┐  QR link   ┌──────────────────────┐  RTDB/Firestore  ┌──────────┐
│ Web App  │◄──────────►│ Firebase             │◄────────────────►│  T-Auth  │
│ (web/)   │  sessions/*│ RTDB + Firestore +   │   /oauth/*       │ server   │
└──────────┘            │ Auth + Storage + FCM │                  └──────────┘
                        └──────────────────────┘
                                 ▲
                                 │ Socket.IO
                        ┌──────────────────────┐
                        │ webrtc-server        │
                        │  :3001               │
                        └──────────────────────┘
                                 ▲
                        ┌──────────────────────┐
                        │ open-hub (Vercel)    │
                        │  APK distribution    │
                        └──────────────────────┘
```

**Data flow (sending a message):**

1. Web/App → `set(ref(db, messages/{chatId}/{msgId}), payload)` → RTDB fan-out.
2. `chats/{chatId}.lastMessage` updated for list sorting.
3. Presence: `status/{uid}`, typing: `typing/{chatId}/{uid}` (TTL 2s).
4. Calls: `POST /notify/call` → FCM → `CallService` → WebRTC offer/answer via `webrtc-server`.

---

## 3. Features — exhaustive

### 3.1 Android App (`app/`)

| Area | Features |
|------|----------|
| **Messaging** | 1-1 & group chats (≤200k), channels, text/photo/video/audio/voice/docs/stickers/GIFs/polls/location/contacts, reply/forward/pin, edit & delete-for-everyone, scheduled messages, message search (full-text), chat folders/tabs, emoji reactions, swipe-to-reply |
| **Encryption** | Signal-like Double-Ratchet + X3DH placeholder, Android Keystore, HMAC-SHA256, TLS 1.3, secret chats (no server storage), self-destruct timers |
| **Presence** | Online/last-seen, typing indicators, read receipts (✓/✓✓/blue), pinned chats, mute |
| **Media** | Coil image pipeline, Media3/ExoPlayer, CameraX capture, Cloudinary upload, shared-media drawer |
| **Calls** | WebRTC P2P voice/video, group voice chats, screen share (`ScreenCapturer`, `MediaProjectionService`), call logs, waveform UI, foreground `CallService` |
| **Stories** | `StoriesScreen` + `StoryCameraScreen` (CameraX) |
| **AI / ML** | MediaPipe GenAI (Llama 3.2 on-device), MLKit translate / language-id / text-recognition |
| **Auth** | Firebase Phone OTP + Google Sign-In + **T-Auth OAuth PKCE** (`TAuthClient` + `TAuthActivity` WebView) |
| **Security UI** | App passcode + Chat lock (`AppLockSetupScreen`, `ChatLockSetupScreen`), biometric, encrypted DataStore |
| **Settings** | Theme (Material You dynamic color, dark/light/system), notifications, privacy, storage, chat wallpaper, call settings, help/about |

Screens (in `app/src/main/kotlin/com/openchat/app/presentation/screens/`): `auth/Login`, `auth/ProfileSetup`, `home/Home`, `home/ChatFolders`, `chat/Chat`, `chat/ChatSettings`, `group/CreateGroup`, `contacts/Contacts`, `call/Call`, `stories/Stories`, `stories/StoryCamera`, `media/SharedMedia`, `scheduled/ScheduledMessages`, `profile/Profile`, `settings/*` (Settings, Privacy, Notifications, Storage, CallSettings, Help, About, FontSize, ChatLock, AppLock), `lock/Lock`, `splash/Splash`.

Services: `FCMService`, `CallService`, `MediaProjectionService`, `MessageSyncService`, `BootCompletedReceiver`.

### 3.2 Web Companion (`web/`)

Single-page vanilla JS + Firebase JS SDK v10.7.1 (see §5).

* **QR linking**: generates 32-char session token → `set(sessions/{token}, {status: pending})` → QR via `qrcodejs` (`openchat://link?token=…`) → `onValue(sessions/{token})` until Android writes `authorized + authenticatedUid` → `signInAnonymously`.
* **Dashboard**: 3-panel layout (sidebar nav | chat list | chat viewport + details drawer).
* **Chats**: real-time `onValue(chats)`, participant filter, pinned first, unread badge, presence dots.
* **Messages**: `onValue(messages/{chatId})`, types: TEXT/IMAGE/VIDEO/VOICE/FILE, deleted placeholder, forwarded & reply headers, XSS-safe (`escapeHtml` + `sanitizeUrl`), linkification, reactions (`reactions/{uid}`), read ticks, voice playback, disappearing messages (`expiresAt`).
* **Compose**: Enter-to-send, typing indicator (`typing/{chatId}/{uid}`), scheduled messages (`scheduledMessages/{uid}`), media via FileReader DataURL (photo/video/doc), reply preview, emoji picker (6 reactions + copy/delete).
* **Group**: create group (RTDB + Firestore dual-write, `participants_map`), members drawer.
* **Profile**: edit displayName/bio/photoUrl → `setDoc(users/{uid}, merge)`.
* **Details drawer**: encryption banner, disappearing timer (Off/24h/7d) dual-write RTDB+Firestore, wallpaper selector (5 presets), shared media grid, members list.
* **Other**: in-conversation search with `highlight`, global chat search filter, attachments popup, scheduled list modal, help FAQ, theme toggle, logout, wallpaper theming.

### 3.3 T-Auth OAuth Server (`t-auth-server/server.js`)

Full OAuth 2.0 Authorization Code + PKCE — Google-login look-alike.

* Beautiful glassmorphism login/consent page (`generateAuthPage`) with sign-in / create-account tabs, animated gradient + orbs.
* **Endpoints**: `POST /auth/register`, `POST /auth/login`, `POST /auth/refresh` (rotation), `POST /auth/logout`, `GET /oauth/authorize` (HTML), `POST /oauth/approve` (signin), `POST /oauth/approve-register` (signup), `POST /oauth/token` (PKCE verify, 10-min TTL, one-time code), `GET /oauth/userinfo` (Bearer), `POST /oauth/clients`, `GET /callback` (debug), `GET /health`.
* Security: bcrypt(12), JWT (access 15m / refresh 30d), `ACCESS_TOKEN_SECRET` + `REFRESH_TOKEN_SECRET` from env (hard-fail if missing), rate limiter (Map per IP+path), HTML-escape (`escapeHtml`), CORS allowlist (`TAUTH_ALLOWED_ORIGINS`), XSS + PKCE (`S256`) protection.
* In-memory stores: `users`, `oauthClients`, `authCodes`, `refreshTokens` + seeded `test/test-secret` client + `test@example.com/password`.

Android SDK (`app/t-auth/`): `TAuthClient` (singleton, EncryptedSharedPreferences AES256_GCM + SIV, `buildAuthUrl` with code_challenge + state, `exchangeCode`, `getUserInfo`, `refreshToken`, `isLoggedIn`) + `TAuthActivity` (WebView OAuth, `tauth://callback` deep link) + `schema.sql` + `server.js`/`auth.js`/`oauth.js` reference split.

### 3.4 WebRTC Signaling Server (`webrtc-server/server.js`)

Socket.IO relay — no media passes through it.

* `register(userId→socketId)`, `call-initiate` → `incoming-call` or `call-failed` (User offline), `call-answer` → `call-answered`, `call-reject` → `call-rejected`, `ice-candidate` → `ice-candidate`, `call-end` → `call-ended`, `disconnect` → purge user + terminate related calls.
* `connectedUsers: Map`, `activeCalls: Map(callId→{callerId, recipientId, callType, status})`, `ALLOWED_ORIGINS` env, CORS.

### 3.5 WebServer (`server/`)

Legacy Express placeholder (Express 5, cors, jsonwebtoken, bcryptjs, twilio) — `npm start → node index.js`. Currently minimal; Twilio stub for SMS OTP if Firebase Auth is replaced.

### 3.6 Open-Hub (`open-hub/index.html` & `open-hub/dist/`)

* One-file static APK distribution page: gradient card, logo, `Download openchat.apk` button with `__APK_URL__`, `__APK_VERSION__`, `__APK_SIZE__`, `__APK_DATE__` placeholders replaced at deploy, meta row, 3-step install guide (unknown sources), Vercel footer.
* `deploy.ps1` build script, `.vercel/project.json`, `.env.local` for Vercel env.

---

## 4. Tech stack — what we used and where it came from

### 4.1 Android (`app/build.gradle.kts` + `gradle/libs.versions.toml`)

| Dependency | Version | Source |
|------------|---------|--------|
| Kotlin | 2.1.0 | kotlinlang.org |
| AGP | 8.6.0 | Google Maven |
| KSP | 2.1.0-1.0.29 | Google Maven |
| Compose BOM | 2024.02.00 | androidx.compose |
| Material 3 | — | `androidx.compose.material3` |
| Hilt | 2.57.1 | `com.google.dagger` |
| Navigation Compose | 2.7.7 | `androidx.navigation` |
| Room | 2.6.1 | `androidx.room` |
| Retrofit 2.9.0 + OkHttp 4.12.0 | — | `com.squareup` |
| Coroutines 1.7.3 | — | `org.jetbrains.kotlinx` |
| Firebase BOM 34.12.0 (Analytics 22.0.0, Database 21.0.0, Firestore 25.0.0, Storage 21.0.0, Messaging 24.0.0, Functions 21.0.0, Auth) | — | `com.google.firebase` |
| Play Services Auth 20.7.0 + Location 21.2.0 | — | `com.google.android.gms` |
| Coil 2.5.0 | — | `io.coil-kt` |
| Media3 (ExoPlayer) 1.2.1 | — | `androidx.media3` |
| CameraX 1.3.1 | — | `androidx.camera` |
| WebRTC 1.0.32006 | `org.webrtc:google-webrtc` | Maven Central (local `app/libs/libwebrtc.aar` for release) |
| Signal Protocol 2.8.1 (removed — custom impl via Keystore) | — | `org.signal` (not on Maven) |
| Paging 3.2.1 + WorkManager 2.9.0 + DataStore 1.0.0 + Security-Crypto 1.1.0-alpha06 + Biometric 1.1.0 + Splash 1.0.1 + Accompanist 0.32.0 | — | AndroidX |
| Lottie 6.3.0 | — | `com.airbnb.android` |
| ZXing Android Embedded 4.3.0 (QR scanning) | — | `com.journeyapps` |
| MediaPipe Tasks GenAI 0.10.14 (on-device Llama) | — | `com.google.mediapipe` |
| MLKit Translate 17.0.3 + Language-ID 17.0.4 + Text-Rec 19.0.0 | — | `com.google.mlkit` + `play-services-mlkit` |
| Cloudinary Android 2.5.0 | — | `com.cloudinary` |
| protobuf-javalite 3.25.1 + serialization-json 1.6.2 | — | `com.google.protobuf` |

Build: JDK 17, `compileSdk 35`, `minSdk 24`, `targetSdk 35`, `namespace com.openchat.app`, R8/ProGuard in `app/proguard-rules.pro`, `buildConfig` for `FCM_SERVICE_ACCOUNT_JSON` (base64), `CLOUDINARY_API_SECRET`, `TURN_*`.

### 4.2 Web (`web/`)

| Lib | Version / CDN | From |
|-----|---------------|------|
| Firebase Modular SDK | 10.7.1 (`firebasejs/10.7.1/firebase-app.js`, `-database.js`, `-firestore.js`, `-auth.js`) | `gstatic.com` CDN (ES modules) |
| qrcodejs | 1.0.0 | `cdnjs.cloudflare.com/qrcodejs` |
| Font Awesome | 6.4.0 | `cdnjs.cloudflare.com` |
| Outfit font | — | `fonts.googleapis.com` |
| `style.css` | custom glassmorphism | — |

No bundler — plain `type="module"` import maps, Firebase Hosting serves `web/` directly.

### 4.3 T-Auth Server (`t-auth-server/package.json`)

`express@4.18.2` + `bcrypt@5.1.1` + `jsonwebtoken@9.0.2` + `pg@8.11.3` + `dotenv@16.3.1` + `cors@2.8.5` + `uuid@9.0.0`, dev `nodemon@3.0.1`, Node ≥18.

Android SDK uses `androidx.security:security-crypto` + `kotlinx-coroutines-android` + `HttpURLConnection` (no Retrofit).

### 4.4 WebRTC Server (`webrtc-server/package.json`)

`express@4.18.2` + `socket.io@4.7.2` + `cors@2.8.5`, dev `nodemon@3.0.1`, default port `3001`.

### 4.5 Legacy Server (`server/package.json`)

`express@5.2.1` + `cors@2.8.6` + `jsonwebtoken@9.0.2` + `bcryptjs@2.4.3` + `twilio@5.3.0`.

### 4.6 Root (`package.json` + `firebase.json`)

Root: `firebase@12.14.0` (Firebase CLI hosting deploy). `firebase.json`: `hosting.public = "web"`, `database.rules = "database.rules.json"`.

---

## 5. Repository files — complete file tree

```
open-/                              ← repo root (no git; workspace: C:\Users\zack1\Downloads\open-)
├── README.md                       ← this file
├── package.json                    ← root: firebase@12.14.0 dep
├── package-lock.json
├── firebase.json                   ← { hosting:{public:"web"}, database:{rules:"database.rules.json"} }
├── .firebaserc                     ← Firebase project id: open-chat-795f6
├── database.rules.json             ← RTDB rules (sessions/messages/chats/status/typing/scheduledMessages)
├── .env                            ← local env (not committed in real proj; present here)
├── .gitignore
├── .qodo/  .kotlin/  .gradle/      ← tool caches
├── gradle/libs.versions.toml       ← central version catalog (≈ 40 libs, see §4)
├── gradle/wrapper/{gradle-wrapper.jar, gradle-wrapper.properties}
├── gradle.properties
├── gradle.properties + local.properties (TURN creds, Cloudinary secrets, SDK paths)
├── settings.gradle.kts             ← includes :app
├── build.gradle.kts                ← top-level plugin aliases
├── gradlew / gradlew.bat / debug.keystore
├── build/reports/problems/problems-report.html
├── BUG_MASTER_KNOWLEDGE_BASE.md / bugfinder.md / SECURITY_AUDIT_REPORT.md / T-AUTH-SETUP.md
│
├── app/                            ← ANDROID APP (com.openchat, com.openchat.app namespace)
│   ├── build.gradle.kts            ← 215 lines, all dependencies, buildConfig fields, buildTypes
│   ├── proguard-rules.pro
│   ├── google-services.json        ← (gitignored; download from Firebase Console)
│   ├── libs/libwebrtc.aar          ← local WebRTC binary
│   ├── service-account.json        ← FCM service account (base64 → BuildConfig)
│   ├── src/main/
│   │   ├── AndroidManifest.xml     ← permissions: INTERNET/CAMERA/RECORD_AUDIO/MODIFY_AUDIO/FOREGROUND_SERVICE/POST_NOTIFICATIONS
│   │   ├── kotlin/com/openchat/app/
│   │   │   ├── OpenChatApplication.kt (Hilt @HiltAndroidApp)
│   │   │   ├── MainActivity.kt     ← NavHost entry
│   │   │   ├── core/               ← constants, base VM, Result sealed class, utils
│   │   │   ├── data/
│   │   │   │   ├── local/          ← Room DB, DAOs, entities, pagers
│   │   │   │   ├── remote/         ← Retrofit APIs, Firebase sources
│   │   │   │   ├── repository/     ← repo impls + VoiceRecorderManager
│   │   │   │   └── crypto/         ← Keystore + Signal protocol stub
│   │   │   ├── domain/             ← use-cases, domain models, repo interfaces
│   │   │   ├── presentation/
│   │   │   │   ├── theme/{Color, Type, Theme, ThemeManager}.kt
│   │   │   │   └── screens/
│   │   │   │       ├── auth/{LoginScreen, LoginViewModel, ProfileSetupScreen, ProfileSetupViewModel}
│   │   │   │       ├── home/{HomeScreen, HomeViewModel, ChatFoldersScreen, ChatFoldersViewModel}
│   │   │   │       ├── chat/{ChatScreen, ChatViewModel, ChatSettingsScreen, ChatSettingsViewModel}
│   │   │   │       ├── group/{CreateGroupScreen, CreateGroupViewModel}
│   │   │   │       ├── contacts/{ContactsScreen, ContactsViewModel}
│   │   │   │       ├── call/{CallScreen, CallViewModel}
│   │   │   │       ├── stories/{StoriesScreen, StoriesViewModel, StoryCameraScreen, StoryCameraViewModel}
│   │   │   │       ├── media/{SharedMediaScreen, SharedMediaViewModel}
│   │   │   │       ├── scheduled/{ScheduledMessagesScreen, ScheduledMessagesViewModel}
│   │   │   │       ├── profile/{ProfileScreen, ProfileViewModel}
│   │   │   │       ├── settings/{SettingsScreen, SettingsViewModel, PrivacyScreen, NotificationsScreen, StorageScreen, CallSettingsScreen, HelpScreen, AboutScreen, FontSize, AppLockSetupScreen, AppLockSetupViewModel, ChatLockSetupScreen, ChatLockSetupViewModel}
│   │   │   │       ├── lock/{LockScreen, LockViewModel}
│   │   │   │       └── splash/{SplashScreen, SplashViewModel}
│   │   │   ├── webrtc/{WebRTCManager, ScreenCapturer, FcmSender}.kt
│   │   │   ├── service/
│   │   │   │   ├── call/{CallService, CallNotificationHelper, MediaProjectionService}.kt
│   │   │   │   ├── fcm/{FCMService, MessageNotificationHelper}.kt
│   │   │   │   ├── message/MessageSyncService.kt
│   │   │   │   └── receiver/BootCompletedReceiver.kt
│   │   │   ├── tauth/{TAuthClient, TAuthActivity}.kt  ← SDK (also duplicated in app/t-auth/)
│   │   │   └── di/{AppModule, DatabaseModule, NetworkModule, RepositoryModule, StorageModule, WebRTCModule, CloudinaryModule, CoroutineModule}.kt
│   │   └── res/
│   │       ├── values/{strings.xml, colors.xml, themes.xml}
│   │       ├── values-night/themes.xml
│   │       ├── drawable/{ic_call, ic_videocam, ic_notification, ic_sync, ic_call_*, ic_person}.xml + ic_launcher_foreground.xml
│   │       ├── layout/activity_incoming_call.xml
│   │       ├── mipmap-*/ic_launcher*.xml
│   │       └── xml/{network_security_config.xml, file_paths.xml, backup_rules.xml, data_extraction_rules.xml}
│   └── t-auth/                     ← T-AUTH SDK reference copy (also is Android module)
│       ├── README.md
│       ├── AndroidManifest.xml
│       ├── MainActivity.kt         ← example usage
│       ├── TAuthClient.kt
│       ├── TAuthActivity.kt
│       ├── auth.js / oauth.js / server.js  ← server snippet copies
│       └── schema.sql
│
├── web/                            ← WEB COMPANION (Firebase Hosting root)
│   ├── index.html                  ← 430 lines, login-card + dashboard, all modals/drawers
│   ├── app.js                      ← ~1400 lines, QR session + RTDB/Firestore sync + all UI logic
│   └── style.css                   ← glass + dark theme + blobs + mobile responsive
│
├── webrtc-server/                  ← SIGNALING SERVER (Node.js)
│   ├── server.js                   ← 149 lines, Express + Socket.IO
│   └── package.json                ← express, socket.io, cors
│
├── t-auth-server/                  ← OAUTH SERVER (Node.js)
│   ├── server.js                   ← 877 lines, Express + JWT + PKCE + HTML page generator
│   ├── package.json                ← express, bcrypt, jsonwebtoken, pg, dotenv, cors, uuid
│   ├── package-lock.json
│   ├── setup-client.js             ← registers OpenChat OAuth client
│   ├── preview.html                ← HTML page preview
│   └── README.md
│
├── server/                         ← LEGACY/PLACEHOLDER SERVER
│   ├── package.json                ← express@5, cors, jwt, bcryptjs, twilio
│   └── package-lock.json + node_modules/
│
└── open-hub/                       ← APK LANDING PAGE (Vercel)
    ├── index.html                  ← 108 lines, card + APK placeholders
    ├── dist/index.html             ← built output (same, with replaced __APK_*__)
    ├── dist/.vercel/project.json
    ├── deploy.ps1                  ← deploy script
    ├── .vercel/project.json
    └── .env.local
```

`firebase.json` maps `web/` to Hosting; `database.rules.json` enforces per-path auth. All `node_modules/` are git-ignored in practice but present locally.

---

## 6. Setup — run everything locally

### Prerequisites

* Android Studio Hedgehog 2023.1.1+ · JDK 17+ · Android SDK API 35
* Node 18+ · Firebase CLI (`npm i -g firebase-tools`)
* (Optional) PostgreSQL if you want T-Auth persisted (current `server.js` is in-memory; `pg` ready for upgrade)

### 6.1 Firebase (one-time)

1. Create project **open-chat-795f6** at https://console.firebase.google.com
2. Add Android app `com.openchat` → download `google-services.json` → `app/google-services.json`.
3. Enable: **Auth** (Phone + Google + Anonymous for web QR), **Firestore** (locked mode), **Realtime Database**, **Storage**, **Cloud Messaging**.
4. Copy web `firebaseConfig` (already in `web/app.js:6-15`) or replace with your own.

### 6.2 Web companion

```bash
firebase login
firebase deploy --only hosting   # serves web/ at https://<project>.web.app

# or local preview
npx serve web
# or firebase emulators
firebase emulators:start
```

Rules: `firebase deploy --only database` pushes `database.rules.json`.

### 6.3 T-Auth server

```bash
cd t-auth-server
npm install
# set secrets (required — server exits 1 if missing)
# Windows PowerShell:
$env:ACCESS_TOKEN_SECRET="your-32+char-secret"
$env:REFRESH_TOKEN_SECRET="your-other-32+char-secret"
# optional: $env:PORT=3000; $env:TAUTH_ALLOWED_ORIGINS="tauth://callback,http://localhost:3000"
# optional DB: $env:DB_HOST / DB_PORT / DB_NAME / DB_USER / DB_PASSWORD

npm start        # http://0.0.0.0:3000
# test:
# http://localhost:3000/health
# http://localhost:3000/oauth/authorize?client_id=test&redirect_uri=http://localhost:3000/callback&code_challenge=test
node setup-client.js   # registers OpenChat client → prints clientId/secret
```

Android: in `Application.onCreate`:
```kotlin
TAuthClient.getInstance(this).init("http://10.0.2.2:3000", "test", "tauth://callback")
```
For device: `adb reverse tcp:3000 tcp:3000`.

### 6.4 WebRTC server

```bash
cd webrtc-server
npm install
# $env:PORT=3001  (default 3001)
# $env:ALLOWED_ORIGINS="http://localhost:3000,http://localhost:8080"
npm start        # Socket.IO on :3001
# dev
npm run dev      # nodemon
```

Set `Constants.WEBRTC_SIGNALING_SERVER = "ws://10.0.2.2:3001"` in Android `core/Constants.kt`.

### 6.5 Android app

```bash
./gradlew build
./gradlew installDebug   # to emulator/device
./gradlew assembleRelease # → app/build/outputs/apk/release/app-release.apk  (minify + shrink)
```

Create `local.properties` for optional Cloudinary/TURN (see `local.properties.example`):
```properties
cloudinary.apiSecret=...
turn.username=...
turn.credential=...
sdk.dir=/path/to/Android/Sdk
```

Android Studio: Sync Gradle → Run `app`.

### 6.6 Open-Hub (Vercel)

```powershell
cd open-hub
# edit index.html __APK_URL__/__APK_VERSION__/__APK_SIZE__/__APK_DATE__
# or run dist build:
.\deploy.ps1
vercel --prod       # or push to GitHub → Vercel auto-deploy
```

`dist/index.html` is what Vercel serves; `.env.local` holds `APK_URL` etc.

### 6.7 Full local run (all at once)

```powershell
# Terminal 1
cd t-auth-server; npm start
# Terminal 2
cd webrtc-server; npm start
# Terminal 3
firebase emulators:start   # or npx serve web
# Terminal 4
./gradlew installDebug
```

---

## 7. RTDB schema & security rules

Firebase project: `open-chat-795f6` · `databaseURL: https://open-chat-795f6-default-rtdb.firebaseio.com`

`database.rules.json`:

| Path | Read | Write |
|------|------|-------|
| `sessions/$token` | `auth.uid != null \|\| !data.exists()` | `!data.exists() \|\| auth.uid != null` validate `hasChildren(['status'])` |
| `messages/$chatId` | `auth.uid != null` | `auth.uid != null` |
| `chats` | `auth.uid != null` | `auth.uid != null` |
| `status/$uid` | `auth.uid != null` | `auth.uid == $uid` |
| `typing/$chatId` | `auth.uid != null` | `auth.uid != null` |
| `scheduledMessages/$uid` | `auth.uid == $uid` | `auth.uid == $uid` |

Firestore collections used by `web/app.js` + Android: `users/{uid}`, `chats/{chatId}` (mirror of RTDB for wallpaper/disappearingTimer).

---

## 8. API reference

### T-Auth

| Method | Path | Body / Query | Auth | Returns |
|--------|------|-------------|------|---------|
| POST | `/auth/register` | `{email, password, name}` | — | `{user, token/accessToken, refreshToken, expiresIn:900}` 400/409 |
| POST | `/auth/login` | `{email, password}` | — | same 401 on fail |
| POST | `/auth/refresh` | `{refresh_token}` | — | `{accessToken, refreshToken, expiresIn}` 403 |
| POST | `/auth/logout` | `{refresh_token}` | — | `{message}` |
| GET | `/oauth/authorize` | `?client_id&redirect_uri&code_challenge&state` | — | HTML login page |
| POST | `/oauth/approve` | form `client_id,redirect_uri,code_challenge,state,email,password` | — | 302 `redirect_uri?code=&state=` or HTML error |
| POST | `/oauth/approve-register` | form `client_id,redirect_uri,code_challenge,state,email,password,name` | — | same 302 |
| POST | `/oauth/token` | form `grant_type=authorization_code&code&code_verifier&client_id&redirect_uri` | — | `{accessToken, refreshToken, expiresIn:900, tokenType:Bearer}` 400 `invalid_grant` |
| GET | `/oauth/userinfo` | — | Bearer | `{id,email,name}` 404 |
| POST | `/oauth/clients` | `{name,redirectUris,allowedGrants}` | — | `{clientId,clientSecret,name,redirectUris}` |
| GET | `/health` | — | — | `{status:ok,timestamp}` |
| GET | `/callback` | `?code&state` | — | HTML debug page (escaped) |

### WebRTC Socket.IO events

Client → Server: `register(userId)`, `call-initiate({recipientId,callType,offer,callId})`, `call-answer({callId,answer})`, `call-reject({callId})`, `ice-candidate({recipientId,candidate})`, `call-end({callId})`.
Server → Client: `incoming-call({callId,callerId,callType,offer})`, `call-answered({callId,answer})`, `call-rejected({callId})`, `ice-candidate({senderId,candidate})`, `call-ended({callId,reason})`, `call-failed({callId,reason:'User offline'})`.

---

## 9. Where things came from (sources & docs)

* **Firebase**: https://firebase.google.com/docs — RTDB, Firestore, Auth, Storage, FCM REST, Admin SDK `google-services.json` flow.
* **WebRTC**: https://webrtc.org — `org.webrtc:google-webrtc:1.0.32006`, Socket.IO signaling pattern (offer/answer/ICE).
* **T-Auth OAuth 2.0 + PKCE**: RFC 6749 + RFC 7636 (S256), JWT (RFC 7519) via `jsonwebtoken`, bcrypt via `bcrypt`.
* **Jetpack Compose + Material 3**: https://developer.android.com/jetpack/compose — Material You dynamic color, `material3`.
* **Compose BOM / Room / Hilt / Navigation / Paging / WorkManager / DataStore**: AndroidX release notes, https://developer.android.com.
* **Coil, ExoPlayer/Media3, CameraX, Lottie**: respective GitHub docs.
* **Cloudinary**: https://cloudinary.com/documentation — Android SDK `cloudinary-android`.
* **MediaPipe GenAI / MLKit**: https://ai.google.dev/edge/mediapipe + https://developers.google.com/ml-kit.
* **Socket.IO**: https://socket.io/docs/v4.
* **ZXing**: https://github.com/journeyapps/zxing-android-embedded.

No new dependency is introduced that isn't already declared in `gradle/libs.versions.toml` or `package.json`.

---

## 10. Build, test, deploy

```bash
./gradlew test                 # unit tests (JUnit + MockK + Turbine)
./gradlew connectedAndroidTest # instrumented (Espresso + Compose test)
./gradlew lint                 # Android lint
./gradlew assembleRelease      # R8 + shrink → APK
firebase deploy --only hosting,database  # web + rules
vercel --prod                  # open-hub (from open-hub/)
```

ProGuard keeps: Firebase, WebRTC, Room — see `app/proguard-rules.pro`.

---

## 11. Environment variables

| File | Key | Required |
|------|-----|----------|
| `t-auth-server/.env` | `ACCESS_TOKEN_SECRET`, `REFRESH_TOKEN_SECRET` | **yes** (exit 1 if missing) |
| | `PORT` (default 3000), `TAUTH_ALLOWED_ORIGINS` | no |
| | `DB_HOST/PORT/NAME/USER/PASSWORD`, `DATABASE_URL` | no (in-memory fallback) |
| `webrtc-server` | `PORT` (default 3001), `ALLOWED_ORIGINS` | no |
| `app/local.properties` | `cloudinary.apiSecret`, `turn.username`, `turn.credential`, `sdk.dir` | no |
| `app/service-account.json` | FCM service account JSON → `BuildConfig.FCM_SERVICE_ACCOUNT_JSON` | no |

---

## 12. Known simplifications

* T-Auth stores users in-memory (Map) — swap to `pg` + `schema.sql` for persistence.
* Web media uses DataURL (base64) — move to Firebase Storage / Cloudinary CDN for large files.
* Signal Protocol is stubbed — wire `androidx.security:security-crypto` + Double-Ratchet when ready.
* `server/` is a placeholder; real API is Firebase + T-Auth + WebRTC.

---

## License

MIT — see `app/` license headers. © 2024 OpenChat
