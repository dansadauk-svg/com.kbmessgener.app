# Cloudflare edge setup

Run these commands from `cloudflare-worker/` after signing in to Wrangler.

## 1. Create resources

```bash
npm install
npx wrangler login
npx wrangler d1 create kb-customer-care
npx wrangler queues create kb-customer-care-jobs
npx wrangler queues create kb-customer-care-dead-letter
```

Copy the D1 `database_id` into `wrangler.jsonc`. The configured private R2
bucket is `kbmovies`; change `bucket_name` if the real name differs.

## 2. Add Worker secrets

Run each command and paste only the requested value at Wrangler's prompt:

```bash
npx wrangler secret put META_VERIFY_TOKEN
npx wrangler secret put META_APP_SECRET
npx wrangler secret put META_ACCESS_TOKEN
npx wrangler secret put META_PHONE_NUMBER_ID
npx wrangler secret put CLOUDFLARE_ACCOUNT_ID
npx wrangler secret put R2_BUCKET_NAME
npx wrangler secret put R2_ACCESS_KEY_ID
npx wrangler secret put R2_SECRET_ACCESS_KEY
npx wrangler secret put FIREBASE_PROJECT_ID
npx wrangler secret put FIREBASE_CLIENT_EMAIL
npx wrangler secret put FIREBASE_PRIVATE_KEY
```

`FIREBASE_PRIVATE_KEY` must be the complete service-account `private_key`,
including its BEGIN/END lines. Secrets belong only in the Worker. Rotate any
credential that has previously been shared publicly.

## 3. Create tables and deploy

```bash
npx wrangler d1 migrations apply kb-customer-care --remote
npx wrangler deploy
```

Open `https://YOUR_WORKER.workers.dev/health`; it should return `{"ok":true,...}`.

## 4. Connect Meta WhatsApp

Set the WhatsApp webhook callback URL in Meta Developers to:

`https://YOUR_WORKER.workers.dev/webhooks/meta`

Use the value stored as `META_VERIFY_TOKEN`, verify the webhook, and subscribe
the WhatsApp Business Account to `messages`. Use a permanent production access
token before launch; Meta's temporary test token expires.

## 5. Firebase notifications

Use the Firebase project matching `android-app/app/google-services.json`. Copy
the service-account `project_id`, `client_email`, and complete `private_key` to
the Worker secrets. Install v2.0.0, sign in once, allow notifications, and
confirm the agent appears in the D1 `devices` table.

## 6. Private media retention

Keep R2 private. In R2 > bucket > Settings > Object lifecycle rules, create a
rule for prefix `native-support/` that deletes objects after 90 days. Worker
metadata records the intended deletion date; the lifecycle rule performs it.

Android does not require browser CORS. Never include R2 API keys in the app.

## 7. Build and test

1. Add GitHub repository variable `KBCC_EDGE_API` containing the deployed
   Worker URL ending in `/v1/`.
2. Push to `main` or run Android CI manually.
3. Download the `.apk` asset from the `customer-care-latest` Release, not the
   source ZIP and not the zipped Actions artifact.
4. Test text, image, voice, sent/delivered/read ticks, live foreground delivery,
   and background FCM using two phones.

## Rollout

This is a new message database. Existing WordPress chat history is not copied
automatically. Keep the old system available during testing and plan a cutover.
A migration can be added after confirming the old table schema and retention.
