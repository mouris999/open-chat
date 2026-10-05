# T-Auth Integration Guide for OpenChat

This guide explains how to set up and use T-Auth (your custom OAuth 2.0 authentication) in the OpenChat Android app.

## Overview

T-Auth provides a complete OAuth 2.0 authentication system with:
- **PKCE** security (Proof Key for Code Exchange)
- **Encrypted token storage** on Android
- **Refresh token rotation** for security
- **Short-lived access tokens** (15 min) + long-lived refresh tokens (30 days)

## Architecture

```
┌──────────────┐         ┌──────────────┐         ┌──────────────┐
│  OpenChat    │         │   T-Auth     │         │  PostgreSQL  │
│  Android App │────────▶│   Server     │────────▶│   Database   │
└──────────────┘         └──────────────┘         └──────────────┘
       │
       │ OAuth 2.0 Flow:
       │ 1. Authorization Request (with PKCE)
       │ 2. User Login in WebView
       │ 3. Authorization Code Callback
       │ 4. Token Exchange
       │ 5. User Info Retrieval
```

## Setup Instructions

### 1. T-Auth Server Setup

First, you need to set up the T-Auth Node.js server:

```bash
# Clone or create the T-Auth server
cd t-auth-server

# Install dependencies
npm install

# Create .env file
cp .env.example .env
```

Edit `.env` with your configuration:
```env
# Database
DB_HOST=localhost
DB_PORT=5432
DB_NAME=tauth
DB_USER=postgres
DB_PASSWORD=your_password

# JWT Secrets (generate strong random strings)
ACCESS_TOKEN_SECRET=your_random_access_secret
REFRESH_TOKEN_SECRET=your_random_refresh_secret

# Server
PORT=3000
NODE_ENV=production
```

Create the database:
```bash
# Create database
psql -U postgres -c "CREATE DATABASE tauth"

# Run schema
psql -U postgres -d tauth -f db/schema.sql
```

Start the server:
```bash
npm start
```

### 2. Register OpenChat as OAuth Client

Once your T-Auth server is running, register OpenChat:

```bash
curl -X POST https://your-t-auth-server.com/oauth/clients \
  -H "Content-Type: application/json" \
  -d '{
    "name": "OpenChat Android",
    "redirectUris": ["tauth://callback"],
    "allowedGrants": ["authorization_code"]
  }'
```

Save the returned `client_id` for the next step.

### 3. Android App Configuration

Update `OpenChatApplication.kt` with your server details:

```kotlin
private fun initializeTAuth() {
    TAuthClient.getInstance(this).init(
        serverUrl = "https://your-t-auth-server.com",  // Your T-Auth server
        clientId = "YOUR_CLIENT_ID",                   // From registration
        redirectUri = "tauth://callback"               // Must match server config
    )
}
```

### 4. Usage in Login Screen

In your Login screen (composable), add a "Sign in with T-Auth" button:

```kotlin
@Composable
fun LoginScreen(
    viewModel: LoginViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as Activity
    
    // Launcher for T-Auth result
    val tAuthLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val accessToken = data?.getStringExtra(TAuthActivity.EXTRA_ACCESS_TOKEN)
            val refreshToken = data?.getStringExtra(TAuthActivity.EXTRA_REFRESH_TOKEN)
            val userBundle = data?.getBundleExtra(TAuthActivity.EXTRA_USER)
            
            viewModel.handleTAuthResult(accessToken, refreshToken, userBundle)
        }
    }
    
    Column {
        // ... other login options ...
        
        Button(
            onClick = {
                val intent = Intent(context, TAuthActivity::class.java)
                tAuthLauncher.launch(intent)
            }
        ) {
            Text("Sign in with T-Auth")
        }
    }
}
```

### 5. Handle Auth State

Check authentication status anywhere in your app:

```kotlin
// Check if user is logged in
if (TAuthClient.getInstance(context).isLoggedIn()) {
    // User is authenticated
}

// Get current user
val user = TAuthClient.getInstance(context).getCurrentUser()
val userId = user?.id
val email = user?.email

// Get access token for API calls
val token = TAuthClient.getInstance(context).getAccessToken()
```

### 6. Logout

```kotlin
// In your ViewModel or Activity
lifecycleScope.launch {
    TAuthClient.getInstance(context).logout()
    // Redirect to login screen
}
```

## API Endpoints (T-Auth Server)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | /auth/register | Create new account |
| POST | /auth/login | Login with email/password |
| POST | /auth/refresh | Refresh access token |
| POST | /auth/logout | Revoke refresh token |
| GET | /oauth/authorize | OAuth authorization page |
| POST | /oauth/approve | Submit login approval |
| POST | /oauth/token | Exchange code for tokens |
| GET | /oauth/userinfo | Get user profile |
| POST | /oauth/clients | Register OAuth client |

## Security Features

✅ **PKCE (RFC 7636)**: Prevents authorization code interception attacks
✅ **Encrypted Storage**: Tokens stored in EncryptedSharedPreferences
✅ **Token Rotation**: New refresh token issued on every use
✅ **bcrypt Hashing**: Passwords hashed with cost factor 12
✅ **Rate Limiting**: Protected auth endpoints

## Testing

1. Start your T-Auth server locally
2. Use ngrok to expose it: `ngrok http 3000`
3. Update OpenChatApplication with ngrok URL
4. Build and run the app
5. Click "Sign in with T-Auth"
6. Complete login in WebView
7. Verify successful authentication

## Troubleshooting

### "Failed to initialize T-Auth" Error
- Check that `serverUrl` is properly set in `OpenChatApplication.kt`
- Ensure the URL uses HTTPS in production
- Verify the client ID is correct

### "Authorization error" Callback
- Check that the redirect URI matches exactly in both client and server
- Verify the client is registered in the T-Auth server database

### Token Refresh Fails
- The refresh token may have expired (30 days)
- User needs to re-authenticate
- Check server logs for details

## Migration from Firebase/Supabase

To fully migrate from Firebase/Supabase to T-Auth:

1. **Update AuthRepository**: Replace Firebase calls with T-Auth server API calls
2. **Update User Model**: Sync T-Auth users with your existing user database
3. **Migrate Data**: Move existing user data to PostgreSQL
4. **Update FCM**: Keep Firebase Cloud Messaging but use T-Auth for authentication

## Production Deployment

1. **Server**: Deploy to Railway, Render, or VPS with HTTPS
2. **Database**: Use managed PostgreSQL (Supabase, Neon, AWS RDS)
3. **Environment**: Set strong JWT secrets in production
4. **SSL**: Required for OAuth security
5. **Monitoring**: Add logging and monitoring to T-Auth server

## Files Added/Modified

### New Files:
- `app/src/main/kotlin/com/openchat/app/tauth/TAuthClient.kt` - SDK client
- `app/src/main/kotlin/com/openchat/app/tauth/TAuthActivity.kt` - OAuth WebView activity
- `app/src/main/AndroidManifest.xml` - Added TAuthActivity with callback filter

### Modified Files:
- `OpenChatApplication.kt` - T-Auth initialization
- `LoginViewModel.kt` - Added T-Auth result handler
- `AuthRepository.kt` - Added signInWithTAuth method
- `AuthRepositoryImpl.kt` - T-Auth implementation

## Next Steps

1. Set up your T-Auth server
2. Register OpenChat as an OAuth client
3. Update the server URL and client ID in `OpenChatApplication.kt`
4. Build and test the authentication flow
5. Deploy to production
