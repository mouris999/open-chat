const express = require('express');
const cors = require('cors');
const jwt = require('jsonwebtoken');
const bcrypt = require('bcryptjs');
const crypto = require('crypto');

const ALLOWED_ORIGINS = process.env.ALLOWED_ORIGINS
    ? process.env.ALLOWED_ORIGINS.split(',')
    : ['http://localhost:3000', 'http://localhost:8080'];

const app = express();

// Middleware
app.use(cors({ origin: ALLOWED_ORIGINS }));
app.use(express.json());

// JWT Secret — must be set via environment variable
const JWT_SECRET = process.env.JWT_SECRET;
if (!JWT_SECRET) {
    console.error('FATAL: JWT_SECRET environment variable must be set');
    process.exit(1);
}

// In-memory user store (in production, use a database)
const users = new Map();
const phoneVerifications = new Map();

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

// Helper functions
function generateToken(userId, email) {
  return jwt.sign({ userId, email }, JWT_SECRET, { expiresIn: '7d', algorithm: 'HS256' });
}

function verifyToken(token) {
  try {
    return jwt.verify(token, JWT_SECRET, { algorithms: ['HS256'] });
  } catch (error) {
    return null;
  }
}

// Middleware to verify JWT
function authenticateToken(req, res, next) {
  const authHeader = req.headers['authorization'];
  const token = authHeader && authHeader.split(' ')[1];

  if (!token) {
    return res.status(401).json({ error: 'Access token required' });
  }

  const decoded = verifyToken(token);
  if (!decoded) {
    return res.status(403).json({ error: 'Invalid or expired token' });
  }

  req.user = decoded;
  next();
}

// Auth endpoints

// Register with email and password
app.post('/auth/register', rateLimit(60000, 10), async (req, res) => {
  try {
    const { email, password, name } = req.body;

    if (!email || !password) {
      return res.status(400).json({ error: 'Email and password are required' });
    }

    if (password.length < 8) {
      return res.status(400).json({ error: 'Password must be at least 8 characters' });
    }

    // Check if user already exists
    if (users.has(email)) {
      return res.status(409).json({ error: 'User already exists with this email' });
    }

    // Hash password
    const hashedPassword = await bcrypt.hash(password, 12);

    // Create user
    const userId = crypto.randomUUID();
    const user = {
      id: userId,
      email,
      name: name || 'User',
      password: hashedPassword,
      createdAt: new Date().toISOString()
    };

    users.set(email, user);

    // Generate token
    const token = generateToken(userId, email);

    res.status(201).json({
      token,
      user: {
        id: userId,
        email,
        name: user.name
      }
    });
  } catch (error) {
    console.error('Registration error:', error);
    res.status(500).json({ error: 'Registration failed' });
  }
});

// Login with email and password
app.post('/auth/login', rateLimit(60000, 15), async (req, res) => {
  try {
    const { email, password } = req.body;

    if (!email || !password) {
      return res.status(400).json({ error: 'Email and password are required' });
    }

    const user = users.get(email);
    if (!user) {
      return res.status(401).json({ error: 'Invalid email or password' });
    }

    const isValidPassword = await bcrypt.compare(password, user.password);
    if (!isValidPassword) {
      return res.status(401).json({ error: 'Invalid email or password' });
    }

    const token = generateToken(user.id, user.email);

    res.json({
      token,
      user: {
        id: user.id,
        email: user.email,
        name: user.name
      }
    });
  } catch (error) {
    console.error('Login error:', error);
    res.status(500).json({ error: 'Login failed' });
  }
});

// Send phone verification code
app.post('/auth/send-phone-verification', rateLimit(60000, 5), async (req, res) => {
  try {
    const { phoneNumber } = req.body;

    if (!phoneNumber) {
      return res.status(400).json({ error: 'Phone number is required' });
    }

    const verificationCode = String(Math.floor(100000 + crypto.randomInt(0, 900000)));
    const verificationId = `verification_${Date.now()}_${crypto.randomBytes(4).toString('hex')}`;

    // Store verification code (in production, use Redis or similar)
    phoneVerifications.set(verificationId, {
      phoneNumber,
      code: verificationCode,
      expiresAt: Date.now() + 5 * 60 * 1000 // 5 minutes
    });

    // In production, send SMS using Twilio or similar service
    console.log(`Verification code for ${phoneNumber}: ${verificationCode}`);

    res.json({
      verificationId,
      message: 'Verification code sent'
    });
  } catch (error) {
    console.error('Phone verification error:', error);
    res.status(500).json({ error: 'Failed to send verification code' });
  }
});

