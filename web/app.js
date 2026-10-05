import { initializeApp } from "https://www.gstatic.com/firebasejs/10.7.1/firebase-app.js";
import { getDatabase, ref, onValue, set, update, push, remove, runTransaction } from "https://www.gstatic.com/firebasejs/10.7.1/firebase-database.js";
import { getFirestore, doc, getDoc, setDoc, updateDoc, collection, getDocs, onSnapshot, query, where, limit } from "https://www.gstatic.com/firebasejs/10.7.1/firebase-firestore.js";
import { getAuth, signInAnonymously } from "https://www.gstatic.com/firebasejs/10.7.1/firebase-auth.js";

const firebaseConfig = {
  apiKey: "AIzaSyDiyCiP0aWWiRgfL1-szMHHLj4O61IEvl4",
  authDomain: "open-chat-795f6.firebaseapp.com",
  databaseURL: "https://open-chat-795f6-default-rtdb.firebaseio.com",
  projectId: "open-chat-795f6",
  storageBucket: "open-chat-795f6.firebasestorage.app",
  messagingSenderId: "992470424862",
  appId: "1:992470424862:web:82c4753bb28a0edc093d0f",
  measurementId: "G-V69G0VNQJQ"
};

const app = initializeApp(firebaseConfig);
const db = getDatabase(app);
const firestore = getFirestore(app);

function initialsAvatar(letter, bg = '6366f1', fg = 'ffffff', size = 80) {
    const svg = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ' + size + ' ' + size + '"><rect fill="#' + bg + '" width="' + size + '" height="' + size + '" rx="' + Math.round(size / 6) + '"/><text x="50%" y="50%" dominant-baseline="central" text-anchor="middle" fill="#' + fg + '" font-size="' + Math.round(size / 2.5) + '" font-family="sans-serif" font-weight="600">' + letter + '</text></svg>';
    return 'data:image/svg+xml,' + encodeURIComponent(svg);
}

