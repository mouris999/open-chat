-- T-Auth Database Schema

-- Users table
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    name VARCHAR(255),
    avatar_url VARCHAR(500),
    email_verified BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- OAuth Clients (apps that use T-Auth)
CREATE TABLE IF NOT EXISTS oauth_clients (
    client_id VARCHAR(100) PRIMARY KEY,
    client_secret VARCHAR(255) NOT NULL,
    app_name VARCHAR(255) NOT NULL,
    app_logo VARCHAR(500),
    owner_user_id UUID REFERENCES users(id),
    redirect_uris TEXT[] NOT NULL,
    scopes TEXT[] DEFAULT ARRAY['openid', 'profile', 'email'],
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Authorization codes (short-lived, one-time use)
CREATE TABLE IF NOT EXISTS auth_codes (
    code VARCHAR(255) PRIMARY KEY,
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    client_id VARCHAR(100) REFERENCES oauth_clients(client_id),
    redirect_uri VARCHAR(500),
    scope VARCHAR(500),
    code_challenge VARCHAR(255),
    code_challenge_method VARCHAR(10),
    expires_at TIMESTAMP NOT NULL,
    used BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Refresh tokens
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token VARCHAR(500) UNIQUE NOT NULL,
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    client_id VARCHAR(100) REFERENCES oauth_clients(client_id),
    scope VARCHAR(500),
    expires_at TIMESTAMP NOT NULL,
    revoked BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Seed a test client for development
INSERT INTO oauth_clients (client_id, client_secret, app_name, redirect_uris)
VALUES (
    'tauth_test_client',
    'tauth_test_secret_changeme',
    'T-Auth Test App',
    ARRAY['tauth://callback', 'http://localhost:8080/callback']
) ON CONFLICT DO NOTHING;
