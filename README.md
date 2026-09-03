# KB Movies Customer Care 2.0

Native Android agent app backed by Cloudflare Workers, Durable Objects,
Queues, D1 and private R2 storage. WordPress remains responsible for agent
login, users, payments and subscription activation; it is no longer the live
message-delivery server.

## Delivery path

- Meta WhatsApp webhook is acknowledged immediately by the Worker.
- D1 stores conversations, text, assignments and delivery/read status.
- A Durable Object WebSocket pushes messages to an open Android app.
- FCM alerts the app while it is backgrounded or closed.
- Queue consumers copy inbound WhatsApp media to private R2 and retry failures.
- The agent app uploads pictures and recorded voice notes directly to R2 with
  short-lived signed PUT URLs.
- Fallback refresh is 30 seconds while live (10 seconds while reconnecting),
  replacing the old 900 ms WordPress polling.

## Folders

- `cloudflare-worker/` — edge API, Meta webhook, WebSocket, queue and D1 schema.
- `android-app/` — Customer Care Android app, version 2.0.0.
- `wordpress-plugin/` — existing WordPress login/subscription bridge.
- `docs/CLOUDFLARE-SETUP.md` — deployment and Meta configuration steps.

## Build the APK

After deploying the Worker, add a GitHub repository variable named
`KBCC_EDGE_API`. Its value must end in `/v1/`, for example:

`https://kb-customer-care-edge.example.workers.dev/v1/`

The workflow refuses to build without this value, preventing distribution of
an APK with a placeholder server address. It creates `Customer-Care-v2.0.0.apk`
and publishes it directly to the `customer-care-latest` GitHub Release.

Do not commit Meta, R2, Firebase service-account or WordPress credentials.