document.addEventListener('DOMContentLoaded', () => {
    // Session State
    let sessionToken = null;
    let myUid = null;
    let myProfile = null;
    
    // Application States
    let activeChats = [];
    let activeContacts = [];
    let selectedChatId = null;
    let selectedChatData = null;
    let activeMsgListener = null;
    let activePresenceListener = null;
    let activeTypingListener = null;
    
    // Reply Reference
    let replyToMsg = null;
    
    // Typing debouncer
    let typingTimeout = null;

    // UI Nodes
    const qrcodeElement = document.getElementById('qrcode');
    const statusText = document.getElementById('status');
    const loginCard = document.getElementById('login-card');
    const dashboardCard = document.getElementById('dashboard-card');
    
    // Dashboard Components
    const chatsContainer = document.getElementById('chats-container');
    const messagesContainer = document.getElementById('messages-container');
    const chatSearch = document.getElementById('chat-search');
    
    // Active Chat nodes
    const welcomeViewport = document.getElementById('welcome-viewport');
    const activeChatContainer = document.getElementById('active-chat-container');
    const activeChatAvatar = document.getElementById('active-chat-avatar');
    const activeChatTitle = document.getElementById('active-chat-title');
    const activeChatStatus = document.getElementById('active-chat-status');
    const activeUserStatusDot = document.getElementById('active-user-status');
    const messageInput = document.getElementById('message-input');
    const sendMsgBtn = document.getElementById('send-msg-btn');
    
    // Theme, logout, nav
    const themeToggleBtn = document.getElementById('theme-toggle-btn');
    const logoutBtn = document.getElementById('logout-btn');
    const sidebarProfileBtn = document.getElementById('sidebar-profile-btn');
    const userAvatar = document.getElementById('user-avatar');
    
    // Modals
    const createChatBtn = document.getElementById('create-chat-btn');
    const createChatModal = document.getElementById('create-chat-modal');
    const closeChatModal = document.getElementById('close-chat-modal');
    const contactsModalList = document.getElementById('contacts-modal-list');
    const contactSearchInput = document.getElementById('contact-search-input');
    
    const createGroupBtn = document.getElementById('create-group-btn');
    const createGroupModal = document.getElementById('create-group-modal');
    const closeGroupModal = document.getElementById('close-group-modal');
    const groupContactsList = document.getElementById('group-contacts-list');
    const groupContactSearch = document.getElementById('group-contact-search');
    const confirmGroupBtn = document.getElementById('confirm-group-btn');
    const cancelGroupBtn = document.getElementById('cancel-group-btn');
    const groupNameInput = document.getElementById('group-name-input');
    
    const profileEditorModal = document.getElementById('profile-editor-modal');
    const closeProfileModal = document.getElementById('close-profile-modal');
    const profileAvatarUrl = document.getElementById('profile-avatar-url');
    const profileNameInput = document.getElementById('profile-name-input');
    const profileBioInput = document.getElementById('profile-bio-input');
    const confirmProfileBtn = document.getElementById('confirm-profile-btn');
    const cancelProfileBtn = document.getElementById('cancel-profile-btn');
    const profileAvatarPreview = document.getElementById('profile-edit-avatar-preview');
    
    const scheduledMessagesModal = document.getElementById('scheduled-messages-modal');
    const closeScheduledModal = document.getElementById('close-scheduled-modal');
    const scheduledListContainer = document.getElementById('scheduled-list-container');
    const navScheduled = document.getElementById('nav-scheduled');
    const navContacts = document.getElementById('nav-contacts');
    
    const helpSupportModal = document.getElementById('help-support-modal');
    const closeHelpModal = document.getElementById('close-help-modal');
    const navHelp = document.getElementById('nav-help');
    
    // Drawers
    const toggleDetailsBtn = document.getElementById('toggle-details-btn');
    const detailsDrawer = document.getElementById('details-drawer');
    const closeDrawerBtn = document.getElementById('close-drawer-btn');
    const drawerChatAvatar = document.getElementById('drawer-chat-avatar');
    const drawerChatTitle = document.getElementById('drawer-chat-title');
    const drawerChatSubtitle = document.getElementById('drawer-chat-subtitle');
    const membersCount = document.getElementById('members-count');
    const membersContainer = document.getElementById('members-container');
    const groupMembersSection = document.getElementById('group-members-section');
    const disappearingMessagesSection = document.getElementById('disappearing-messages-section');
    const sharedMediaContainer = document.getElementById('shared-media-container');
    
    // Reply and Inner Search
    const replyPreviewBar = document.getElementById('reply-preview-bar');
    const replyPreviewSender = document.getElementById('reply-preview-sender');
    const replyPreviewText = document.getElementById('reply-preview-text');
    const closeReplyBtn = document.getElementById('close-reply-btn');
    
    const searchMsgBtn = document.getElementById('search-msg-btn');
    const innerSearchWrapper = document.getElementById('inner-search-wrapper');
    const innerSearchInput = document.getElementById('inner-search-input');
    const closeSearchBtn = document.getElementById('close-search-btn');
    
    // Schedule dialog
    const scheduleSendBtn = document.getElementById('schedule-send-btn');
    const scheduleSelectModal = document.getElementById('schedule-select-modal');
    const closeScheduleSelect = document.getElementById('close-schedule-select');
    const cancelScheduleSelect = document.getElementById('cancel-schedule-select');
    const confirmScheduleSelect = document.getElementById('confirm-schedule-select');
    const scheduleTimeInput = document.getElementById('schedule-time-input');

    // Float reactions & actions
    const reactionsPickerFloating = document.getElementById('reactions-picker-floating');
    let reactionTargetMessageId = null;
    let reactionTargetMsgIsMe = false;

    const copyMsgBtn = document.getElementById('copy-msg-btn');
    const deleteMsgBtn = document.getElementById('delete-msg-btn');
    const voiceCallBtn = document.getElementById('voice-call-btn');
    const videoCallBtn = document.getElementById('video-call-btn');

    // Attachments
    const attachBtn = document.getElementById('attach-btn');
    const attachmentsPopup = document.getElementById('attachments-popup');

    // Initialize Cryptographic QR linking
    async function startSessionSetup() {
        sessionToken = generateSessionToken();
        console.log('[OpenChat] Session token:', sessionToken);
        const sessionRef = ref(db, 'sessions/' + sessionToken);
        
        try {
            await set(sessionRef, {
                status: 'pending',
                createdAt: Date.now()
            });
            console.log('[OpenChat] Session created in Firebase');
        } catch (e) {
            console.error('[OpenChat] Failed to create session:', e);
            statusText.innerHTML = '<i class="fa-solid fa-triangle-exclamation" style="color:#f43f5e;"></i> Error: Cannot reach Firebase. Check that Realtime Database rules allow unauthenticated writes to /sessions/{token}.' + '<br><small style="opacity:0.7">' + e.message + '</small>';
            statusText.style.color = '#f43f5e';
            return;
        }

        // Generate QR code
        qrcodeElement.innerHTML = '';
        new QRCode(qrcodeElement, {
            text: `openchat://link?token=${sessionToken}`,
            width: 210,
            height: 210
        });
        console.log('[OpenChat] QR code rendered');

        statusText.innerHTML = '<i class="fa-solid fa-circle-notch fa-spin status-spinner"></i> Waiting for phone scan...<br><small style="opacity:0.7">Token: ' + sessionToken.substring(0, 8) + '...</small>';

        // Timeout if phone doesn't scan within 5 minutes
        const scanTimeout = setTimeout(() => {
            statusText.innerHTML = '<i class="fa-solid fa-triangle-exclamation" style="color:#f43f5e;"></i> Timed out waiting for phone scan. Refresh to try again.';
            statusText.style.color = '#f43f5e';
        }, 300000);

        // Listen for mobile authorization
        onValue(sessionRef, (snapshot) => {
            const data = snapshot.val();
            console.log('[OpenChat] Session data changed:', JSON.stringify(data));
            if (data && data.status === 'authorized' && data.authenticatedUid) {
                clearTimeout(scanTimeout);
                console.log('[OpenChat] Authorized! UID:', data.authenticatedUid);
                statusText.innerHTML = '<i class="fa-solid fa-shield-halved text-success"></i> Session Authorized! Signing in...';
                statusText.style.color = '';
                
                myUid = data.authenticatedUid;

                setTimeout(async () => {
                    try {
                        console.log('[OpenChat] Signing in anonymously...');
                        await signInAnonymously(getAuth(app));
                        console.log('[OpenChat] Anonymous sign-in success');
                    } catch (e) {
                        console.error('[OpenChat] Anonymous auth failed:', e);
                        statusText.innerHTML = '<i class="fa-solid fa-triangle-exclamation" style="color:#f43f5e;"></i> Auth error: ' + e.message + '<br><small style="opacity:0.7">Enable Anonymous sign-in in Firebase Console > Authentication > Sign-in method</small>';
                        return;
                    }
                    
                    statusText.innerHTML = '<i class="fa-solid fa-circle-notch fa-spin status-spinner"></i> Loading profile...';
                    await initUserProfile();
                    console.log('[OpenChat] Profile loaded:', myProfile?.displayName);
                    
                    await updatePresence(true);
                    
                    // Transition View to Dashboard
                    loginCard.classList.add('hidden');
                    dashboardCard.classList.remove('hidden');
                    console.log('[OpenChat] Dashboard shown');
                    
                    // Sync lists
                    initChatsList();
                    loadAllContacts();
                }, 1000);
            }
        });
    }

    // Token Generator
    function generateSessionToken(length = 32) {
        const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
        const array = new Uint8Array(length);
        crypto.getRandomValues(array);
        let result = '';
        for (let i = 0; i < length; i++) {
            result += chars.charAt(array[i] % chars.length);
        }
        return result;
    }

    // Load User Profile
    async function initUserProfile() {
        try {
            const userDocRef = doc(firestore, "users", myUid);
            const docSnap = await getDoc(userDocRef);
            
            if (docSnap.exists()) {
                myProfile = docSnap.data();
                userAvatar.src = myProfile.photoUrl || initialsAvatar((myProfile.displayName || 'U')[0]);
                profileAvatarUrl.value = myProfile.photoUrl || '';
                profileNameInput.value = myProfile.displayName || '';
                profileBioInput.value = myProfile.bio || 'Encrypted chats';
                profileAvatarPreview.src = userAvatar.src;
            } else {
                myProfile = { displayName: `User ${myUid.substring(0, 5)}` };
            }
        } catch (e) {
            console.error("Firestore user fetch error:", e);
        }
    }

    // Online Presence
    async function updatePresence(online) {
        if (!myUid) return;
        const statusRef = ref(db, `status/${myUid}`);
        if (online) {
            set(statusRef, {
                online: true,
                lastSeen: Date.now()
            });
            // Auto offline on disconnect
            const presenceRef = ref(db, `status/${myUid}/online`);
            const lastSeenRef = ref(db, `status/${myUid}/lastSeen`);
            // Set callbacks
            // In pure web, Firebase handles onDisconnect cleanly
        } else {
            set(statusRef, {
                online: false,
                lastSeen: Date.now()
            });
        }
    }

    // Load registered contacts
    async function loadAllContacts() {
        try {
            const querySnapshot = await getDocs(collection(firestore, "users"));
            activeContacts = [];
            querySnapshot.forEach((doc) => {
                if (doc.id !== myUid) {
                    activeContacts.push({
                        id: doc.id,
                        ...doc.data()
                    });
                }
            });
            console.log('[OpenChat] Contacts loaded:', activeContacts.length);
            if (!createChatModal.classList.contains('hidden')) renderContactsList(contactsModalList, false);
            if (!createGroupModal.classList.contains('hidden')) renderContactsList(groupContactsList, true);
        } catch (e) {
            console.error("Error loading contacts — check Firestore rules allow read on /users for authenticated users:", e);
        }
    }

    // Load Chats List
    function initChatsList() {
        const chatsRef = ref(db, 'chats');
        
        onValue(chatsRef, (snapshot) => {
            chatsContainer.innerHTML = '';
            activeChats = [];
            
            if (!snapshot.exists()) {
                chatsContainer.innerHTML = '<p class="empty-list-placeholder">No conversations active yet.</p>';
                return;
            }
            
            snapshot.forEach((child) => {
                const chat = child.val();
                const chatId = child.key;
                
                // Read participants map or list
                const participants = chat.participants || [];
                const participantsMap = chat.participants_map || {};
                
                const isPart = participants.includes(myUid) || participantsMap[myUid] === true;
                
                if (isPart) {
                    activeChats.push({
                        id: chatId,
                        ...chat
                    });
                }
            });

            // Sort chats by lastMessageTimestamp desc
            activeChats.sort((a, b) => (b.lastMessageTimestamp || 0) - (a.lastMessageTimestamp || 0));

            // Render Pinned first
            const pinned = activeChats.filter(c => c.isPinned);
            const standard = activeChats.filter(c => !c.isPinned);
            
            const ordered = [...pinned, ...standard];

            if (ordered.length === 0) {
                chatsContainer.innerHTML = '<p class="empty-list-placeholder">No conversations active yet.</p>';
                return;
            }

            ordered.forEach(chat => {
                renderChatItem(chat);
            });
        });
    }

    // Render individual chat card
    function renderChatItem(chat) {
        const item = document.createElement('div');
        item.className = `chat-item ${chat.id === selectedChatId ? 'active' : ''}`;
        item.dataset.chatId = chat.id;

        const isGroup = chat.type === 'GROUP';
        const safeTitle = escapeHtml(chat.title || 'Private Conversation');
        const firstChar = (chat.title || 'G')[0];
        const photo = chat.photoUrl || (isGroup ? initialsAvatar('G', 'e2e8f0', '1e293b') : initialsAvatar(firstChar));
        const safePhoto = escapeHtml(photo);
        
        const lastMsg = escapeHtml(chat.lastMessage ? chat.lastMessage.content : 'No messages yet');
        const rawTime = chat.lastMessageTimestamp || chat.createdAt || Date.now();
        const timeFormatted = formatTime(rawTime);
        
        const unread = chat.unreadCount && chat.unreadCount[myUid] ? chat.unreadCount[myUid] : 0;
        
        item.innerHTML = `
            <div class="chat-item-avatar-wrapper">
                <img src="${safePhoto}" class="chat-item-avatar" alt="Avatar">
                <span class="chat-item-status" id="status-${chat.id}"></span>
            </div>
            <div class="chat-item-details">
                <div class="chat-item-header">
                    <span class="chat-item-name">${safeTitle}</span>
                    <span class="chat-item-time">${timeFormatted}</span>
                </div>
                <div class="chat-item-body">
                    <span class="chat-item-message">${lastMsg}</span>
                    <div class="chat-item-badges">
                        ${chat.isPinned ? '<i class="fa-solid fa-thumbtack pinned-badge"></i>' : ''}
                        ${unread > 0 ? `<span class="unread-badge">${unread}</span>` : ''}
                    </div>
                </div>
            </div>
        `;

        item.addEventListener('click', () => selectChat(chat.id));
        chatsContainer.appendChild(item);

        // Fetch online presence for 1-1 chats
        if (!isGroup) {
            const otherId = chat.participants.find(p => p !== myUid);
            if (otherId) {
                onValue(ref(db, `status/${otherId}/online`), (snap) => {
                    const statusDot = document.getElementById(`status-${chat.id}`);
                    if (statusDot) {
                        if (snap.val() === true) {
                            statusDot.className = 'chat-item-status online';
                        } else {
                            statusDot.className = 'chat-item-status';
                        }
                    }
                });
            }
        }
    }

    // Change Conversation Viewport
    async function selectChat(chatId) {
        if (selectedChatId === chatId) return;
        
        // Clean previous listeners
        if (activeMsgListener) activeMsgListener();
        if (activePresenceListener) activePresenceListener();
        if (activeTypingListener) activeTypingListener();
        
        selectedChatId = chatId;
        
        // Active selection UI highlights
        document.querySelectorAll('.chat-item').forEach(el => {
            el.classList.remove('active');
            if (el.dataset.chatId === chatId) el.classList.add('active');
        });

        welcomeViewport.classList.add('hidden');
        activeChatContainer.classList.remove('hidden');

        // Fetch chat settings metadata
        const chatSnap = await getDoc(doc(firestore, "chats", chatId));
        if (chatSnap.exists()) {
            selectedChatData = chatSnap.data();
        } else {
            // Check RTDB
            onValue(ref(db, `chats/${chatId}`), (snapshot) => {
                selectedChatData = snapshot.val();
            }, { onlyOnce: true });
        }

        setTimeout(() => {
            updateActiveChatHeader();
            syncChatMessages(chatId);
            listenTypingStatus(chatId);
            listenPresence(chatId);
            loadSharedMedia(chatId);
        }, 100);
    }

    // Sync header details
    function updateActiveChatHeader() {
        if (!selectedChatData) return;
        
        const isGroup = selectedChatData.type === 'GROUP';
        const title = selectedChatData.title || 'Conversation';
        const photo = selectedChatData.photoUrl || (isGroup ? initialsAvatar('G', 'e2e8f0', '1e293b') : initialsAvatar(title[0]));
        
        activeChatAvatar.src = photo;
        activeChatTitle.innerText = title;
        
        // Wallpaper support
        const wp = selectedChatData.wallpaperUrl || 'default';
        applyWallpaper(wp);
        
        // Disappearing Message active indicator
        const disappearingMs = selectedChatData.disappearingTimer || 0;
        document.querySelectorAll('.timer-opt').forEach(opt => {
            opt.classList.remove('active');
            if (parseInt(opt.dataset.duration) === disappearingMs) opt.classList.add('active');
        });

        if (isGroup) {
            activeChatStatus.innerText = `${selectedChatData.participants?.length || 0} participants`;
            activeUserStatusDot.className = 'active-status-dot';
            groupMembersSection.classList.remove('hidden');
            disappearingMessagesSection.classList.add('hidden');
            loadGroupMembers();
        } else {
            groupMembersSection.classList.add('hidden');
            disappearingMessagesSection.classList.remove('hidden');
        }
    }

    // Sync message threads
    function syncChatMessages(chatId) {
        const messagesRef = ref(db, `messages/${chatId}`);
        
        activeMsgListener = onValue(messagesRef, (snapshot) => {
            messagesContainer.innerHTML = '';
            
            if (!snapshot.exists()) {
                messagesContainer.innerHTML = '<div class="list-placeholder"><p>No messages in this chat. Say hello!</p></div>';
                return;
            }

            snapshot.forEach((child) => {
                const message = child.val();
                message.id = child.key;
                
                // Expire disappearing messages on the fly
                if (message.expiresAt && Date.now() > message.expiresAt && !message.isDeleted) {
                    // Soft delete remotely
                    update(ref(db, `messages/${chatId}/${message.id}`), {
                        isDeleted: true,
                        content: "This message was deleted due to disappearing timer"
                    });
                }
                
                renderMessage(message);
            });

            // Smooth Scroll
            messagesContainer.scrollTop = messagesContainer.scrollHeight;
        });
    }

    // Render message bubbles
    function renderMessage(msg) {
        const wrap = document.createElement('div');
        const isMe = msg.senderId === myUid;
        wrap.className = `msg-wrapper ${isMe ? 'sent' : 'received'}`;
        wrap.dataset.messageId = msg.id;

        const time = formatTime(msg.timestamp);
        const safeName = escapeHtml(isMe ? 'You' : (msg.senderName || 'Contact'));
        
        let contentHtml = '';

        if (msg.isDeleted) {
            contentHtml = `<div class="msg-bubble deleted"><i class="fa-solid fa-ban"></i> This message was deleted</div>`;
        } else {
            let msgContent = msg.content;
            let safeMsgContent = escapeHtml(msgContent);
            
            // Forwarded indicator
            let forwardHeader = '';
            if (msg.forwardedFrom) {
                const safeOrigSender = escapeHtml(msg.forwardedFrom.originalSenderName);
                forwardHeader = `<div class="reply-ref-in-bubble"><i class="fa-solid fa-share-nodes"></i> Forwarded from <strong>${safeOrigSender}</strong></div>`;
            }

            // Reply Reference indicator
            let replyHeader = '';
            if (msg.replyToMessageContent) {
                const safeReplyContent = escapeHtml(msg.replyToMessageContent.substring(0, 40));
                replyHeader = `<div class="reply-ref-in-bubble"><i class="fa-solid fa-reply"></i> Replying to: <em>${safeReplyContent}...</em></div>`;
            }

            const safeUrl = sanitizeUrl(msgContent);
            const safeUrlEscaped = escapeHtml(safeUrl);

            if (msg.messageType === 'IMAGE') {
                contentHtml = `
                    <div class="msg-bubble">
                        ${forwardHeader}
                        ${replyHeader}
                        <img src="${safeUrlEscaped}" class="shared-image-bubble" style="max-width: 250px; border-radius: 12px; margin-bottom: 4px; cursor: pointer;" onclick="window.open('${safeUrlEscaped}')">
                        <div class="msg-footer">${time} ${isMe ? renderTicks(msg.isRead) : ''}</div>
                    </div>
                `;
            } else if (msg.messageType === 'VIDEO') {
                contentHtml = `
                    <div class="msg-bubble">
                        ${forwardHeader}
                        ${replyHeader}
                        <video src="${safeUrlEscaped}" controls class="shared-video-bubble" style="max-width: 250px; border-radius: 12px; margin-bottom: 4px;"></video>
                        <div class="msg-footer">${time} ${isMe ? renderTicks(msg.isRead) : ''}</div>
                    </div>
                `;
            } else if (msg.messageType === 'VOICE') {
                contentHtml = `
                    <div class="msg-bubble">
                        ${forwardHeader}
                        ${replyHeader}
                        <div class="audio-player-bubble">
                            <button class="play-pause-btn" data-voice-url="${safeUrlEscaped}"><i class="fa-solid fa-play"></i></button>
                            <div class="audio-timeline-track">
                                <div class="audio-timeline-fill"></div>
                            </div>
                        </div>
                        <div class="msg-footer">${time} ${isMe ? renderTicks(msg.isRead) : ''}</div>
                    </div>
                `;
            } else if (msg.messageType === 'FILE') {
                contentHtml = `
                    <div class="msg-bubble">
                        ${forwardHeader}
                        ${replyHeader}
                        <a href="${safeUrlEscaped}" target="_blank" style="color: white; text-decoration: underline; display: flex; align-items: center; gap: 8px;">
                            <i class="fa-solid fa-file-invoice"></i> Download Document
                        </a>
                        <div class="msg-footer">${time} ${isMe ? renderTicks(msg.isRead) : ''}</div>
                    </div>
                `;
            } else {
                // Plain Text / Code
                contentHtml = `
                    <div class="msg-bubble">
                        ${forwardHeader}
                        ${replyHeader}
                        <p class="msg-text">${safeMsgContent}</p>
                        <div class="msg-footer">${time} ${isMe ? renderTicks(msg.isRead) : ''}</div>
                    </div>
                `;
            }
        }

        wrap.innerHTML = `
            ${!isMe ? `<span class="msg-meta-header">${safeName}</span>` : ''}
            <div class="msg-bubble-container">
                ${contentHtml}
                <button class="msg-options-trigger" onclick="openMsgOptions(event, '${escapeHtml(msg.id)}', ${isMe}, ${msg.isDeleted})">
                    <i class="fa-solid fa-chevron-down"></i>
                </button>
            </div>
            <div class="msg-reactions-line" id="reactions-${escapeHtml(msg.id)}"></div>
        `;

        messagesContainer.appendChild(wrap);
        renderReactions(msg);

        // Mark as read automatically
        if (!isMe && !msg.isRead) {
            update(ref(db, `messages/${selectedChatId}/${msg.id}`), { isRead: true });
        }
    }

    // Play Voice Note Playback (event delegation for dynamically created buttons)
    messagesContainer.addEventListener('click', function(e) {
        const btn = e.target.closest('.play-pause-btn');
        if (!btn) return;
        const url = btn.dataset.voiceUrl;
        if (!url) return;
        
        let audio = btn.audioObj;
        const fill = btn.parentElement.querySelector('.audio-timeline-fill');
        
        if (!audio) {
            audio = new Audio(url);
            btn.audioObj = audio;
            
            audio.addEventListener('timeupdate', () => {
                const pct = (audio.currentTime / audio.duration) * 100;
                fill.style.width = pct + '%';
            });
            
            audio.addEventListener('ended', () => {
                btn.innerHTML = '<i class="fa-solid fa-play"></i>';
                fill.style.width = '0%';
            });
        }

        if (audio.paused) {
            audio.play();
            btn.innerHTML = '<i class="fa-solid fa-pause"></i>';
        } else {
            audio.pause();
            btn.innerHTML = '<i class="fa-solid fa-play"></i>';
        }
    });

    // Render ticks
    function renderTicks(isRead) {
        return isRead ? '<i class="fa-solid fa-check-double read-ticks read"></i>' : '<i class="fa-solid fa-check read-ticks"></i>';
    }

    // Escape HTML
    function escapeHtml(unsafe) {
        if (!unsafe) return '';
        return String(unsafe)
             .replace(/&/g, "&amp;")
             .replace(/</g, "&lt;")
             .replace(/>/g, "&gt;")
             .replace(/"/g, "&quot;")
             .replace(/'/g, "&#039;");
    }

    // Sanitize URL to prevent javascript: XSS
    function sanitizeUrl(url) {
        if (!url) return '';
        if (url.startsWith('javascript:')) return '';
        if (url.startsWith('data:') && !url.startsWith('data:image/')) return '';
        return url;
    }

    // Render reactions pills
    function renderReactions(msg) {
        const container = document.getElementById(`reactions-${msg.id}`);
        if (!container) return;
        container.innerHTML = '';
        
        if (!msg.reactions) return;
        
        const countMap = {};
        Object.entries(msg.reactions).forEach(([uid, emoji]) => {
            countMap[emoji] = (countMap[emoji] || 0) + 1;
        });

        Object.entries(countMap).forEach(([emoji, count]) => {
            const pill = document.createElement('span');
            pill.className = 'reaction-pill';
            pill.innerHTML = `<span>${emoji}</span> <span class="react-count">${count}</span>`;
            pill.addEventListener('click', () => {
                toggleEmojiReaction(msg.id, emoji);
            });
            container.appendChild(pill);
        });
    }

    // Send Message
    async function handleSendMessage(scheduledTime = null) {
        const text = messageInput.value.trim();
        if (!text || !selectedChatId) return;

        messageInput.value = '';
        messageInput.style.height = 'auto';

        const payload = {
            senderId: myUid,
            senderName: myProfile?.displayName || 'Web User',
            content: text,
            timestamp: Date.now(),
            messageType: 'TEXT',
            isRead: false
        };

        if (replyToMsg) {
            payload.replyToMessageId = replyToMsg.id;
            payload.replyToMessageContent = replyToMsg.content;
            
            // Clear reply reference
            replyToMsg = null;
            replyPreviewBar.classList.add('hidden');
        }

        if (scheduledTime) {
            // Save to scheduled messages
            const schedRef = push(ref(db, `scheduledMessages/${myUid}`));
            set(schedRef, {
                id: schedRef.key,
                chatId: selectedChatId,
                senderId: myUid,
                content: text,
                messageType: 'TEXT',
                scheduledTime: scheduledTime,
                isSent: false,
                createdAt: Date.now()
            });
            alert("Message scheduled successfully!");
        } else {
            // Write to RTDB
            const newMsgRef = push(ref(db, `messages/${selectedChatId}`));
            payload.id = newMsgRef.key;
            
            // Apply disappearing messages if configured
            const disappearingMs = selectedChatData?.disappearingTimer || 0;
            if (disappearingMs > 0) {
                payload.expiresAt = Date.now() + disappearingMs;
            }
            
            await set(newMsgRef, payload);
            
            // Update metadata
            update(ref(db, `chats/${selectedChatId}`), {
                lastMessage: payload,
                lastMessageTimestamp: Date.now()
            });
            
            // Clear Typing Indicator
            remove(ref(db, `typing/${selectedChatId}/${myUid}`));
        }
    }

    // Message options dialog trigger
    window.openMsgOptions = function(event, messageId, isMe, isDeleted) {
        event.stopPropagation();
        reactionTargetMessageId = messageId;
        reactionTargetMsgIsMe = isMe;
        deleteMsgBtn.style.display = isDeleted ? 'none' : '';

        // Setup options menu floating positioning
        const rect = event.currentTarget.getBoundingClientRect();
        reactionsPickerFloating.style.top = (rect.top - 50) + 'px';
        reactionsPickerFloating.style.left = (rect.left - 80) + 'px';
        reactionsPickerFloating.classList.remove('hidden');

        // Close when clicking outside
        const closePicker = () => {
            reactionsPickerFloating.classList.add('hidden');
            document.removeEventListener('click', closePicker);
        };
        setTimeout(() => document.addEventListener('click', closePicker), 50);
    };

    // Toggle Reaction emoji
    async function toggleEmojiReaction(messageId, emoji) {
        const reactionRef = ref(db, `messages/${selectedChatId}/${messageId}/reactions/${myUid}`);
        const snap = await getDoc(reactionRef);
        if (snap.exists() && snap.val() === emoji) {
            remove(reactionRef);
        } else {
            set(reactionRef, emoji);
        }
    }

    // Typing Indicators write
    messageInput.addEventListener('input', () => {
        if (!selectedChatId || !myUid) return;

        set(ref(db, `typing/${selectedChatId}/${myUid}`), Date.now());
        
        clearTimeout(typingTimeout);
        typingTimeout = setTimeout(() => {
            remove(ref(db, `typing/${selectedChatId}/${myUid}`));
        }, 2000);
    });

    // Send on Enter
    messageInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            handleSendMessage();
        }
    });

    // Listen typing status
    function listenTypingStatus(chatId) {
        const typingRef = ref(db, `typing/${chatId}`);
        const textIndicator = document.getElementById('typing-indicator');
        
        activeTypingListener = onValue(typingRef, (snapshot) => {
            if (!snapshot.exists()) {
                textIndicator.classList.add('hidden');
                return;
            }
            
            const typists = [];
            snapshot.forEach(child => {
                if (child.key !== myUid && Date.now() - child.val() < 4000) {
                    typists.push(child.key);
                }
            });

            if (typists.length > 0) {
                textIndicator.classList.remove('hidden');
            } else {
                textIndicator.classList.add('hidden');
            }
        });
    }

    // Listen online presence for header
    function listenPresence(chatId) {
        const isGroup = selectedChatData?.type === 'GROUP';
        if (isGroup) return;

        const otherId = selectedChatData?.participants?.find(p => p !== myUid);
        if (!otherId) return;

        activePresenceListener = onValue(ref(db, `status/${otherId}`), (snapshot) => {
            const data = snapshot.val();
            if (data) {
                if (data.online === true) {
                    activeChatStatus.innerText = 'online';
                    activeUserStatusDot.className = 'active-status-dot online';
                } else {
                    const ago = formatRelativeTime(data.lastSeen);
                    activeChatStatus.innerText = `last seen ${ago}`;
                    activeUserStatusDot.className = 'active-status-dot';
                }
            } else {
                activeChatStatus.innerText = 'offline';
                activeUserStatusDot.className = 'active-status-dot';
            }
        });
    }

    // Group creation Confirm
    confirmGroupBtn.addEventListener('click', async () => {
        const name = groupNameInput.value.trim();
        if (!name) {
            alert("Please enter a group subject!");
            return;
        }

        const selectedMembers = [myUid];
        document.querySelectorAll('.group-cb:checked').forEach(cb => {
            selectedMembers.push(cb.value);
        });

        if (selectedMembers.length < 2) {
            alert("Please select at least 1 other participant!");
            return;
        }

        try {
            const chatId = push(ref(db, 'chats')).key;
            const participantsMap = {};
            selectedMembers.forEach(m => participantsMap[m] = true);
            
            const groupData = {
                id: chatId,
                type: "GROUP",
                title: name,
                participants: selectedMembers,
                participants_map: participantsMap,
                creatorId: myUid,
                createdAt: Date.now(),
                lastMessageTimestamp: Date.now()
            };

            // Save to RTDB and Firestore
            await set(ref(db, `chats/${chatId}`), groupData);
            await setDoc(doc(firestore, "chats", chatId), groupData);

            createGroupModal.classList.add('hidden');
            groupNameInput.value = '';
            
            // Switch to new chat
            selectChat(chatId);
        } catch (e) {
            alert("Error creating group: " + e.message);
        }
    });

    // Profile updates
    confirmProfileBtn.addEventListener('click', async () => {
        const name = profileNameInput.value.trim();
        const bio = profileBioInput.value.trim();
        const photo = profileAvatarUrl.value.trim();

        if (!name) {
            alert("Name cannot be empty!");
            return;
        }

        try {
            const updates = {
                displayName: name,
                bio: bio,
                photoUrl: photo,
                updatedAt: Date.now()
            };

            await setDoc(doc(firestore, "users", myUid), updates, { merge: true });
            
            // Sync local profile state
            await initUserProfile();
            
            profileEditorModal.classList.add('hidden');
            alert("Profile updated successfully!");
        } catch (e) {
            alert("Profile update failed: " + e.message);
        }
    });

    // Chat search filter
    chatSearch.addEventListener('input', () => {
        const q = chatSearch.value.trim().toLowerCase();
        let visible = 0;
        document.querySelectorAll('.chat-item').forEach(item => {
            const name = item.querySelector('.chat-item-name')?.innerText.toLowerCase() || '';
            const msg = item.querySelector('.chat-item-message')?.innerText.toLowerCase() || '';
            const show = !q || name.includes(q) || msg.includes(q);
            item.style.display = show ? 'flex' : 'none';
            if (show) visible++;
        });
        let noRes = document.getElementById('chat-search-no-results');
        if (!q || visible > 0) { if (noRes) noRes.remove(); }
        else if (!noRes && activeChats.length > 0) {
            noRes = document.createElement('p');
            noRes.id = 'chat-search-no-results';
            noRes.className = 'empty-list-placeholder';
            noRes.textContent = 'No chats match "' + chatSearch.value.trim() + '"';
            chatsContainer.appendChild(noRes);
        }
    });

    // Full-text inner search
    searchMsgBtn.addEventListener('click', () => {
        innerSearchWrapper.classList.toggle('hidden');
        innerSearchInput.focus();
    });

    closeSearchBtn.addEventListener('click', () => {
        innerSearchWrapper.classList.add('hidden');
        innerSearchInput.value = '';
        removeHighlights();
    });

    innerSearchInput.addEventListener('input', () => {
        const q = innerSearchInput.value.trim().toLowerCase();
        removeHighlights();
        if (!q) return;

        document.querySelectorAll('.msg-text').forEach(el => {
            const text = el.innerText;
            if (text.toLowerCase().includes(q)) {
                const regex = new RegExp(`(${q})`, 'gi');
                el.innerHTML = text.replace(regex, `<span class="highlight">$1</span>`);
            }
        });
    });

    function removeHighlights() {
        document.querySelectorAll('.msg-text').forEach(el => {
            el.innerHTML = escapeHtml(el.innerText);
        });
    }

    // Attachment popup triggers
    attachBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        attachmentsPopup.classList.toggle('hidden');
    });

    document.addEventListener('click', () => {
        attachmentsPopup.classList.add('hidden');
    });

    // Photo upload via file picker
    document.getElementById('attach-photo').addEventListener('click', () => {
        document.getElementById('image-file-input').click();
    });

    document.getElementById('image-file-input').addEventListener('change', (e) => {
        const file = e.target.files[0];
        if (!file) return;
        const reader = new FileReader();
        reader.onload = (ev) => {
            sendMediaMessage('IMAGE', ev.target.result);
        };
        reader.readAsDataURL(file);
        e.target.value = '';
    });

    document.getElementById('attach-video').addEventListener('click', () => {
        document.getElementById('video-file-input').click();
    });

    document.getElementById('video-file-input').addEventListener('change', (e) => {
        const file = e.target.files[0];
        if (!file) return;
        const reader = new FileReader();
        reader.onload = (ev) => {
            sendMediaMessage('VIDEO', ev.target.result);
        };
        reader.readAsDataURL(file);
        e.target.value = '';
    });

    document.getElementById('attach-doc').addEventListener('click', () => {
        document.getElementById('doc-file-input').click();
    });

    document.getElementById('doc-file-input').addEventListener('change', (e) => {
        const file = e.target.files[0];
        if (!file) return;
        const reader = new FileReader();
        reader.onload = (ev) => {
            sendMediaMessage('FILE', ev.target.result);
        };
        reader.readAsDataURL(file);
        e.target.value = '';
    });

    async function sendMediaMessage(type, url) {
        if (!selectedChatId) return;
        const newMsgRef = push(ref(db, `messages/${selectedChatId}`));
        const payload = {
            id: newMsgRef.key,
            senderId: myUid,
            senderName: myProfile?.displayName || 'Web User',
            content: url,
            timestamp: Date.now(),
            messageType: type,
            isRead: false
        };
        await set(newMsgRef, payload);
        update(ref(db, `chats/${selectedChatId}`), {
            lastMessage: payload,
            lastMessageTimestamp: Date.now()
        });
    }

    // Load shared media library
    async function loadSharedMedia(chatId) {
        const messagesRef = ref(db, `messages/${chatId}`);
        onValue(messagesRef, (snap) => {
            sharedMediaContainer.innerHTML = '';
            let found = false;
            
            if (snap.exists()) {
                snap.forEach(child => {
                    const msg = child.val();
                    if ((msg.messageType === 'IMAGE' || msg.messageType === 'VIDEO') && !msg.isDeleted) {
                        found = true;
                        const item = document.createElement('div');
                        item.className = 'media-item';
                        if (msg.messageType === 'IMAGE') {
                            item.innerHTML = `<img src="${msg.content}" style="width: 100%; height: 60px; object-fit: cover; border-radius: 8px; cursor: pointer;" onclick="window.open('${msg.content}')">`;
                        } else {
                            item.innerHTML = `<div style="width: 100%; height: 60px; background: rgba(255,255,255,0.05); display: flex; align-items: center; justify-content: center; border-radius: 8px;"><i class="fa-solid fa-circle-play" style="color: white; font-size: 1.2rem;"></i></div>`;
                        }
                        sharedMediaContainer.appendChild(item);
                    }
                });
            }

            if (!found) {
                sharedMediaContainer.innerHTML = '<p class="empty-media-placeholder">No shared media yet.</p>';
            }
        }, { onlyOnce: true });
    }

    // Disappearing Messages Selector
    document.querySelectorAll('.timer-opt').forEach(btn => {
        btn.addEventListener('click', async () => {
            const ms = parseInt(btn.dataset.duration);
            if (!selectedChatId) return;

            document.querySelectorAll('.timer-opt').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');

            // Update RTDB & Firestore
            update(ref(db, `chats/${selectedChatId}`), { disappearingTimer: ms });
            updateDoc(doc(firestore, "chats", selectedChatId), { disappearingTimer: ms });
            
            selectedChatData.disappearingTimer = ms;
        });
    });

    // Change wall theme
    document.querySelectorAll('.wp-opt').forEach(opt => {
        opt.addEventListener('click', () => {
            const theme = opt.dataset.wp;
            document.querySelectorAll('.wp-opt').forEach(o => o.classList.remove('active'));
            opt.classList.add('active');
            applyWallpaper(theme);
        });
    });

    function applyWallpaper(wp) {
        if (wp === 'sunset') {
            messagesContainer.style.background = 'linear-gradient(135deg, #f43f5e 0%, #fb923c 100%)';
        } else if (wp === 'ocean') {
            messagesContainer.style.background = 'linear-gradient(135deg, #0ea5e9 0%, #2563eb 100%)';
        } else if (wp === 'emerald') {
            messagesContainer.style.background = 'linear-gradient(135deg, #10b981 0%, #059669 100%)';
        } else if (wp === 'violet') {
            messagesContainer.style.background = 'linear-gradient(135deg, #8b5cf6 0%, #ec4899 100%)';
        } else {
            messagesContainer.style.background = '#0b0f19';
        }
    }

    // Float picker reactions bind
    reactionsPickerFloating.addEventListener('click', (e) => {
        const emoji = e.target.dataset.emoji;
        if (emoji && reactionTargetMessageId) {
            toggleEmojiReaction(reactionTargetMessageId, emoji);
            reactionsPickerFloating.classList.add('hidden');
        }
    });

    // Copy message text
    copyMsgBtn.addEventListener('click', () => {
        if (!reactionTargetMessageId || !selectedChatId) return;
        onValue(ref(db, `messages/${selectedChatId}/${reactionTargetMessageId}`), (snap) => {
            if (snap.exists()) {
                const text = snap.val().content || '';
                navigator.clipboard.writeText(text);
            }
        }, { onlyOnce: true });
        reactionsPickerFloating.classList.add('hidden');
    });

    // Delete message (for everyone)
    deleteMsgBtn.addEventListener('click', async () => {
        if (!reactionTargetMessageId || !selectedChatId) return;
        if (!confirm('Delete this message for everyone?')) return;
        await update(ref(db, `messages/${selectedChatId}/${reactionTargetMessageId}`), {
            isDeleted: true,
            content: 'This message was deleted'
        });
        reactionsPickerFloating.classList.add('hidden');
    });

    // Call handlers — POST to auth server
    async function initiateCall(isVideo) {
        if (!selectedChatData) return;
        const otherId = selectedChatData.participants?.find(p => p !== myUid);
        if (!otherId) { alert('Group calls not supported yet'); return; }
        try {
            const base = (typeof window !== 'undefined' && window.OPENCHAT_CALL_URL) ? window.OPENCHAT_CALL_URL : '';
            if (!base) { alert('Calls require server configuration (OPENCHAT_CALL_URL)'); return; }
            const res = await fetch(base + '/notify/call', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + (sessionToken || '') },
                body: JSON.stringify({
                    targetUserId: otherId,
                    callId: 'call_' + Date.now(),
                    callerName: myProfile?.displayName || 'Web User',
                    isVideo
                })
            });
            const data = await res.json();
            alert(isVideo ? 'Video call notification sent' : 'Voice call notification sent');
        } catch (e) {
            alert('Call server unavailable: ' + e.message);
        }
    }

    voiceCallBtn.addEventListener('click', () => initiateCall(false));
    videoCallBtn.addEventListener('click', () => initiateCall(true));

    // Custom Scheduled send triggers
    scheduleSendBtn.addEventListener('click', () => {
        scheduleSelectModal.classList.remove('hidden');
    });

    confirmScheduleSelect.addEventListener('click', () => {
        const dateStr = scheduleTimeInput.value;
        if (!dateStr) {
            alert("Please select a date and time!");
            return;
        }

        const ms = new Date(dateStr).getTime();
        if (ms < Date.now()) {
            alert("Cannot schedule messages in the past!");
            return;
        }

        scheduleSelectModal.classList.add('hidden');
        handleSendMessage(ms);
    });

    // Load Scheduled List
    navScheduled.addEventListener('click', () => {
        scheduledMessagesModal.classList.remove('hidden');
        
        onValue(ref(db, `scheduledMessages/${myUid}`), (snap) => {
            scheduledListContainer.innerHTML = '';
            let found = false;
            
            if (snap.exists()) {
                snap.forEach(child => {
                    const item = child.val();
                    if (!item.isSent) {
                        found = true;
                        const row = document.createElement('div');
                        row.className = 'modal-user-row';
                        row.innerHTML = `
                            <div class="user-row-left">
                                <div style="display: flex; flex-direction: column;">
                                    <span class="user-row-name">${item.content}</span>
                                    <span class="user-row-phone">Trigger: ${new Date(item.scheduledTime).toLocaleString()}</span>
                                </div>
                            </div>
                            <button class="logout-btn" onclick="cancelSchedMsg('${item.id}')" style="margin: 0;"><i class="fa-solid fa-trash"></i></button>
                        `;
                        scheduledListContainer.appendChild(row);
                    }
                });
            }

            if (!found) {
                scheduledListContainer.innerHTML = '<p class="empty-list-placeholder">No upcoming scheduled messages.</p>';
            }
        });
    });

    window.cancelSchedMsg = function(id) {
        remove(ref(db, `scheduledMessages/${myUid}/${id}`));
        alert("Scheduled message cancelled.");
    };

    // Contacts nav button
    navContacts.addEventListener('click', () => {
        contactSearchInput.value = '';
        createChatModal.classList.remove('hidden');
        renderContactsList(contactsModalList, false);
    });

    // FAQ dialogs
    navHelp.addEventListener('click', () => {
        helpSupportModal.classList.remove('hidden');
    });

    // Close Modals
    closeChatModal.addEventListener('click', () => createChatModal.classList.add('hidden'));
    closeGroupModal.addEventListener('click', () => createGroupModal.classList.add('hidden'));
    cancelGroupBtn.addEventListener('click', () => createGroupModal.classList.add('hidden'));
    closeProfileModal.addEventListener('click', () => profileEditorModal.classList.add('hidden'));
    cancelProfileBtn.addEventListener('click', () => profileEditorModal.classList.add('hidden'));
    closeScheduledModal.addEventListener('click', () => scheduledMessagesModal.classList.add('hidden'));
    closeHelpModal.addEventListener('click', () => helpSupportModal.classList.add('hidden'));
    closeScheduleSelect.addEventListener('click', () => scheduleSelectModal.classList.add('hidden'));
    cancelScheduleSelect.addEventListener('click', () => scheduleSelectModal.classList.add('hidden'));

    // Chat Trigger lists modal click
    createChatBtn.addEventListener('click', () => {
        contactSearchInput.value = '';
        createChatModal.classList.remove('hidden');
        renderContactsList(contactsModalList, false);
    });

    // Contact search filter
    contactSearchInput.addEventListener('input', () => {
        const q = contactSearchInput.value.trim().toLowerCase();
        document.querySelectorAll('#contacts-modal-list .modal-user-row').forEach(row => {
            const name = row.querySelector('.user-row-name')?.innerText.toLowerCase() || '';
            const phone = row.querySelector('.user-row-phone')?.innerText.toLowerCase() || '';
            row.style.display = (!q || name.includes(q) || phone.includes(q)) ? 'flex' : 'none';
        });
    });

    groupContactSearch.addEventListener('input', () => {
        const q = groupContactSearch.value.trim().toLowerCase();
        document.querySelectorAll('#group-contacts-list .modal-user-row').forEach(row => {
            const name = row.querySelector('.user-row-name')?.innerText.toLowerCase() || '';
            const phone = row.querySelector('.user-row-phone')?.innerText.toLowerCase() || '';
            row.style.display = (!q || name.includes(q) || phone.includes(q)) ? 'flex' : 'none';
        });
    });

    // Group Trigger lists modal click
    createGroupBtn.addEventListener('click', () => {
        groupContactSearch.value = '';
        createGroupModal.classList.remove('hidden');
        renderContactsList(groupContactsList, true);
    });

    function renderContactsList(container, withCheckbox) {
        container.innerHTML = '';
        if (activeContacts.length === 0) {
            container.innerHTML = '<p class="empty-list-placeholder">No other registered contacts found.</p>';
            return;
        }

        activeContacts.forEach(user => {
            const row = document.createElement('div');
            row.className = 'modal-user-row';
            
            const displayName = user.displayName || user.name || user.email || 'OpenChat User';
            const photo = user.photoUrl || user.picture || initialsAvatar((displayName || 'U')[0], 'cbd5e1', '1e293b');
            
            row.innerHTML = `
                <div class="user-row-left">
                    <img src="${escapeHtml(photo)}" class="user-row-avatar">
                    <div style="display: flex; flex-direction: column;">
                        <span class="user-row-name">${escapeHtml(displayName)}</span>
                        <span class="user-row-phone">${escapeHtml(user.phoneNumber || user.phone || 'OpenChat User')}</span>
                    </div>
                </div>
                ${withCheckbox ? `<input type="checkbox" class="group-cb" value="${escapeHtml(user.id)}" style="width: 18px; height: 18px;">` : ''}
            `;

            if (!withCheckbox) {
                row.addEventListener('click', () => {
                    createChatModal.classList.add('hidden');
                    startNewPrivateChat(user.id, user.displayName);
                });
            }

            container.appendChild(row);
        });
    }

    // Start Chat sequence
    async function startNewPrivateChat(otherId, otherName) {
        const generatedId = myUid < otherId ? `${myUid}_${otherId}` : `${otherId}_${myUid}`;
        
        // Save Chat Session in RTDB & Firestore
        const chatPayload = {
            id: generatedId,
            type: "PRIVATE",
            title: otherName,
            participants: [myUid, otherId],
            participants_map: { [myUid]: true, [otherId]: true },
            createdAt: Date.now(),
            lastMessageTimestamp: Date.now()
        };

        await set(ref(db, `chats/${generatedId}`), chatPayload);
        await setDoc(doc(firestore, "chats", generatedId), chatPayload);

        selectChat(generatedId);
    }

    // Group Members drawer list
    async function loadGroupMembers() {
        membersContainer.innerHTML = '';
        if (!selectedChatData || !selectedChatData.participants) return;
        
        membersCount.innerText = selectedChatData.participants.length;
        
        selectedChatData.participants.forEach(uid => {
            const photo = uid === myUid ? myProfile?.photoUrl : activeContacts.find(c => c.id === uid)?.photoUrl;
            const name = uid === myUid ? (myProfile?.displayName || 'You') : (activeContacts.find(c => c.id === uid)?.displayName || 'Group Participant');
            
            const row = document.createElement('div');
            row.className = 'modal-user-row';
            row.style.background = 'transparent';
            row.style.padding = '6px 0';
            row.innerHTML = `
                <div class="user-row-left">
                    <img src="${photo || initialsAvatar(name[0], 'cbd5e1', '1e293b')}" class="user-row-avatar" style="width: 32px; height: 32px;">
                    <span class="user-row-name" style="font-size: 0.85rem;">${name}</span>
                </div>
            `;
            membersContainer.appendChild(row);
        });
    }

    // Modal view Profile clicks
    sidebarProfileBtn.addEventListener('click', () => {
        profileEditorModal.classList.remove('hidden');
    });

    // Toggle light theme
    themeToggleBtn.addEventListener('click', () => {
        document.body.classList.toggle('light-theme');
        const isLight = document.body.classList.contains('light-theme');
        themeToggleBtn.innerHTML = isLight ? '<i class="fa-solid fa-sun"></i>' : '<i class="fa-solid fa-moon"></i>';
    });

    // Profile photo preview live binding
    profileAvatarUrl.addEventListener('input', () => {
        profileAvatarPreview.src = profileAvatarUrl.value.trim() || initialsAvatar('U');
    });

    // Details Drawer toggle
    toggleDetailsBtn.addEventListener('click', () => {
        if (!selectedChatId) return;
        
        drawerChatTitle.innerText = activeChatTitle.innerText;
        drawerChatAvatar.src = activeChatAvatar.src;
        drawerChatSubtitle.innerText = selectedChatData?.type === 'GROUP' ? 'Group Chat' : 'Direct Conversation';
        
        detailsDrawer.classList.toggle('hidden');
    });

    closeDrawerBtn.addEventListener('click', () => {
        detailsDrawer.classList.add('hidden');
    });

    // Logout
    logoutBtn.addEventListener('click', () => {
        if (confirm("Disconnect web companion session?")) {
            // Delete session remotely
            if (sessionToken) {
                remove(ref(db, 'sessions/' + sessionToken));
            }
            updatePresence(false);
            
            // Reload page
            window.location.reload();
        }
    });

    // Helper utilities
    function formatTime(timestamp) {
        if (!timestamp) return '';
        const d = new Date(timestamp);
        return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    }

    function formatRelativeTime(timestamp) {
        if (!timestamp) return 'offline';
        const sec = Math.floor((Date.now() - timestamp) / 1000);
        if (sec < 60) return 'just now';
        const min = Math.floor(sec / 60);
        if (min < 60) return `${min}m ago`;
        const hrs = Math.floor(min / 60);
        if (hrs < 24) return `${hrs}h ago`;
        return new Date(timestamp).toLocaleDateString();
    }

    // Set message listener options panel
    window.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') {
            createChatModal.classList.add('hidden');
            createGroupModal.classList.add('hidden');
            profileEditorModal.classList.add('hidden');
            scheduledMessagesModal.classList.add('hidden');
            helpSupportModal.classList.add('hidden');
            detailsDrawer.classList.add('hidden');
            scheduleSelectModal.classList.add('hidden');
        }
    });

    // Start Session cryptographic QR code linking on load
    startSessionSetup();
});
