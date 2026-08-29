# WordPress setup

1. Install `kb-native-customer-care-v1.0.0.zip`.
2. Keep KB Movies R2 Direct Uploader active.
3. Deactivate KB Movies Support Chat; this bridge replaces its widget.
4. Assign staff the **Customer Care Agent** role.
5. Agents sign into Android and switch to **Available**.
6. Only available agents appear in the website selector.
7. Exclude `/wp-json/kbcc/v1/*` from Breeze/Varnish response caching.

Media uploads go directly to R2 with short-lived signed URLs. WordPress stores
conversation records and media metadata, not the media bytes.
