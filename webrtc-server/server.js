const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const cors = require('cors');

const ALLOWED_ORIGINS = process.env.ALLOWED_ORIGINS
    ? process.env.ALLOWED_ORIGINS.split(',')
    : ['http://localhost:3000', 'http://localhost:8080', 'http://127.0.0.1:3000', 'http://127.0.0.1:8080', 'https://open-chat-795f6.web.app', 'https://open-chat-795f6.firebaseapp.com', 'https://*.vercel.app'];

const app = express();
app.use(cors({ origin: ALLOWED_ORIGINS }));

const server = http.createServer(app);
const io = new Server(server, {
    cors: {
        origin: ALLOWED_ORIGINS,
        methods: ["GET", "POST"]
    }
});

const connectedUsers = new Map();
const activeCalls = new Map();

io.on('connection', (socket) => {
    console.log('User connected:', socket.id);
    
    socket.on('register', (userId) => {
        connectedUsers.set(userId, socket.id);
        socket.userId = userId;
        console.log(`User ${userId} registered with socket ${socket.id}`);
    });
    
    socket.on('call-initiate', (data) => {
        const { recipientId, callType, offer, callId } = data;
        const recipientSocketId = connectedUsers.get(recipientId);
        
        if (recipientSocketId) {
            activeCalls.set(callId, {
                callerId: socket.userId,
                recipientId: recipientId,
                callType: callType,
                status: 'ringing'
            });
            
            io.to(recipientSocketId).emit('incoming-call', {
                callId,
                callerId: socket.userId,
                callType,
                offer
            });
        } else {
            socket.emit('call-failed', { 
                callId, 
                reason: 'User offline' 
            });
        }
    });
    
    socket.on('call-answer', (data) => {
        const { callId, answer } = data;
        const call = activeCalls.get(callId);
        
        if (call) {
            call.status = 'connected';
            const callerSocketId = connectedUsers.get(call.callerId);
            
            if (callerSocketId) {
                io.to(callerSocketId).emit('call-answered', {
                    callId,
                    answer
                });
            }
        }
    });
    
    socket.on('call-reject', (data) => {
        const { callId } = data;
        const call = activeCalls.get(callId);
        
        if (call) {
            const callerSocketId = connectedUsers.get(call.callerId);
            
            if (callerSocketId) {
                io.to(callerSocketId).emit('call-rejected', { callId });
            }
            
            activeCalls.delete(callId);
        }
    });
    
    socket.on('ice-candidate', (data) => {
        const { recipientId, candidate } = data;
        const recipientSocketId = connectedUsers.get(recipientId);
        
        if (recipientSocketId) {
            io.to(recipientSocketId).emit('ice-candidate', {
                senderId: socket.userId,
                candidate
            });
        }
    });
    
    socket.on('call-end', (data) => {
        const { callId } = data;
        const call = activeCalls.get(callId);
        
        if (call) {
            const otherUserId = call.callerId === socket.userId ? 
                call.recipientId : call.callerId;
            const otherSocketId = connectedUsers.get(otherUserId);
            
            if (otherSocketId) {
                io.to(otherSocketId).emit('call-ended', { callId });
            }
            
            activeCalls.delete(callId);
        }
    });
    
    socket.on('disconnect', () => {
        console.log('User disconnected:', socket.id);
        
        if (socket.userId) {
            connectedUsers.delete(socket.userId);
            
            activeCalls.forEach((call, callId) => {
                if (call.callerId === socket.userId || call.recipientId === socket.userId) {
                    const otherUserId = call.callerId === socket.userId ? 
                        call.recipientId : call.callerId;
                    const otherSocketId = connectedUsers.get(otherUserId);
                    
                    if (otherSocketId) {
                        io.to(otherSocketId).emit('call-ended', { 
                            callId, 
                            reason: 'User disconnected' 
                        });
                    }
                    
                    activeCalls.delete(callId);
                }
            });
        }
    });
});

const PORT = process.env.PORT || 3001;
server.listen(PORT, () => {
    console.log(`WebRTC Signaling Server running on port ${PORT}`);
});
