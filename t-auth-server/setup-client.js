#!/usr/bin/env node

/**
 * T-Auth Setup Script
 * Registers the OpenChat client with the T-Auth server
 */

const http = require('http');

const SERVER_URL = 'http://localhost:3000';
const CLIENT_CONFIG = {
  name: 'OpenChat',
  redirectUris: ['tauth://callback'],
  allowedGrants: ['authorization_code']
};

async function registerClient() {
  return new Promise((resolve, reject) => {
    const data = JSON.stringify(CLIENT_CONFIG);
    const options = {
      hostname: 'localhost',
      port: 3000,
      path: '/oauth/clients',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Content-Length': data.length
      }
    };

    const req = http.request(options, (res) => {
      let body = '';
      res.on('data', (chunk) => {
        body += chunk;
      });
      res.on('end', () => {
        if (res.statusCode === 200) {
          const client = JSON.parse(body);
          console.log('✅ Client registered successfully!');
          console.log('Client ID:', client.clientId);
          console.log('Client Secret:', client.clientSecret);
          console.log('');
          console.log('Update your Android app configuration:');
          console.log(`serverUrl = "${SERVER_URL}"`);
          console.log(`clientId = "${client.clientId}"`);
          console.log(`redirectUri = "${CLIENT_CONFIG.redirectUris[0]}"`);
          resolve(client);
        } else {
          reject(new Error(`Failed to register client: ${res.statusCode} ${body}`));
        }
      });
    });

    req.on('error', (error) => {
      reject(error);
    });

    req.write(data);
    req.end();
  });
}

async function checkHealth() {
  return new Promise((resolve, reject) => {
    const options = {
      hostname: 'localhost',
      port: 3000,
      path: '/health',
      method: 'GET'
    };

    const req = http.request(options, (res) => {
      if (res.statusCode === 200) {
        console.log('✅ T-Auth server is running');
        resolve();
      } else {
        reject(new Error(`Server health check failed: ${res.statusCode}`));
      }
    });

    req.on('error', (error) => {
      reject(new Error('Cannot connect to T-Auth server. Make sure it is running.'));
    });

    req.end();
  });
}

async function main() {
  try {
    console.log('🔧 Setting up T-Auth client...\n');
    await checkHealth();
    await registerClient();
    console.log('\n🎉 Setup complete!');
  } catch (error) {
    console.error('❌ Setup failed:', error.message);
    console.log('\nMake sure the T-Auth server is running:');
    console.log('cd t-auth-server && npm start');
    process.exit(1);
  }
}

main();