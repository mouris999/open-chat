const express = require('express');
const bcrypt = require('bcrypt');
const jwt = require('jsonwebtoken');
const crypto = require('crypto');
const cors = require('cors');
require('dotenv').config();

const ALLOWED_ORIGINS = process.env.TAUTH_ALLOWED_ORIGINS
    ? process.env.TAUTH_ALLOWED_ORIGINS.split(',')
    : ['tauth://callback', 'http://localhost:3000', 'http://localhost:8080', 'https://open-chat-795f6.web.app', 'https://open-chat-795f6.firebaseapp.com'];

const app = express();

// ── Middleware ────────────────────────────────────────────────────────────────
app.use(cors({ origin: ALLOWED_ORIGINS }));
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// Simple in-memory rate limiter
const rateLimitMap = new Map();
function rateLimit(windowMs = 60000, maxRequests = 20) {
  return (req, res, next) => {
    const key = req.ip + req.path;
    const now = Date.now();
    const entry = rateLimitMap.get(key) || { count: 0, resetAt: now + windowMs };
    if (now > entry.resetAt) {
      entry.count = 0;
      entry.resetAt = now + windowMs;
    }
    entry.count++;
    rateLimitMap.set(key, entry);
    if (entry.count > maxRequests) {
      return res.status(429).json({ error: 'Too many requests. Please try again later.' });
    }
    next();
  };
}

// JWT secrets — must be set via environment variables
const ACCESS_TOKEN_SECRET = process.env.ACCESS_TOKEN_SECRET;
const REFRESH_TOKEN_SECRET = process.env.REFRESH_TOKEN_SECRET;
if (!ACCESS_TOKEN_SECRET || !REFRESH_TOKEN_SECRET) {
    console.error('FATAL: ACCESS_TOKEN_SECRET and REFRESH_TOKEN_SECRET environment variables must be set');
    process.exit(1);
}

// In-memory storage
const users = new Map();
const oauthClients = new Map();
const authCodes = new Map();
const refreshTokens = new Map();

// Initialize test data
(async () => {
  // Create test client
  oauthClients.set('test', {
    id: 'test-client-id',
    clientId: 'test',
    clientSecret: 'test-secret',
    name: 'Test Client',
    redirectUris: ['tauth://callback', 'http://192.168.1.8:3000/callback'],
    allowedGrants: ['authorization_code']
  });

  // Create test user
  const hashedPassword = await bcrypt.hash('password', 12);
  users.set('test@example.com', {
    id: 'test-user-id',
    email: 'test@example.com',
    passwordHash: hashedPassword,
    name: 'Test User'
  });

  console.log('✓ Test data initialized');
})();

// ── Auth Middleware ───────────────────────────────────────────────────────────
function authenticateToken(req, res, next) {
  const authHeader = req.headers['authorization'];
  const token = authHeader && authHeader.split(' ')[1];
  
  if (!token) return res.sendStatus(401);
  
  jwt.verify(token, ACCESS_TOKEN_SECRET, (err, user) => {
    if (err) return res.sendStatus(403);
    req.user = user;
    next();
  });
}

// ── Helper: HTML-escape user-controlled values ──────────────────────────────
function escapeHtml(str) {
  if (!str) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#x27;');
}

