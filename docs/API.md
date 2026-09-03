# Edge API summary

Base: `https://YOUR_WORKER.workers.dev/v1/`

Agent endpoints use `Authorization: Bearer <edge-session-token>`.

- `POST login` — validates the agent through WordPress and creates a 180-day edge session.
- `GET me` — agent profile.
- `POST availability` — online/offline state used for assignment.
- `POST device` — FCM device-token registration.
- `GET conversations` — active agent inbox.
- `GET conversations/{id}/messages` — history, incremental updates and read acknowledgement.
- `POST conversations/{id}/close` — manually ends the active chat.
- `POST messages` — sends text or already-uploaded media to WhatsApp.
- `POST media/presign` — five-minute private R2 direct-upload URL.
- `POST activity` — agent typing/recording activity.
- `GET realtime` — authenticated WebSocket upgrade.

Meta posts to `/webhooks/meta`. The Worker validates Meta's HMAC signature,
stores each message once using its external message ID, broadcasts it to the
agent, queues FCM, and copies media to R2 in the background.

The WordPress `/wp-json/kbcc/v1/login` endpoint remains an internal identity
check. WordPress is not used for live chat polling or media transfer.
