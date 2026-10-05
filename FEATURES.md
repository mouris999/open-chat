# OpenChat — 10 Highest-Impact Feature Ideas

Ranked by impact per unit of engineering effort. The first four build on code that
already exists in this repo, so they are the cheapest wins.

---

## 1. Real end-to-end encryption (Signal Protocol)

**Why first:** `SignalProtocolManager.kt` exists but is not wired into the send/receive
path, and `SignalProtocolManagerTest` is the only test in the project. This is the single
biggest differentiator for a messenger and the single biggest trust gap.

**Shape of the work:**
- Persist an X25519 identity keypair + signed prekey per device in
  `users/{uid}/devices/{deviceId}` (the entity and DAOs for this do not exist yet).
- `sendMessage` encrypts the Signal envelope for each participant device; store the
  ciphertext, never the plaintext.
- Add a per-chat safety-number UI derived from both identity keys, with a
  "Safety number changed" warning — this is what makes E2EE legible to users.
- Multi-device: fan out one ciphertext per device, not per user.

**Leverage:** the X25519/DH helpers, key serialization, and the test scaffolding already
exist. This is wiring plus a device model, not cryptography from scratch.

---

## 2. Message search that actually works end-to-end

**Why:** `searchMessages` reads the whole `messages/{chatId}` node over the network on
every keystroke, `MessageDao.searchMessages` filters on `type = 'TEXT'` (so media and
captions are unsearchable), and `MessageDao.getMessagesPagingSource` is dead code while
`getMessagesPaging` wraps a full list in `PagingData.from`.

**Shape of the work:**
- Use the existing `getMessagesPagingSource` with a real `Pager` instead of
  `PagingData.from`.
- Index message text into a `messages_fts` table (Room FTS4), refreshed by the existing
  message observer.
- Debounce the query, and drop the `type = 'TEXT'` predicate so captions are searchable.
- Search across chats with a per-chat result header and jump-to-message deep link.

**Leverage:** the Paging 3 + Room + DAO wiring is all installed. This is a data-layer
change behind an existing screen.

---

## 3. Offline-first message queue with optimistic delivery

**Why:** `MessageDao` is used almost exclusively for `clearAll*`, and local ids are
`System.currentTimeMillis().toString()` while the repository *replaces* the id with a
Firebase push key. So the optimistic row and the database row have different ids and the
rollback at `ChatViewModel` (filter by `message.id`) can never remove the duplicate.

**Shape of the work:**
- One `UUID` per outgoing message, used for local and remote rows alike; return the id
  from `sendMessage` so rollback matches.
- A Room-backed outbox: write locally, enqueue, flush on connectivity.
- Per-message states (pending / sent / delivered / failed) with a retry affordance,
  driven by `NetworkMonitor` (already present).
- Flush from a `WorkManager` worker so it survives process death.

**Leverage:** Room, WorkManager, `NetworkMonitor`, and the status field on the message
entity are all already wired.

---

## 4. Group calls (mesh via SFU)

**Why:** `CallScreen` already handles `CallType.GROUP_VIDEO` and the WebRTC layer works
for 1:1, but there is no group path. This is the most-requested feature class in any
messenger.

**Shape of the work:**
- Move from a 1:1 `PeerConnection` to one peer connection per participant.
- Choose topology: mesh up to ~4 peers, then a selective-forwarding-unit (mediasoup /
  LiveKit / Cloudflare Calls) beyond that. Getting this decision right early matters.
- Group ring: the incoming-call listener currently only reads
  `webrtc_signaling/{myUid}`; add a `groups/{groupId}/calls` node.
- Grid layout with per-participant mute/camera state, plus a focused-speaker view.

**Leverage:** `WebRTCManager` (46 KB of peer-connection, TURN, and ICE logic), the call
service, the incoming-call activity, and the call screen all exist and are reusable.

---

## 5. Disappearing messages that actually disappear

**Why:** `disappearingTimer` is stored and displayed, and `VanishModeIndicator` renders
an in-memory countdown — but no server-side expiry and no local cleanup job exist. The
message is still readable in the database and in backups.

