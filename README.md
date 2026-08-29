# KB Movies Customer Care

Native Android customer-care app plus a WordPress bridge for KBMovies.ng.

## What is included

- Android app written in Kotlin and Jetpack Compose.
- Agent login with WordPress customer-care accounts.
- Available/offline switch controlled by each agent.
- WhatsApp-style conversation list and chat screen.
- Text, image and recorded voice-note messages.
- Firebase Cloud Messaging notifications while the app is backgrounded or closed.
- Cloudflare R2 signed uploads through KB Movies R2 Direct Uploader.
- GitHub Actions debug APK build.
- Installable WordPress plugin in `wordpress-plugin/kb-native-customer-care`.

## Required setup

1. Install and activate the WordPress plugin on KBMovies.ng.
2. Keep KB Movies R2 Direct Uploader active and configured.
3. In WordPress open **Native Customer Care > Settings** and add the Firebase
   project ID, service-account email and private key.
4. Give each agent the WordPress role **Customer Care Agent**.
5. Create an Android Firebase app with package `ng.kbmovies.customercare`.
6. The Firebase Android configuration is already included in `android-app/app/`.
7. Push this repository to GitHub. The included workflow builds a debug APK.

Never commit a Firebase service-account JSON/private key or WordPress password.
`google-services.json` contains Android project identifiers and is included in
this project. Service-account credentials must remain only on the server.

## API base URL

The default is `https://kbmovies.ng/wp-json/kbcc/v1/`. Change `API_BASE_URL` in
`android-app/app/build.gradle.kts` if the site URL changes.

## Production checklist

- Use HTTPS only.
- Exclude `/wp-json/kbcc/v1/*` from page cache, but keep rate limiting enabled.
- Confirm `android-app/app/google-services.json` is present before building.
- Create a signed release keystore in GitHub Secrets; do not commit it.
- Test background notifications on Android 13+ after granting notification permission.
- Publish a privacy policy explaining message and media retention.

## Important scope

Version 1.0.0 is a production-oriented MVP. It uses short polling while the app
is open and FCM for instant background alerts. A later release can add WebSocket
delivery without changing the database/API contract.
