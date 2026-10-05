# T-Auth - Custom OAuth Authentication Server

T-Auth is a custom OAuth 2.0 authentication server built for OpenChat, inspired by Google's authentication system.

## Features

- 🔐 OAuth 2.0 Authorization Code Flow with PKCE
- 👥 User Registration and Login
- 🔑 JWT Access and Refresh Tokens
- 🛡️ Rate Limiting and Security
- 🎨 Beautiful, responsive web interface
- 📱 Mobile-optimized OAuth flow
- 🗄️ PostgreSQL database with connection pooling

## Quick Start

### 1. Set up PostgreSQL Database

**Option A: Using Docker (Recommended)**
```bash
cd t-auth-server
docker-compose up -d
```

**Option B: Local PostgreSQL**
- Install PostgreSQL
- Create database: `CREATE DATABASE tauth;`

### 2. Configure Environment

Copy the example environment file:
```bash
cp .env.example .env
```

Update the database credentials in `.env`:
```env
DB_HOST=localhost
DB_PORT=5432
DB_NAME=tauth
DB_USER=postgres
DB_PASSWORD=your_password
```

### 3. Install Dependencies

```bash
npm install
```

### 4. Start the Server

```bash
npm start
```

The server will be available at `http://localhost:3000`

### 5. Register OAuth Client

Run the setup script to register OpenChat as a client:
```bash
node setup-client.js
```

This will output the client ID and secret. Update your Android app configuration.

## API Endpoints

### Authentication
- `POST /auth/register` - Register new user
- `POST /auth/login` - Login user
- `POST /auth/refresh` - Refresh access token
- `POST /auth/logout` - Logout user

### OAuth 2.0
- `GET /oauth/authorize` - Authorization endpoint (serves login page)
- `POST /oauth/approve` - Approve authorization (signin)
- `POST /oauth/approve-register` - Approve with registration (signup)
- `POST /oauth/token` - Token exchange
- `GET /oauth/userinfo` - Get user info

### Management
- `POST /oauth/clients` - Register OAuth client
- `GET /health` - Health check

## Android Integration

The Android app includes a complete T-Auth SDK:

1. **TAuthClient** - Handles OAuth flow and token management
2. **TAuthActivity** - WebView-based OAuth authorization
3. **Encrypted Storage** - Secure token storage

### Usage Example

```kotlin
// Initialize (in Application.onCreate)
TAuthClient.getInstance(context).init(
    serverUrl = "http://your-server:3000",
    clientId = "your_client_id",
    redirectUri = "tauth://callback"
)

// Launch login
TAuthActivity.launch(activity, REQUEST_CODE)

// Handle result
override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    if (requestCode == REQUEST_CODE && resultCode == Activity.RESULT_OK) {
        val user = data?.getBundleExtra(TAuthActivity.EXTRA_USER)
        // User logged in successfully
    }
}
```

## Security Features

- **PKCE** - Proof Key for Code Exchange
- **Rate Limiting** - Prevents abuse
- **Password Hashing** - bcrypt with salt rounds
- **JWT Tokens** - Short-lived access tokens with refresh tokens
- **Encrypted Storage** - Android EncryptedSharedPreferences
- **CORS Protection** - Configured for web security

## Development

### Project Structure

```
t-auth-server/
├── server.js          # Main server file
├── package.json       # Dependencies
├── .env               # Environment configuration
├── .env.example       # Environment template
├── docker-compose.yml # Database setup
└── setup-client.js    # Client registration script
```

### Environment Variables

- `DB_HOST` - PostgreSQL host
- `DB_PORT` - PostgreSQL port
- `DB_NAME` - Database name
- `DB_USER` - Database user
- `DB_PASSWORD` - Database password
- `ACCESS_TOKEN_SECRET` - JWT access token secret
- `REFRESH_TOKEN_SECRET` - JWT refresh token secret
- `PORT` - Server port (default: 3000)

## License

This project is part of OpenChat and follows the same licensing terms.