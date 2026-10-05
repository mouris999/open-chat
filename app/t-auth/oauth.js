const express  = require('express');
const jwt      = require('jsonwebtoken');
const crypto   = require('crypto');
const { v4: uuidv4 } = require('uuid');
const pool     = require('../config/db');
const authenticate = require('../middleware/authenticate');
const router   = express.Router();

// ─── GET /oauth/authorize ─────────────────────────────────────────────────────
// Step 1: Android app opens this URL in a browser/WebView
// User logs in → T-Auth redirects back with ?code=xxx
router.get('/authorize', async (req, res) => {
  const {
    client_id,
    redirect_uri,
    response_type,
    scope = 'openid profile email',
    state,
    code_challenge,
    code_challenge_method
  } = req.query;

  // --- Validate client ---
  if (!client_id || !redirect_uri || response_type !== 'code') {
    return res.status(400).send('Invalid request parameters');
  }

  try {
    const clientResult = await pool.query(
      'SELECT * FROM oauth_clients WHERE client_id = $1 AND is_active = true',
      [client_id]
    );

    if (!clientResult.rows.length) {
      return res.status(400).send('Unknown client_id');
    }

    const client = clientResult.rows[0];

    if (!client.redirect_uris.includes(redirect_uri)) {
      return res.status(400).send('redirect_uri not allowed for this client');
    }

    // Serve the login/consent HTML page
    // In production this would be a proper React page
    res.send(buildLoginPage({ client_id, redirect_uri, scope, state, code_challenge, code_challenge_method, app_name: client.app_name }));

  } catch (err) {
    console.error('Authorize error:', err);
    res.status(500).send('Server error');
  }
});

// ─── POST /oauth/approve ──────────────────────────────────────────────────────
// Called when user submits the login form on the consent screen
router.post('/approve', async (req, res) => {
  const { email, password, client_id, redirect_uri, scope, state, code_challenge, code_challenge_method } = req.body;

  try {
    const bcrypt = require('bcryptjs');
    const userResult = await pool.query('SELECT * FROM users WHERE email = $1', [email.toLowerCase()]);
    const user = userResult.rows[0];

    if (!user || !(await bcrypt.compare(password, user.password_hash))) {
      // Re-render login with error
      const clientResult = await pool.query('SELECT app_name FROM oauth_clients WHERE client_id = $1', [client_id]);
      const app_name = clientResult.rows[0]?.app_name || client_id;
      return res.status(401).send(buildLoginPage({ client_id, redirect_uri, scope, state, code_challenge, code_challenge_method, app_name, error: 'Invalid email or password' }));
    }

    // Generate authorization code
    const code = crypto.randomBytes(32).toString('hex');
    const expiresAt = new Date(Date.now() + 5 * 60 * 1000); // 5 minutes

    await pool.query(
      `INSERT INTO auth_codes (code, user_id, client_id, redirect_uri, scope, code_challenge, code_challenge_method, expires_at)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8)`,
      [code, user.id, client_id, redirect_uri, scope, code_challenge || null, code_challenge_method || null, expiresAt]
    );

    // Redirect back to Android app with the code
    const redirectUrl = new URL(redirect_uri);
    redirectUrl.searchParams.set('code', code);
    if (state) redirectUrl.searchParams.set('state', state);

    res.redirect(redirectUrl.toString());

  } catch (err) {
    console.error('Approve error:', err);
    res.status(500).send('Server error');
  }
});

