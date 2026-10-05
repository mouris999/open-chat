const express  = require('express');
const bcrypt   = require('bcryptjs');
const jwt      = require('jsonwebtoken');
const { v4: uuidv4 } = require('uuid');
const pool     = require('../config/db');
const router   = express.Router();

// ─── POST /auth/register ──────────────────────────────────────────────────────
router.post('/register', async (req, res) => {
  const { email, password, name } = req.body;

  if (!email || !password) {
    return res.status(400).json({ error: 'email and password are required' });
  }
  if (password.length < 8) {
    return res.status(400).json({ error: 'password must be at least 8 characters' });
  }

  try {
    const existing = await pool.query('SELECT id FROM users WHERE email = $1', [email]);
    if (existing.rows.length > 0) {
      return res.status(409).json({ error: 'email_taken', error_description: 'Email already registered' });
    }

    const password_hash = await bcrypt.hash(password, 12);
    const result = await pool.query(
      `INSERT INTO users (email, password_hash, name)
       VALUES ($1, $2, $3) RETURNING id, email, name, created_at`,
      [email.toLowerCase().trim(), password_hash, name || null]
    );

    const user = result.rows[0];
    res.status(201).json({
      message: 'Account created successfully',
      user: { id: user.id, email: user.email, name: user.name }
    });

  } catch (err) {
    console.error('Register error:', err);
    res.status(500).json({ error: 'server_error' });
  }
});

// ─── POST /auth/login ─────────────────────────────────────────────────────────
router.post('/login', async (req, res) => {
  const { email, password } = req.body;

  if (!email || !password) {
    return res.status(400).json({ error: 'email and password are required' });
  }

  try {
    const result = await pool.query(
      'SELECT * FROM users WHERE email = $1',
      [email.toLowerCase().trim()]
    );

    const user = result.rows[0];
    if (!user) {
      return res.status(401).json({ error: 'invalid_credentials' });
    }

    const valid = await bcrypt.compare(password, user.password_hash);
    if (!valid) {
      return res.status(401).json({ error: 'invalid_credentials' });
    }

    // Issue access token
    const accessToken = jwt.sign(
      { sub: user.id, email: user.email, name: user.name },
      process.env.ACCESS_TOKEN_SECRET,
      { expiresIn: process.env.ACCESS_TOKEN_EXPIRES || '15m' }
    );

    // Issue refresh token
    const refreshToken = uuidv4() + '.' + uuidv4();
    const expiresAt = new Date();
    expiresAt.setDate(expiresAt.getDate() + 30);

    await pool.query(
      `INSERT INTO refresh_tokens (token, user_id, client_id, expires_at)
       VALUES ($1, $2, $3, $4)`,
      [refreshToken, user.id, 'direct_login', expiresAt]
    );

    res.json({
      access_token: accessToken,
      refresh_token: refreshToken,
      token_type: 'Bearer',
      expires_in: 900,
      user: { id: user.id, email: user.email, name: user.name, avatar_url: user.avatar_url }
    });

  } catch (err) {
    console.error('Login error:', err);
    res.status(500).json({ error: 'server_error' });
  }
});

// ─── POST /auth/refresh ───────────────────────────────────────────────────────
router.post('/refresh', async (req, res) => {
  const { refresh_token } = req.body;
  if (!refresh_token) return res.status(400).json({ error: 'refresh_token required' });

  try {
    const result = await pool.query(
      `SELECT rt.*, u.email, u.name FROM refresh_tokens rt
       JOIN users u ON rt.user_id = u.id
       WHERE rt.token = $1 AND rt.revoked = false AND rt.expires_at > NOW()`,
      [refresh_token]
    );

    if (!result.rows.length) {
      return res.status(401).json({ error: 'invalid_refresh_token' });
    }

    const row = result.rows[0];

    // Rotate: revoke old, issue new
    await pool.query('UPDATE refresh_tokens SET revoked = true WHERE token = $1', [refresh_token]);

    const newAccessToken = jwt.sign(
      { sub: row.user_id, email: row.email, name: row.name },
      process.env.ACCESS_TOKEN_SECRET,
      { expiresIn: process.env.ACCESS_TOKEN_EXPIRES || '15m' }
    );

    const newRefreshToken = uuidv4() + '.' + uuidv4();
    const expiresAt = new Date();
    expiresAt.setDate(expiresAt.getDate() + 30);

    await pool.query(
      `INSERT INTO refresh_tokens (token, user_id, client_id, expires_at)
       VALUES ($1, $2, $3, $4)`,
      [newRefreshToken, row.user_id, row.client_id, expiresAt]
    );

    res.json({
      access_token: newAccessToken,
      refresh_token: newRefreshToken,
      token_type: 'Bearer',
      expires_in: 900
    });

  } catch (err) {
    console.error('Refresh error:', err);
    res.status(500).json({ error: 'server_error' });
  }
});

// ─── POST /auth/logout ────────────────────────────────────────────────────────
router.post('/logout', async (req, res) => {
  const { refresh_token } = req.body;
  if (refresh_token) {
    await pool.query('UPDATE refresh_tokens SET revoked = true WHERE token = $1', [refresh_token]);
  }
  res.json({ message: 'Logged out successfully' });
});

module.exports = router;
