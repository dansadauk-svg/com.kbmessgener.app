# Security policy

- Production traffic must use HTTPS.
- WordPress passwords are never stored in the app.
- D1 stores only SHA-256 hashes of random edge-session tokens.
- R2 PUT URLs expire after 5 minutes; private playback URLs expire after 15 minutes.
- Upload MIME type and size are validated server-side.
- Public write endpoints are rate limited.
- Meta, Firebase and R2 credentials remain in encrypted Worker secrets only.
- The R2 bucket must remain private and use an automatic lifecycle deletion rule.
- Never commit service-account keys, release keystores, passwords or live tokens.
