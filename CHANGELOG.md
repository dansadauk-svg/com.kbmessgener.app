# Changelog

## 2.0.1 Android / 1.4.9 WordPress
- Restored the existing KBMovies.ng WordPress API as the app backend.
- Removed the Cloudflare Worker URL requirement from GitHub Actions.
- Kept picture and voice-note file bytes off WordPress with direct R2 uploads.
- Copies selected images to a replayable temporary file before upload.
- Retries failed direct R2 uploads up to three times without duplicating chat messages.
- Reports the actual R2 HTTP error and separates upload failures from WordPress save failures.
- Validates R2 media keys and rebuilds trusted media URLs on the server.
- Uses five-second inbox and two-second open-chat refresh intervals.

## 2.0.0 Android / Cloudflare Edge
- Moved live chat delivery from WordPress polling to a Cloudflare Worker.
- Added D1 chat storage and Durable Object WebSocket foreground delivery.
- Added Cloudflare Queues for FCM, WhatsApp read receipts, media copying and retries.
- Kept media private in R2 with five-minute upload and fifteen-minute playback URLs.
- Added direct Android-to-R2 image and voice uploads with progress.
- Added WhatsApp sent, delivered, read and failed message indicators.
- Added automatic WebSocket reconnect and low-frequency safety refresh.
- Kept WordPress only for agent identity and subscription operations.
- Added manual chat close with same-agent resume when that agent remains online.

## 1.4.5 WordPress / 1.4.7 Android
- Added a system-recorder/file-capture fallback for Web-to-APK installations without microphone permission.
- Added a server-assisted R2 fallback when direct customer-care voice uploads fail.
- Normalized common Android M4A audio MIME types.

## 1.4.4 WordPress
- Removed ordinary WordPress Administrator accounts from the public customer-care selector.
- Only users explicitly assigned the Customer Care Agent role can appear to customers.

## 1.4.6 Android
- Added one-tap `TSARIN MU` subscription-plan quick reply.
- Added one-tap `ACCOUNT` Moniepoint payment-details quick reply.
- Quick replies preserve professional line spacing and Naira formatting.

## 1.4.5 Android
- Removed the incompatible Kotlin file-level Media3 opt-in that Android Lint flagged.
- Retained AndroidX's correct function-level `OptIn` and targeted lint suppression.

## 1.4.4 Android / 1.4.3 WordPress
- Conversation messages, pictures and voice notes remain stored after close or inactivity.
- The website and Android app reload the same saved thread when it is reopened.
- Initial history loading now retrieves the newest 500 messages immediately.
- Live polling now requests only messages newer than the last message already displayed.

## 1.4.3 Android / 1.4.2 WordPress
- Added Android Lint's Media3 opt-in and `UnsafeOptInUsageError` suppression to the inline player.
- Customer-care login now survives app restarts and temporary network/server failures.
- Added cached staff identity for offline startup.
- WordPress now supports multiple permanent device sessions per customer-care account without one phone logging out another.

## 1.4.2
- Added the required Media3 `UnstableApi` opt-in for the inline ExoPlayer voice-note player.
- Fixed the Android lint failure in `MainActivity.kt`.

## 1.4.1
- Changed customer-care presence wording from Available to Online.
- Corrected the oversized mobile chat header and refined the full-screen layout.
- Closing the chat now returns the user to the agent selector.
- Selecting the same customer-care agent resumes the existing conversation and history.
- Added a professional agent selector and empty-conversation notice.

## 1.4.0
- Fixed R2 image and voice-note uploads by separating signed uploads from authenticated API requests.
- Streamed media directly instead of loading complete files into memory.
- Added visible recording timer and media upload percentage.
- Added full-screen in-app picture viewing and inline voice-note playback.
- Reduced active chat refresh time to one second.

## 1.1.0

- Rebuilt the inbox and conversation screen with a WhatsApp-inspired layout.
- Added customer-care and customer profile pictures with avatar fallback.
- Added the KB Movies logo and native launcher icon.
- Added safe-area padding for camera cutouts and system bars.
- Deduplicated messages by database ID on Android and the web widget.
- Added notification payload delivery and Firebase error reporting.

## 1.0.4

- Added the explicit OkHttp media type and request body extension imports.

## 1.0.3

- Enabled AndroidX in Gradle properties for GitHub Actions builds.

## 1.0.0

- Native Kotlin/Compose customer-care app.
- WordPress agent authentication and availability.
- Assigned conversation inbox and WhatsApp-style chat UI.
- Text, picture and voice-note messages.
- Direct Cloudflare R2 media uploads.
- Firebase background notifications.
- Website agent-selection chat widget.
- GitHub Actions APK build workflow.
