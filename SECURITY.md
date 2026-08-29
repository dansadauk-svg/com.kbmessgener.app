# Security policy

- Production traffic must use HTTPS.
- WordPress passwords are never stored in the app.
- WordPress stores only SHA-256 hashes of random app tokens.
- R2 PUT URLs expire after 15 minutes.
- Upload MIME type and size are validated server-side.
- Public write endpoints are rate limited.
- Firebase OAuth credentials remain on WordPress only.
- Never commit service-account keys, release keystores, passwords or live tokens.
