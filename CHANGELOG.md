# Changelog

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