// ─── POST /oauth/token ────────────────────────────────────────────────────────
// Step 2: Android app exchanges code for access_token
router.post('/token', async (req, res) => {
  const { grant_type, code, redirect_uri, client_id, client_secret, code_verifier, refresh_token } = req.body;

  try {
    // --- Refresh token grant ---
    if (grant_type === 'refresh_token') {
      const result = await pool.query(
        `SELECT rt.*, u.email, u.name FROM refresh_tokens rt
         JOIN users u ON rt.user_id = u.id
         WHERE rt.token = $1 AND rt.revoked = false AND rt.expires_at > NOW()`,
        [refresh_token]
      );
      if (!result.rows.length) return res.status(401).json({ error: 'invalid_grant' });
      const row = result.rows[0];
      await pool.query('UPDATE refresh_tokens SET revoked = true WHERE token = $1', [refresh_token]);
      return res.json(await issueTokens(row.user_id, row.email, row.name, client_id, row.scope));
    }

    // --- Authorization code grant ---
    if (grant_type !== 'authorization_code') {
      return res.status(400).json({ error: 'unsupported_grant_type' });
    }

    // Validate client
    const clientResult = await pool.query(
      'SELECT * FROM oauth_clients WHERE client_id = $1',
      [client_id]
    );
    if (!clientResult.rows.length) return res.status(401).json({ error: 'invalid_client' });
    const client = clientResult.rows[0];

    // For confidential clients, verify secret
    if (client_secret && client.client_secret !== client_secret) {
      return res.status(401).json({ error: 'invalid_client' });
    }

    // Validate code
    const codeResult = await pool.query(
      `SELECT * FROM auth_codes WHERE code = $1 AND used = false AND expires_at > NOW()`,
      [code]
    );
    if (!codeResult.rows.length) return res.status(400).json({ error: 'invalid_grant', error_description: 'Code invalid or expired' });

    const authCode = codeResult.rows[0];

    if (authCode.client_id !== client_id || authCode.redirect_uri !== redirect_uri) {
      return res.status(400).json({ error: 'invalid_grant' });
    }

    // Verify PKCE (code_verifier) if required
    if (authCode.code_challenge) {
      if (!code_verifier) return res.status(400).json({ error: 'invalid_grant', error_description: 'code_verifier required' });
      const hash = crypto.createHash('sha256').update(code_verifier).digest('base64url');
      if (hash !== authCode.code_challenge) {
        return res.status(400).json({ error: 'invalid_grant', error_description: 'PKCE verification failed' });
      }
    }

    // Mark code as used (one-time use)
    await pool.query('UPDATE auth_codes SET used = true WHERE code = $1', [code]);

    // Get user info
    const userResult = await pool.query('SELECT id, email, name FROM users WHERE id = $1', [authCode.user_id]);
    const user = userResult.rows[0];

    res.json(await issueTokens(user.id, user.email, user.name, client_id, authCode.scope));

  } catch (err) {
    console.error('Token error:', err);
    res.status(500).json({ error: 'server_error' });
  }
});

// ─── GET /oauth/userinfo ──────────────────────────────────────────────────────
// Step 3: Android app calls this with the access token to get user profile
router.get('/userinfo', authenticate, async (req, res) => {
  try {
    const result = await pool.query(
      'SELECT id, email, name, avatar_url, email_verified, created_at FROM users WHERE id = $1',
      [req.user.sub]
    );
    if (!result.rows.length) return res.status(404).json({ error: 'user_not_found' });

    const user = result.rows[0];
    res.json({
      sub:            user.id,
      email:          user.email,
      name:           user.name,
      picture:        user.avatar_url,
      email_verified: user.email_verified,
      created_at:     user.created_at,
    });
  } catch (err) {
    console.error('UserInfo error:', err);
    res.status(500).json({ error: 'server_error' });
  }
});

// ─── GET /oauth/clients ───────────────────────────────────────────────────────
// Register a new OAuth client (app developer endpoint - should be protected)
router.post('/clients', authenticate, async (req, res) => {
  const { app_name, redirect_uris, app_logo } = req.body;
  if (!app_name || !redirect_uris?.length) {
    return res.status(400).json({ error: 'app_name and redirect_uris required' });
  }
  try {
    const client_id     = 'tauth_' + crypto.randomBytes(8).toString('hex');
    const client_secret = crypto.randomBytes(32).toString('hex');

    await pool.query(
      `INSERT INTO oauth_clients (client_id, client_secret, app_name, app_logo, owner_user_id, redirect_uris)
       VALUES ($1, $2, $3, $4, $5, $6)`,
      [client_id, client_secret, app_name, app_logo || null, req.user.sub, redirect_uris]
    );

    res.status(201).json({ client_id, client_secret, app_name, redirect_uris });
  } catch (err) {
    console.error('Client register error:', err);
    res.status(500).json({ error: 'server_error' });
  }
});

