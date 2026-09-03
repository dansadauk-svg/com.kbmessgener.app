# Firebase setup

1. Create/select the KB Movies Firebase project.
2. Add Android package `ng.kbmovies.customercare`.
3. The supplied `google-services.json` is already in `android-app/app/`.
4. Enable Firebase Cloud Messaging.
5. Create a dedicated service account with FCM send permission.
6. Store `project_id`, `client_email`, and the complete `private_key` as the
   Worker secrets described in `CLOUDFLARE-SETUP.md`.

For this project, the Firebase project ID is `chatmkmovies`. Generate the
service-account JSON in Firebase/Google Cloud; it cannot be derived from the
Android `google-services.json` file. Keep that downloaded service-account file
private and never commit it to GitHub.

GitHub Actions reads the included Android `google-services.json` directly. You
do not need a `GOOGLE_SERVICES_JSON` repository secret. Never put a Firebase
service-account private key in GitHub.

The app registers its FCM token with the edge API after login and whenever
Firebase rotates it. The Worker queue sends notifications only to devices
belonging to the assigned agent; WordPress is not in the notification path.