// Verify phone code and create account
app.post('/auth/verify-phone', rateLimit(60000, 10), async (req, res) => {
  try {
    const { verificationId, code } = req.body;

    if (!verificationId || !code) {
      return res.status(400).json({ error: 'Verification ID and code are required' });
    }

    const verification = phoneVerifications.get(verificationId);
    if (!verification) {
      return res.status(400).json({ error: 'Invalid verification ID' });
    }

    if (Date.now() > verification.expiresAt) {
      phoneVerifications.delete(verificationId);
      return res.status(400).json({ error: 'Verification code expired' });
    }

    if (verification.code !== code) {
      return res.status(400).json({ error: 'Invalid verification code' });
    }

    const userId = crypto.randomUUID();
    const user = {
      id: userId,
      phoneNumber: verification.phoneNumber,
      name: 'User',
      createdAt: new Date().toISOString()
    };

    // Store user by phone number
    users.set(verification.phoneNumber, user);

    // Clean up verification
    phoneVerifications.delete(verificationId);

    const token = generateToken(userId, null);

    res.json({
      token,
      user: {
        id: userId,
        phoneNumber: user.phoneNumber,
        name: user.name
      }
    });
  } catch (error) {
    console.error('Phone verification error:', error);
    res.status(500).json({ error: 'Verification failed' });
  }
});

// Get current user profile
app.get('/auth/profile', authenticateToken, (req, res) => {
  const user = Array.from(users.values()).find(u => u.id === req.user.userId);
  if (!user) {
    return res.status(404).json({ error: 'User not found' });
  }

  res.json({
    id: user.id,
    email: user.email,
    phoneNumber: user.phoneNumber,
    name: user.name
  });
});

// Update user profile
app.put('/auth/profile', authenticateToken, (req, res) => {
  const { name } = req.body;
  const user = Array.from(users.values()).find(u => u.id === req.user.userId);

  if (!user) {
    return res.status(404).json({ error: 'User not found' });
  }

  user.name = name || user.name;
  res.json({
    id: user.id,
    email: user.email,
    phoneNumber: user.phoneNumber,
    name: user.name
  });
});

// ============== FCM Notification Endpoints ==============

// Firebase Admin SDK initialization (uncomment and add service account JSON to use)
// const admin = require('firebase-admin');
// const serviceAccount = require('./service-account.json');
// admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });

// Store FCM tokens (in production, use database)
const fcmTokens = new Map();

// Register FCM token
app.post('/fcm/register', authenticateToken, (req, res) => {
  const { token } = req.body;
  if (!token) return res.status(400).json({ error: 'Token is required' });
  fcmTokens.set(req.user.userId, token);
  res.json({ success: true });
});

// Send notification for new message
app.post('/notify/message', authenticateToken, async (req, res) => {
  try {
    const { targetUserId, chatId, senderName, message } = req.body;
    if (!targetUserId || !chatId) return res.status(400).json({ error: 'Missing required fields' });

    const targetToken = fcmTokens.get(targetUserId);
    if (!targetToken) return res.json({ success: false, reason: 'User not registered for push' });

    console.log(`Sending message notification to ${targetUserId}: ${senderName}: ${message}`);
    // When Firebase Admin SDK is configured:
    // const payload = {
    //   data: {
    //     type: 'message',
    //     chat_id: chatId,
    //     sender_name: senderName || 'Unknown',
    //     message: message || 'New message'
    //   }
    // };
    // await admin.messaging().send({ token: targetToken, data: payload.data });

    res.json({ success: true, note: 'FCM requires Firebase Admin SDK setup' });
  } catch (error) {
    console.error('Notification error:', error);
    res.status(500).json({ error: 'Failed to send notification' });
  }
});

// Send notification for incoming call
app.post('/notify/call', authenticateToken, async (req, res) => {
  try {
    const { targetUserId, callId, callerName, isVideo } = req.body;
    if (!targetUserId || !callId) return res.status(400).json({ error: 'Missing required fields' });

    const targetToken = fcmTokens.get(targetUserId);
    if (!targetToken) return res.json({ success: false, reason: 'User not registered for push' });

    console.log(`Sending call notification to ${targetUserId}: ${callerName} (${isVideo ? 'video' : 'voice'})`);
    // When Firebase Admin SDK is configured:
    // const payload = {
    //   data: {
    //     type: 'call',
    //     call_id: callId,
    //     caller_name: callerName || 'Unknown',
    //     is_video: String(isVideo || false)
    //   }
    // };
    // await admin.messaging().send({ token: targetToken, data: payload.data });

    res.json({ success: true, note: 'FCM requires Firebase Admin SDK setup' });
  } catch (error) {
    console.error('Call notification error:', error);
    res.status(500).json({ error: 'Failed to send notification' });
  }
});

// Health check endpoint
app.get('/health', (req, res) => {
  res.json({ status: 'OK', timestamp: new Date().toISOString() });
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
  console.log(`Custom Auth Server running on port ${PORT}`);
});
