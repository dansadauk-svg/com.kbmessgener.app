# KB Movies Customer Care 2.0.1

Native Android customer-care app using the existing KBMovies.ng WordPress REST
API. Images and voice notes are uploaded directly from the phone to Cloudflare
R2 with temporary signed URLs; WordPress receives only the small message record.

## Included

- Android Kotlin/Jetpack Compose agent app.
- WordPress bridge plugin version 1.4.9.
- Existing agent login, availability, conversations and Firebase notifications.
- Direct R2 picture and recorded voice-note uploads.
- Replayable upload files and three safe retries for unstable connections.
- Visible recording and upload progress.
- Full-screen image preview and inline voice-note playback.
- Five-second inbox refresh and two-second open-chat refresh.

## Installation order

1. Install/replace `KB Native Customer Care Bridge` with plugin version 1.4.9.
2. Keep `KB Movies R2 Direct Uploader` active and configured.
3. Confirm your R2 public/custom playback domain works.
4. Push this source to GitHub and run `Android CI`.
5. Install `Customer-Care-v2.0.1.apk` from the GitHub Release asset.

The app uses `https://kbmovies.ng/wp-json/kbcc/v1/`. It does not require a
Cloudflare Worker, D1, Queue, or `KBCC_EDGE_API` GitHub variable.

## R2 limits

- Images: JPG, PNG or WebP; maximum 8 MB.
- Voice: M4A/MP4, AAC, MP3, WebM, OGG, 3GP or AMR; maximum 20 MB.
- Upload URLs expire after 15 minutes.

Do not commit Firebase service-account credentials, R2 API keys, passwords,
release keystores, or live access tokens.