// ─── Helper: issue access + refresh tokens ────────────────────────────────────
async function issueTokens(userId, email, name, clientId, scope) {
  const accessToken = jwt.sign(
    { sub: userId, email, name, scope, client_id: clientId },
    process.env.ACCESS_TOKEN_SECRET,
    { expiresIn: process.env.ACCESS_TOKEN_EXPIRES || '15m' }
  );

  const refreshToken = uuidv4() + '.' + uuidv4();
  const expiresAt = new Date();
  expiresAt.setDate(expiresAt.getDate() + 30);

  await pool.query(
    `INSERT INTO refresh_tokens (token, user_id, client_id, scope, expires_at)
     VALUES ($1, $2, $3, $4, $5)`,
    [refreshToken, userId, clientId, scope, expiresAt]
  );

  return {
    access_token:  accessToken,
    refresh_token: refreshToken,
    token_type:    'Bearer',
    expires_in:    900,
    scope,
  };
}

// ─── Helper: build the login HTML page ───────────────────────────────────────
function buildLoginPage({ client_id, redirect_uri, scope, state, code_challenge, code_challenge_method, app_name, error }) {
  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Login with T-Auth</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; background: #f0f4ff; display: flex; align-items: center; justify-content: center; min-height: 100vh; }
    .card { background: white; border-radius: 16px; padding: 40px; width: 100%; max-width: 400px; box-shadow: 0 4px 24px rgba(0,0,0,0.08); }
    .logo { text-align: center; margin-bottom: 8px; font-size: 32px; font-weight: 800; color: #4F46E5; }
    .subtitle { text-align: center; color: #6B7280; margin-bottom: 8px; font-size: 14px; }
    .app-name { text-align: center; font-weight: 600; color: #111827; margin-bottom: 28px; font-size: 16px; }
    .error { background: #FEF2F2; border: 1px solid #FECACA; color: #DC2626; padding: 12px; border-radius: 8px; margin-bottom: 16px; font-size: 14px; }
    label { display: block; font-size: 14px; font-weight: 500; color: #374151; margin-bottom: 6px; }
    input { width: 100%; padding: 12px 14px; border: 1px solid #D1D5DB; border-radius: 8px; font-size: 15px; margin-bottom: 16px; outline: none; transition: border 0.2s; }
    input:focus { border-color: #4F46E5; box-shadow: 0 0 0 3px rgba(79,70,229,0.1); }
    button { width: 100%; padding: 13px; background: #4F46E5; color: white; border: none; border-radius: 8px; font-size: 16px; font-weight: 600; cursor: pointer; }
    button:hover { background: #4338CA; }
    .scopes { background: #F9FAFB; border-radius: 8px; padding: 12px 16px; margin-bottom: 24px; font-size: 13px; color: #6B7280; }
    .scopes span { display: inline-block; background: #EEF2FF; color: #4F46E5; padding: 2px 8px; border-radius: 4px; margin: 2px; }
  </style>
</head>
<body>
  <div class="card">
    <div class="logo">T-Auth</div>
    <div class="subtitle">Sign in to continue to</div>
    <div class="app-name">${app_name}</div>
    ${error ? `<div class="error">${error}</div>` : ''}
    <form method="POST" action="/oauth/approve">
      <input type="hidden" name="client_id" value="${client_id}">
      <input type="hidden" name="redirect_uri" value="${redirect_uri}">
      <input type="hidden" name="scope" value="${scope}">
      <input type="hidden" name="state" value="${state || ''}">
      <input type="hidden" name="code_challenge" value="${code_challenge || ''}">
      <input type="hidden" name="code_challenge_method" value="${code_challenge_method || ''}">
      <label>Email</label>
      <input type="email" name="email" placeholder="you@example.com" required autofocus>
      <label>Password</label>
      <input type="password" name="password" placeholder="••••••••" required>
      <div class="scopes">
        Permissions requested: ${scope.split(' ').map(s => `<span>${s}</span>`).join('')}
      </div>
      <button type="submit">Continue with T-Auth</button>
    </form>
  </div>
</body>
</html>`;
}

module.exports = router;