// ── Helper: Generate the styled OAuth HTML page ─────────────────────────────
function generateAuthPage({ client_id, redirect_uri, code_challenge, state, error, mode }) {
  const currentMode = mode || 'signin';
  const safeClientId = escapeHtml(client_id);
  const safeRedirectUri = escapeHtml(redirect_uri);
  const safeCodeChallenge = escapeHtml(code_challenge);
  const safeState = escapeHtml(state);
  const errorHtml = error ? `<div class="error-banner" id="error-banner">${escapeHtml(error)}</div>` : '';
  
  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Sign In — T-Auth</title>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap" rel="stylesheet">
  <style>
    *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
    
    :root {
      --bg-primary: #0a0e1a;
      --bg-card: rgba(16, 22, 42, 0.85);
      --bg-input: rgba(255, 255, 255, 0.06);
      --border: rgba(255, 255, 255, 0.08);
      --border-focus: rgba(99, 132, 255, 0.5);
      --text-primary: #e8ecf4;
      --text-secondary: #8892a8;
      --text-muted: #5a6480;
      --accent: #6384ff;
      --accent-hover: #7b9aff;
      --accent-glow: rgba(99, 132, 255, 0.25);
      --error: #ff4d6a;
      --error-bg: rgba(255, 77, 106, 0.12);
      --success: #34d399;
    }
    
    body {
      font-family: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
      background: var(--bg-primary);
      color: var(--text-primary);
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      overflow: hidden;
    }
    
    /* Animated background */
    .bg-gradient {
      position: fixed;
      inset: 0;
      z-index: 0;
      background: 
        radial-gradient(ellipse 80% 60% at 20% 10%, rgba(99, 132, 255, 0.12) 0%, transparent 60%),
        radial-gradient(ellipse 60% 80% at 80% 90%, rgba(139, 92, 246, 0.08) 0%, transparent 60%),
        radial-gradient(ellipse 50% 50% at 50% 50%, rgba(6, 182, 212, 0.05) 0%, transparent 70%);
      animation: gradientShift 12s ease-in-out infinite alternate;
    }
    @keyframes gradientShift {
      0% { opacity: 0.8; transform: scale(1); }
      100% { opacity: 1; transform: scale(1.05); }
    }
    
    /* Floating orbs */
    .orb {
      position: fixed;
      border-radius: 50%;
      filter: blur(80px);
      z-index: 0;
      animation: float 8s ease-in-out infinite;
    }
    .orb-1 { width: 300px; height: 300px; background: rgba(99,132,255,0.08); top: -80px; left: -60px; }
    .orb-2 { width: 250px; height: 250px; background: rgba(139,92,246,0.06); bottom: -60px; right: -40px; animation-delay: -4s; }
    @keyframes float {
      0%, 100% { transform: translateY(0px); }
      50% { transform: translateY(-20px); }
    }
    
    .auth-container {
      position: relative;
      z-index: 1;
      width: 100%;
      max-width: 420px;
      padding: 16px;
    }
    
    .auth-card {
      background: var(--bg-card);
      border: 1px solid var(--border);
      border-radius: 20px;
      padding: 40px 32px 32px;
      backdrop-filter: blur(40px);
      -webkit-backdrop-filter: blur(40px);
      box-shadow: 
        0 0 0 1px rgba(255,255,255,0.03) inset,
        0 20px 60px rgba(0,0,0,0.4),
        0 0 80px rgba(99,132,255,0.05);
      animation: cardIn 0.6s cubic-bezier(0.16, 1, 0.3, 1);
    }
    @keyframes cardIn {
      from { opacity: 0; transform: translateY(20px) scale(0.97); }
      to { opacity: 1; transform: translateY(0) scale(1); }
    }
    
    .logo-section {
      text-align: center;
      margin-bottom: 28px;
    }
    .logo-icon {
      width: 48px;
      height: 48px;
      border-radius: 14px;
      background: linear-gradient(135deg, var(--accent), #8b5cf6);
      display: inline-flex;
      align-items: center;
      justify-content: center;
      margin-bottom: 16px;
      box-shadow: 0 8px 24px var(--accent-glow);
    }
    .logo-icon svg { width: 24px; height: 24px; fill: white; }
    .logo-title {
      font-size: 22px;
      font-weight: 700;
      letter-spacing: -0.3px;
      margin-bottom: 6px;
    }
    .logo-subtitle {
      font-size: 13px;
      color: var(--text-secondary);
      line-height: 1.5;
    }
    
    /* Tabs */
    .tab-row {
      display: flex;
      gap: 4px;
      padding: 4px;
      background: var(--bg-input);
      border-radius: 12px;
      margin-bottom: 24px;
    }
    .tab-btn {
      flex: 1;
      padding: 10px 0;
      border: none;
      border-radius: 10px;
      background: transparent;
      color: var(--text-muted);
      font-family: inherit;
      font-size: 13px;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.25s ease;
    }
    .tab-btn:hover { color: var(--text-secondary); }
    .tab-btn.active {
      background: rgba(99,132,255,0.15);
      color: var(--accent);
      box-shadow: 0 2px 8px rgba(99,132,255,0.1);
    }
    
    /* Form */
    .form-group { margin-bottom: 16px; }
    .form-label {
      display: block;
      font-size: 12px;
      font-weight: 600;
      color: var(--text-secondary);
      margin-bottom: 6px;
      letter-spacing: 0.4px;
      text-transform: uppercase;
    }
    .form-input {
      width: 100%;
      padding: 12px 16px;
      border: 1px solid var(--border);
      border-radius: 12px;
      background: var(--bg-input);
      color: var(--text-primary);
      font-family: inherit;
      font-size: 15px;
      outline: none;
      transition: all 0.25s ease;
    }
    .form-input::placeholder { color: var(--text-muted); }
    .form-input:focus {
      border-color: var(--border-focus);
      background: rgba(99,132,255,0.04);
      box-shadow: 0 0 0 3px var(--accent-glow);
    }
    .form-input.error { border-color: var(--error); }
    
    .field-error {
      font-size: 11px;
      color: var(--error);
      margin-top: 4px;
      display: none;
    }
    .field-error.visible { display: block; }
    
    .submit-btn {
      width: 100%;
      padding: 14px 0;
      border: none;
      border-radius: 12px;
      background: linear-gradient(135deg, var(--accent), #8b5cf6);
      color: white;
      font-family: inherit;
      font-size: 15px;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.3s ease;
      margin-top: 8px;
      position: relative;
      overflow: hidden;
    }
    .submit-btn:hover {
      transform: translateY(-1px);
      box-shadow: 0 8px 24px var(--accent-glow);
    }
    .submit-btn:active { transform: translateY(0); }
    .submit-btn:disabled {
      opacity: 0.5;
      cursor: not-allowed;
      transform: none;
      box-shadow: none;
    }
    .submit-btn .spinner {
      display: none;
      width: 18px;
      height: 18px;
      border: 2px solid rgba(255,255,255,0.3);
      border-top-color: white;
      border-radius: 50%;
      animation: spin 0.6s linear infinite;
      margin: 0 auto;
    }
    .submit-btn.loading .btn-text { display: none; }
    .submit-btn.loading .spinner { display: inline-block; }
    @keyframes spin { to { transform: rotate(360deg); } }
    
    .error-banner {
      padding: 12px 16px;
      border-radius: 12px;
      background: var(--error-bg);
      border: 1px solid rgba(255,77,106,0.2);
      color: var(--error);
      font-size: 13px;
      margin-bottom: 20px;
      animation: shake 0.4s ease;
    }
    @keyframes shake {
      0%, 100% { transform: translateX(0); }
      25% { transform: translateX(-4px); }
      75% { transform: translateX(4px); }
    }
    
    .footer-text {
      text-align: center;
      font-size: 11px;
      color: var(--text-muted);
      margin-top: 24px;
      line-height: 1.6;
    }
    .footer-text a { color: var(--text-secondary); text-decoration: none; }
    .footer-text a:hover { color: var(--accent); }
    
    /* Transitions between forms */
    .form-panel { display: none; }
    .form-panel.active { display: block; animation: fadeIn 0.3s ease; }
    @keyframes fadeIn { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: translateY(0); } }
    
    /* Password strength indicator */
    .password-strength {
      height: 3px;
      border-radius: 2px;
      margin-top: 6px;
      background: var(--border);
      overflow: hidden;
    }
    .password-strength-bar {
      height: 100%;
      border-radius: 2px;
      width: 0%;
      transition: all 0.3s ease;
    }
    .strength-weak { width: 33%; background: var(--error); }
    .strength-medium { width: 66%; background: #f59e0b; }
    .strength-strong { width: 100%; background: var(--success); }
  </style>
</head>
<body>
  <div class="bg-gradient"></div>
  <div class="orb orb-1"></div>
  <div class="orb orb-2"></div>
  
  <div class="auth-container">
    <div class="auth-card">
      <div class="logo-section">
        <div class="logo-icon">
          <svg viewBox="0 0 24 24"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z"/></svg>
        </div>
        <div class="logo-title">T-Auth</div>
        <div class="logo-subtitle">Authorize <strong>OpenChat</strong> to access your account</div>
      </div>
      
      ${errorHtml}
      
      <div class="tab-row">
        <button class="tab-btn ${currentMode === 'signin' ? 'active' : ''}" id="tab-signin" onclick="switchMode('signin')">Sign In</button>
        <button class="tab-btn ${currentMode === 'signup' ? 'active' : ''}" id="tab-signup" onclick="switchMode('signup')">Create Account</button>
      </div>
      
      <!-- Sign In Form -->
      <div class="form-panel ${currentMode === 'signin' ? 'active' : ''}" id="panel-signin">
        <form method="POST" action="/oauth/approve" id="signin-form" onsubmit="return handleSubmit(this)">
          <input type="hidden" name="client_id" value="${safeClientId}">
          <input type="hidden" name="redirect_uri" value="${safeRedirectUri}">
          <input type="hidden" name="code_challenge" value="${safeCodeChallenge}">
          <input type="hidden" name="state" value="${safeState}">
          
          <div class="form-group">
            <label class="form-label" for="signin-email">Email</label>
            <input class="form-input" type="email" id="signin-email" name="email" placeholder="you@example.com" required autocomplete="email" autofocus>
            <div class="field-error" id="signin-email-error">Please enter a valid email</div>
          </div>
          
          <div class="form-group">
            <label class="form-label" for="signin-password">Password</label>
            <input class="form-input" type="password" id="signin-password" name="password" placeholder="Enter your password" required autocomplete="current-password">
            <div class="field-error" id="signin-password-error">Password is required</div>
          </div>
          
          <button class="submit-btn" type="submit" id="signin-btn">
            <span class="btn-text">Sign In & Authorize</span>
            <span class="spinner"></span>
          </button>
        </form>
      </div>
      
      <!-- Sign Up Form -->
      <div class="form-panel ${currentMode === 'signup' ? 'active' : ''}" id="panel-signup">
        <form method="POST" action="/oauth/approve-register" id="signup-form" onsubmit="return handleSubmit(this)">
          <input type="hidden" name="client_id" value="${safeClientId}">
          <input type="hidden" name="redirect_uri" value="${safeRedirectUri}">
          <input type="hidden" name="code_challenge" value="${safeCodeChallenge}">
          <input type="hidden" name="state" value="${safeState}">
          
          <div class="form-group">
            <label class="form-label" for="signup-name">Full Name</label>
            <input class="form-input" type="text" id="signup-name" name="name" placeholder="Your name" required autocomplete="name">
          </div>
          
          <div class="form-group">
            <label class="form-label" for="signup-email">Email</label>
            <input class="form-input" type="email" id="signup-email" name="email" placeholder="you@example.com" required autocomplete="email">
            <div class="field-error" id="signup-email-error">Please enter a valid email</div>
          </div>
          
          <div class="form-group">
            <label class="form-label" for="signup-password">Password</label>
            <input class="form-input" type="password" id="signup-password" name="password" placeholder="Min 8 characters" required autocomplete="new-password" oninput="checkPasswordStrength(this.value)">
            <div class="password-strength"><div class="password-strength-bar" id="pw-strength-bar"></div></div>
            <div class="field-error" id="signup-password-error">Password must be at least 8 characters</div>
          </div>
          
          <button class="submit-btn" type="submit" id="signup-btn">
            <span class="btn-text">Create Account & Authorize</span>
            <span class="spinner"></span>
          </button>
        </form>
      </div>
      
      <div class="footer-text">
        By continuing you agree to T-Auth's<br>
        <a href="#">Terms of Service</a> & <a href="#">Privacy Policy</a>
      </div>
    </div>
  </div>
  
  <script>
    function switchMode(mode) {
      document.getElementById('tab-signin').classList.toggle('active', mode === 'signin');
      document.getElementById('tab-signup').classList.toggle('active', mode === 'signup');
      document.getElementById('panel-signin').classList.toggle('active', mode === 'signin');
      document.getElementById('panel-signup').classList.toggle('active', mode === 'signup');
      // Clear errors
      document.querySelectorAll('.error-banner').forEach(el => el.remove());
      document.querySelectorAll('.field-error').forEach(el => el.classList.remove('visible'));
      document.querySelectorAll('.form-input').forEach(el => el.classList.remove('error'));
    }
    
    function handleSubmit(form) {
      const btn = form.querySelector('.submit-btn');
      const emailInput = form.querySelector('input[type="email"]');
      const pwInput = form.querySelector('input[type="password"]');
      
      let valid = true;
      
      if (!emailInput.value || !emailInput.value.includes('@')) {
        emailInput.classList.add('error');
        const err = emailInput.parentElement.querySelector('.field-error');
        if (err) err.classList.add('visible');
        valid = false;
      }
      
      if (!pwInput.value) {
        pwInput.classList.add('error');
        const err = pwInput.parentElement.querySelector('.field-error');
        if (err) err.classList.add('visible');
        valid = false;
      }
      
      if (form.id === 'signup-form' && pwInput.value.length < 8) {
        pwInput.classList.add('error');
        const err = pwInput.parentElement.querySelector('.field-error');
        if (err) { err.textContent = 'Password must be at least 8 characters'; err.classList.add('visible'); }
        valid = false;
      }
      
      if (valid) {
        btn.classList.add('loading');
        btn.disabled = true;
      }
      return valid;
    }
    
    function checkPasswordStrength(pw) {
      const bar = document.getElementById('pw-strength-bar');
      bar.className = 'password-strength-bar';
      if (pw.length >= 12 && /[A-Z]/.test(pw) && /[0-9]/.test(pw) && /[^A-Za-z0-9]/.test(pw)) {
        bar.classList.add('strength-strong');
      } else if (pw.length >= 8) {
        bar.classList.add('strength-medium');
      } else if (pw.length >= 1) {
        bar.classList.add('strength-weak');
      }
    }
    
    // Clear field errors on input
    document.querySelectorAll('.form-input').forEach(input => {
      input.addEventListener('input', () => {
        input.classList.remove('error');
        const err = input.parentElement.querySelector('.field-error');
        if (err) err.classList.remove('visible');
      });
    });
  </script>
</body>
</html>`;
}

// ── Routes ───────────────────────────────────────────────────────────────────

// Register new user (direct API)
app.post('/auth/register', rateLimit(60000, 10), async (req, res) => {
  const { email, password, name } = req.body;

  if (!email || !password) {
    return res.status(400).json({ error: 'Email and password are required' });
  }

  if (password.length < 8) {
    return res.status(400).json({ error: 'Password must be at least 8 characters' });
  }

  if (users.has(email)) {
    return res.status(409).json({ error: 'An account with this email already exists' });
  }

  try {
    const hashedPassword = await bcrypt.hash(password, 12);
    const userId = crypto.randomUUID();
    const user = {
      id: userId,
      email,
      passwordHash: hashedPassword,
      name: name || email.split('@')[0]
    };
    users.set(email, user);

    const accessToken = jwt.sign({ userId: user.id, email: user.email }, ACCESS_TOKEN_SECRET, { expiresIn: '15m' });
    const refreshToken = jwt.sign({ userId: user.id }, REFRESH_TOKEN_SECRET, { expiresIn: '30d' });

    refreshTokens.set(refreshToken, user.id);

    res.json({ user: { id: user.id, email: user.email, name: user.name }, token: accessToken, accessToken, refreshToken, expiresIn: 900 });
  } catch (error) {
    res.status(400).json({ error: error.message });
  }
});

// Login (direct API)
app.post('/auth/login', rateLimit(60000, 15), async (req, res) => {
  const { email, password } = req.body;

  if (!email || !password) {
    return res.status(400).json({ error: 'Email and password are required' });
  }

  const user = users.get(email);

  if (!user || !await bcrypt.compare(password, user.passwordHash)) {
    return res.status(401).json({ error: 'Invalid email or password' });
  }

  const accessToken = jwt.sign({ userId: user.id, email: user.email }, ACCESS_TOKEN_SECRET, { expiresIn: '15m' });
  const refreshToken = jwt.sign({ userId: user.id }, REFRESH_TOKEN_SECRET, { expiresIn: '30d' });

  refreshTokens.set(refreshToken, user.id);

  res.json({
    user: { id: user.id, email: user.email, name: user.name },
    token: accessToken,
    accessToken,
    refreshToken,
    expiresIn: 900
  });
});

// ── OAuth Endpoints ──────────────────────────────────────────────────────────

// OAuth: Authorization endpoint — serves the styled login/consent page
app.get('/oauth/authorize', (req, res) => {
  const { client_id, redirect_uri, code_challenge, state } = req.query;
  
  res.send(generateAuthPage({
    client_id,
    redirect_uri,
    code_challenge,
    state,
    error: null,
    mode: 'signin'
  }));
});

// OAuth: Approve authorization (sign in)
app.post('/oauth/approve', rateLimit(60000, 15), async (req, res) => {
  const { client_id, redirect_uri, code_challenge, state, email, password } = req.body;

  try {
    // Verify user credentials
    const user = users.get(email);

    if (!user || !await bcrypt.compare(password, user.passwordHash)) {
      return res.send(generateAuthPage({
        client_id, redirect_uri, code_challenge, state,
        error: 'Invalid email or password. Please try again.',
        mode: 'signin'
      }));
    }
    
    // Generate authorization code
    const code = crypto.randomBytes(32).toString('hex');
    
    // Store code with PKCE challenge
    authCodes.set(code, {
      userId: user.id,
      codeChallenge: code_challenge,
      redirectUri: redirect_uri,
      used: false,
      createdAt: Date.now()
    });
    
    // Redirect back to app
    const redirectUrl = new URL(redirect_uri);
    redirectUrl.searchParams.set('code', code);
    if (state) redirectUrl.searchParams.set('state', state);
    
    res.redirect(redirectUrl.toString());
  } catch (error) {
    res.send(generateAuthPage({
      client_id, redirect_uri, code_challenge, state,
      error: 'Something went wrong. Please try again.',
      mode: 'signin'
    }));
  }
});

// OAuth: Approve with registration (sign up during consent flow)
app.post('/oauth/approve-register', rateLimit(60000, 10), async (req, res) => {
  const { client_id, redirect_uri, code_challenge, state, email, password, name } = req.body;
  
  if (!email || !password) {
    return res.send(generateAuthPage({
      client_id, redirect_uri, code_challenge, state,
      error: 'Email and password are required.',
      mode: 'signup'
    }));
  }
  
  if (password.length < 8) {
    return res.send(generateAuthPage({
      client_id, redirect_uri, code_challenge, state,
      error: 'Password must be at least 8 characters.',
      mode: 'signup'
    }));
  }
  
  try {
    // Register the user
    const hashedPassword = await bcrypt.hash(password, 12);
    const userId = crypto.randomUUID();
    const user = {
      id: userId,
      email,
      passwordHash: hashedPassword,
      name: name || email.split('@')[0]
    };
    users.set(email, user);
    
    // Generate authorization code
    const code = crypto.randomBytes(32).toString('hex');
    
    authCodes.set(code, {
      userId: user.id,
      codeChallenge: code_challenge,
      redirectUri: redirect_uri,
      used: false,
      createdAt: Date.now()
    });
    
    // Redirect back to app
    const redirectUrl = new URL(redirect_uri);
    redirectUrl.searchParams.set('code', code);
    if (state) redirectUrl.searchParams.set('state', state);
    
    res.redirect(redirectUrl.toString());
  } catch (error) {
    let errorMsg = 'Something went wrong. Please try again.';
    if (error.code === '23505') {
      errorMsg = 'An account with this email already exists. Try signing in instead.';
    }
    res.send(generateAuthPage({
      client_id, redirect_uri, code_challenge, state,
      error: errorMsg,
      mode: 'signup'
    }));
  }
});

// OAuth: Token endpoint (accepts both JSON and URL-encoded)
app.post('/oauth/token', rateLimit(60000, 20), async (req, res) => {
  const { grant_type, code, code_verifier, client_id, redirect_uri } = req.body;
  
  if (grant_type !== 'authorization_code') {
    return res.status(400).json({ error: 'unsupported_grant_type' });
  }

  try {
    const authCode = authCodes.get(code);
  if (!authCode || authCode.used || Date.now() - authCode.createdAt > 600000) {
    return res.status(400).json({ error: 'invalid_grant' });
  }
  
  // Verify PKCE
  const codeChallenge = crypto.createHash('sha256').update(code_verifier).digest('base64url');
  if (codeChallenge !== authCode.codeChallenge) {
    return res.status(400).json({ error: 'invalid_grant', error_description: 'PKCE verification failed' });
  }
  
  // Mark code as used
  authCode.used = true;
  
    // Get user
    const user = Array.from(users.values()).find(u => u.id === authCode.userId);
    
    if (!user) {
      return res.status(400).json({ error: 'invalid_grant', error_description: 'User not found' });
    }
    
    // Generate tokens
    const accessToken = jwt.sign({ userId: user.id, email: user.email }, ACCESS_TOKEN_SECRET, { expiresIn: '15m' });
    const refreshToken = jwt.sign({ userId: user.id }, REFRESH_TOKEN_SECRET, { expiresIn: '30d' });
    
    refreshTokens.set(refreshToken, user.id);
    
    res.json({
      accessToken,
      refreshToken,
      expiresIn: 900,
      tokenType: 'Bearer'
    });
  } catch (error) {
    res.status(500).json({ error: 'server_error', error_description: error.message });
  }
});

// OAuth: User info endpoint
app.get('/oauth/userinfo', authenticateToken, (req, res) => {
  const user = Array.from(users.values()).find(u => u.id === req.user.userId);
  if (!user) return res.status(404).json({ error: 'User not found' });
  res.json({ id: user.id, email: user.email, name: user.name });
});

// Refresh token
app.post('/auth/refresh', rateLimit(60000, 30), (req, res) => {
  const { refresh_token } = req.body;
  
  if (!refresh_token) {
    return res.status(400).json({ error: 'refresh_token is required' });
  }
  
  if (!refreshTokens.has(refresh_token)) {
    return res.status(403).json({ error: 'Invalid refresh token' });
  }
  
  jwt.verify(refresh_token, REFRESH_TOKEN_SECRET, (err, user) => {
    if (err) return res.status(403).json({ error: 'Invalid refresh token' });
    
    // Remove old refresh token (rotation)
    refreshTokens.delete(refresh_token);
    
    // Generate new tokens
    const accessToken = jwt.sign({ userId: user.userId }, ACCESS_TOKEN_SECRET, { expiresIn: '15m' });
    const newRefreshToken = jwt.sign({ userId: user.userId }, REFRESH_TOKEN_SECRET, { expiresIn: '30d' });
    
    refreshTokens.set(newRefreshToken, user.userId);
    
    res.json({ accessToken, refreshToken: newRefreshToken, expiresIn: 900 });
  });
});

// Logout
app.post('/auth/logout', (req, res) => {
  const { refresh_token } = req.body;
  if (refresh_token) refreshTokens.delete(refresh_token);
  res.json({ message: 'Logged out successfully' });
});

// Register OAuth client
app.post('/oauth/clients', authenticateToken, (req, res) => {
  const { name, redirectUris, allowedGrants } = req.body;
  const clientId = crypto.randomUUID();
  const clientSecret = crypto.randomBytes(32).toString('hex');

  oauthClients.set(clientId, {
    id: clientId,
    clientId,
    clientSecret,
    name,
    redirectUris,
    allowedGrants: allowedGrants || ['authorization_code']
  });

  res.json({ clientId, clientSecret, name, redirectUris });
});

// Health check
app.get('/health', (req, res) => {
  res.json({ status: 'ok', timestamp: new Date().toISOString() });
});

// Test callback endpoint
app.get('/callback', (req, res) => {
  const { code, state } = req.query;
  res.send(`
    <html>
    <head><title>Authorization Successful</title></head>
    <body>
      <h1>Authorization Code Received</h1>
      <p>Code: ${escapeHtml(code)}</p>
      <p>State: ${escapeHtml(state)}</p>
      <p>Use this code in the token request.</p>
    </body>
    </html>
  `);
});

// ── Start Server ─────────────────────────────────────────────────────────────
const PORT = process.env.PORT || 3000;
app.listen(PORT, '0.0.0.0', () => {
  console.log(`\n🔐 T-Auth server running on http://0.0.0.0:${PORT} (accessible via adb reverse)`);
  console.log(`   OAuth page: http://localhost:${PORT}/oauth/authorize?client_id=test&redirect_uri=http://localhost:${PORT}/callback&code_challenge=test`);
  console.log(`   Health:     http://localhost:${PORT}/health\n`);
});
