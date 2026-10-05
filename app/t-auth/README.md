# T-Auth — Your Own Google-like Authentication

A complete OAuth 2.0 authentication system with a Node.js server and Android SDK.

---

## Project Structure

```
t-auth/
├── t-auth-server/          ← Node.js OAuth 2.0 backend
│   ├── server.js           ← Entry point
│   ├── routes/
│   │   ├── auth.js         ← Register, login, refresh, logout
│   │   └── oauth.js        ← Authorize, token, userinfo, clients
│   ├── middleware/
│   │   └── authenticate.js ← JWT Bearer token verifier
│   ├── config/db.js        ← PostgreSQL connection
│   ├── db/schema.sql       ← Database tables
│   └── .env.example        ← Environment variables template
│
└── t-auth-android/         ← Android SDK + example
    ├── tauth-sdk/          ← Drop this into any Android project
    │   └── TAuthClient.kt  ← Main SDK class
    │   └── TAuthActivity.kt← OAuth WebView + callback handler
    └── app-example/
        ├── MainActivity.kt ← Example: how to use T-Auth
        ├── AndroidManifest.xml
        └── build.gradle
```

---

## Backend Setup

### 1. Install dependencies
```bash
cd t-auth-server
npm install
```

### 2. Create `.env` file
```bash
cp .env.example .env
# Fill in your DB credentials and JWT secrets
```

### 3. Create the database
```bash
psql -U postgres -c "CREATE DATABASE tauth"
psql -U postgres -d tauth -f db/schema.sql
```

### 4. Start the server
```bash
npm run dev     # development (with nodemon)
npm start       # production
```

### API Endpoints

| Method | Endpoint                  | Description                        |
|--------|---------------------------|------------------------------------|
| POST   | /auth/register            | Create account                     |
| POST   | /auth/login               | Login → access + refresh token     |
| POST   | /auth/refresh             | Refresh access token               |
| POST   | /auth/logout              | Revoke refresh token               |
| GET    | /oauth/authorize          | OAuth consent screen               |
| POST   | /oauth/approve            | Submit login on consent screen     |
| POST   | /oauth/token              | Exchange code → tokens             |
| GET    | /oauth/userinfo           | Get logged-in user profile         |
| POST   | /oauth/clients            | Register a new app (developer)     |
| GET    | /.well-known/openid-configuration | OIDC discovery document  |

---

## Android Setup

### 1. Copy the SDK module into your project
- Copy `tauth-sdk/` into your Android project root
- Add `include ':tauth-sdk'` to `settings.gradle`
- Add `implementation project(':tauth-sdk')` to your app's `build.gradle`

### 2. Add dependencies (`build.gradle`)
```groovy
implementation "androidx.security:security-crypto:1.1.0-alpha06"
implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3"
```

### 3. Add to AndroidManifest.xml
```xml
<activity android:name="com.tauth.TAuthActivity" android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="tauth" android:host="callback" />
    </intent-filter>
</activity>
```

### 4. Initialize and use in your Activity
```kotlin
// Initialize once (e.g. in Application.onCreate or first Activity)
TAuthClient.getInstance(this).init(
    serverUrl   = "https://your-t-auth-server.com",
    clientId    = "your_client_id",
    redirectUri = "tauth://callback"
)

// Launch login
TAuthActivity.launch(this, REQUEST_CODE)

// Handle result
override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    if (requestCode == REQUEST_CODE && resultCode == RESULT_OK) {
        val user = data?.getBundleExtra(TAuthActivity.EXTRA_USER)
        val email = user?.getString("email")
        // User is logged in!
    }
}

// Check login status
if (TAuthClient.getInstance(this).isLoggedIn()) { ... }

// Logout
TAuthClient.getInstance(this).logout()
```

---

## OAuth Flow (How it works end-to-end)

```
Android App                    T-Auth Server                 PostgreSQL
    |                               |                              |
    |  1. buildAuthUrl()            |                              |
    |  Opens WebView ──────────────►| GET /oauth/authorize         |
    |                               | Show login page              |
    |                               |                              |
    |  2. User enters email+pass    |                              |
    |  Submits form ───────────────►| POST /oauth/approve          |
    |                               | Verify password ────────────►|
    |                               | Create auth_code ────────────|
    |                               |◄─────────────────────────────|
    |◄── Redirect tauth://callback?code=xxx                        |
    |                               |                              |
    |  3. exchangeCode(code) ──────►| POST /oauth/token            |
    |                               | Verify code + PKCE ─────────►|
    |                               | Issue access+refresh tokens  |
    |◄── { access_token, refresh_token }                           |
    |                               |                              |
    |  4. getUserInfo() ───────────►| GET /oauth/userinfo          |
    |◄── { id, email, name, picture }                              |
```

---

## Security Features

- ✅ **PKCE** (Proof Key for Code Exchange) — prevents code interception
- ✅ **Encrypted token storage** on Android (EncryptedSharedPreferences)
- ✅ **Refresh token rotation** — new refresh token on every use
- ✅ **Short-lived access tokens** (15 min) + long-lived refresh tokens (30 days)
- ✅ **One-time authorization codes** — marked used immediately
- ✅ **Rate limiting** on auth endpoints
- ✅ **bcrypt** password hashing (cost factor 12)

---

## Deploy to Production

1. Use **HTTPS** (required for OAuth security)
2. Set strong `ACCESS_TOKEN_SECRET` and `REFRESH_TOKEN_SECRET` in `.env`
3. Use a managed PostgreSQL (Supabase, Railway, Neon)
4. Deploy server to Railway, Render, or a VPS
5. Update `serverUrl` in Android to your production URL
