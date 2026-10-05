# server/ — Legacy

This server is **UNWIRED**. No Android/Web/T-Auth/WebRTC code imports it.
It duplicates `t-auth-server` auth + `server/index.js` FCM stubs with `twilio` phone OTP stub (logs code, never sends SMS).

Keep intact for reference. Do not deploy. If phone OTP is revived, merge its `Map` logic into `t-auth-server` and add real Twilio + DB persistence.

Checked: `grep -r "server/index" app/ web/ t-auth-server/ webrtc-server/ open-hub/` → zero imports.
