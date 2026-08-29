# Firebase setup

1. Create/select the KB Movies Firebase project.
2. Add Android package `ng.kbmovies.customercare`.
3. Put `google-services.json` in `android-app/app/`.
4. Enable Firebase Cloud Messaging.
5. Create a dedicated service account with FCM send permission.
6. Copy `project_id`, `client_email`, and `private_key` into WordPress under
   **Settings > Native Customer Care**.

For GitHub Actions, base64-encode the Android `google-services.json` and save it
as `GOOGLE_SERVICES_JSON`. Never put a service-account private key in GitHub.

The app registers its FCM token after login and whenever Firebase rotates it.
WordPress sends notifications only to devices belonging to the assigned agent.
