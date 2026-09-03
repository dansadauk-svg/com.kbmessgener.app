# WordPress API summary

Base: `https://kbmovies.ng/wp-json/kbcc/v1/`

- `POST login` — customer-care login and persistent app token.
- `GET me` — agent profile.
- `POST availability` — online/offline status.
- `POST device` — Firebase device-token registration.
- `GET conversations` — agent inbox.
- `GET conversations/{id}/messages` — incremental saved history and read state.
- `POST conversations/{id}/close` — manual chat close.
- `POST activity` — typing or recording indicator.
- `POST media/presign` — temporary direct R2 PUT URL.
- `POST messages` — saves text or completed R2 media metadata.

For media, Android first obtains a signed URL, uploads the bytes directly to
R2, and only then asks WordPress to save the message. R2 traffic therefore does
not pass through PHP or the Cloudways application server.
