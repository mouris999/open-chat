require('dotenv').config();
const express   = require('express');
const cors      = require('cors');
const rateLimit = require('express-rate-limit');

const authRoutes  = require('./routes/auth');
const oauthRoutes = require('./routes/oauth');

const app  = express();
const PORT = process.env.PORT || 3000;

// ─── Middleware ───────────────────────────────────────────────────────────────
app.use(cors());
app.use(express.json());
app.use(express.urlencoded({ extended: true })); // needed for HTML form POSTs

// Rate limiting on auth endpoints
const authLimiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: 20,
  message: { error: 'too_many_requests', error_description: 'Too many attempts, try again later' }
});

// ─── Routes ───────────────────────────────────────────────────────────────────
app.use('/auth',  authLimiter, authRoutes);
app.use('/oauth', oauthRoutes);

// Health check
app.get('/health', (req, res) => res.json({ status: 'ok', service: 'T-Auth', version: '1.0.0' }));

// OpenID Connect discovery document (optional but good practice)
app.get('/.well-known/openid-configuration', (req, res) => {
  const base = process.env.BASE_URL || `http://localhost:${PORT}`;
  res.json({
    issuer:                 base,
    authorization_endpoint: `${base}/oauth/authorize`,
    token_endpoint:         `${base}/oauth/token`,
    userinfo_endpoint:      `${base}/oauth/userinfo`,
    scopes_supported:       ['openid', 'profile', 'email'],
    response_types_supported: ['code'],
    grant_types_supported:  ['authorization_code', 'refresh_token'],
    code_challenge_methods_supported: ['S256'],
  });
});

// ─── Start ────────────────────────────────────────────────────────────────────
app.listen(PORT, () => {
  console.log(`🚀 T-Auth server running at http://localhost:${PORT}`);
  console.log(`   Auth:  http://localhost:${PORT}/auth/register`);
  console.log(`   OAuth: http://localhost:${PORT}/oauth/authorize`);
});