**Shape of the work:**
- On read, filter out messages where `createdAt + timer < now` so expiry is
  authoritative regardless of any client.
- A `WorkManager` periodic job to delete expired messages locally.
- Undelete semantics: keep a short grace window, and always preserve messages that were
  forwarded or reported.
- Make the timer a real per-chat setting, with the option to disable it for admins.

**Leverage:** the setting, the UI, and the repository write already exist.

---

## 6. Voice/video message recording with waveform + playback

**Why:** voice sending is currently the weakest path in the app — a 500 KB Base64 limit,
a byte cap described to the user as "max 30 seconds", and an FD leak. It works, barely.

**Shape of the work:**
- Record with AAC/Opus at a real bitrate and upload to Cloudinary (the existing
  `uploadVoice`, with the 500 KB cap already fixed).
- Draw a live amplitude waveform during recording and render a seekable one on playback.
- Hold-to-record UI with a slide-up cancel zone and a max-duration ring.
- Persist playback position so resuming a long note works.

**Leverage:** `VoiceRecorderManager`, `VoiceRecorder`, `CallTranslationService`'s ML Kit
dependency, and Cloudinary upload are all present.

---

## 7. Smart reply suggestions with on-device inference

**Why:** `SmartReplySuggestions.kt` is a UI shell; `AiEngine` is wired to MediaPipe
GenAI but used only for translation. The component exists with nothing behind it.

**Shape of the work:**
- Classify the last inbound message (question / confirmation / scheduling / social) with
  the on-device model.
- Generate 3 ranked suggestions from that class plus conversation recency, cached per
  contact.
- Learn from accept/reject: rank down suggestions the user dismisses.
- Gate on a downloaded-model check so the feature degrades quietly, never errors.

**Leverage:** the widget, the engine, and the download-state plumbing are already built.

---

## 8. Contact-aware message effects (per-contact encryption, disappearing, auto-download)

**Why:** `ContactEntity` has no per-contact settings. Every privacy control today is
global, which is exactly the wrong granularity for a messenger.

**Shape of the work:**
- A `contact_settings` node keyed by contact id: disappearing timer, auto-download
  over cellular, per-contact E2EE, and a "hide read receipts" toggle.
- Columnar sync so the settings screen reads locally and reconciles in the background.
- Enforce at write time in `MessagingRepository`, not just at render time — otherwise a
  client-side check is trivially bypassed by any other client.
- Settings UI: tap a contact in the chat header → a single settings sheet.

**Leverage:** `ContactRepository`, `ContactsScreen`, and the settings navigation
structure all exist.

---

## 9. Backup, export, and device transfer

**Why:** `exportChat` was writing JSON to a directory that is read-only under scoped
storage, so it always failed. Users also lose everything on uninstall with no recovery
path.

**Shape of the work:**
- A real export: write via `MediaStore.Downloads`, and expose a share sheet (needs a
  `FileProvider`, which exists in `file_paths.xml` but is unused).
- End-to-end encrypted backup to Cloudinary (already used for media) or Drive, with a
  user-held recovery phrase.
- Device-to-device transfer over the existing T-Auth device-linking flow
  (`sessions/{token}` + QR), streaming history rather than snapshotting it.
- Scheduled automatic backups with a retention window.

**Leverage:** the T-Auth link flow, QR scanner, Cloudinary, and the (now fixed) export
path are all in place.

---

## 10. Notification intelligence: priority, grouping, and quiet hours

**Why:** notifications are one-size-fits-all, and `MessageNotificationHelper` fires on
every chat-list emission. On a busy account that is both noisy and a battery problem.

**Shape of the work:**
- Priority rules: contacts, groups, mentions, keywords; everything else batches.
- Group by conversation, collapse repeats, and summarise ("3 new messages").
- Quiet hours with per-chat overrides, honouring the user's timezone.
- Wear / tablet / desktop delivery as a single dispatch point, so a message is notified
  once across all surfaces.
- Cap per-conversation notification volume rather than notifying on every message.

**Leverage:** `FCMService`, `MessageNotificationHelper`, `NetworkMonitor`, the
`NetworkMonitor`-driven background state, and the notification-permission flow in
`MainActivity` are all in place.
