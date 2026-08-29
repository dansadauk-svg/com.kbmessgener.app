# REST API summary

Base: `/wp-json/kbcc/v1/`

- `POST login` — agent login and bearer token.
- `GET me` — agent profile.
- `POST availability` — online/offline state.
- `POST device` — FCM token registration.
- `GET agents` — available agents for the website.
- `POST customer/start` — create assigned conversation.
- `GET conversations` — agent inbox.
- `GET conversations/{id}/messages` — incremental history.
- `POST messages` — text/image/audio metadata.
- `POST media/presign` — short-lived R2 PUT URL.

Agent calls use `Authorization: Bearer <token>`. Customer calls are tied to the
logged-in WordPress account or signed guest cookie.
