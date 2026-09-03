import { AwsClient } from "aws4fetch";

const encoder = new TextEncoder();
let fcmCache = { token: "", expires: 0 };
const nowIso = () => new Date().toISOString().replace("T", " ").slice(0, 19);
const json = (value, status = 200, extra = {}) => new Response(JSON.stringify(value), { status, headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store", ...extra } });
const randomToken = () => crypto.randomUUID() + crypto.randomUUID().replaceAll("-", "");
const sha256 = async value => [...new Uint8Array(await crypto.subtle.digest("SHA-256", encoder.encode(value)))].map(x => x.toString(16).padStart(2, "0")).join("");
const b64url = value => btoa(String.fromCharCode(...new Uint8Array(value))).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/, "");

function cors(request, response) {
  const origin = request.headers.get("origin") || "";
  const allowed = origin === "https://kbmovies.ng" ? origin : "https://kbmovies.ng";
  const headers = new Headers(response.headers);
  headers.set("access-control-allow-origin", allowed);
  headers.set("access-control-allow-headers", "authorization,content-type");
  headers.set("access-control-allow-methods", "GET,POST,PUT,OPTIONS");
  headers.set("vary", "Origin");
  return new Response(response.body, { status: response.status, headers });
}

async function body(request) {
  try { return await request.json(); } catch { return {}; }
}

async function session(request, env) {
  const raw = (request.headers.get("authorization") || "").replace(/^Bearer\s+/i, "").trim();
  if (!raw) return null;
  const hash = await sha256(raw);
  return await env.DB.prepare("SELECT s.agent_id,a.name,a.avatar,a.available FROM sessions s JOIN agents a ON a.id=s.agent_id WHERE s.token_hash=? AND s.expires_at>?").bind(hash, Math.floor(Date.now()/1000)).first();
}

async function requireAgent(request, env) {
  const auth = await session(request, env);
  return auth || json({ message: "Your session has expired. Please sign in again." }, 401);
}

function agentData(row) {
  return { id: Number(row.agent_id ?? row.id), name: row.name, avatar: row.avatar || null, available: Boolean(row.available) };
}

function r2Client(env) {
  return new AwsClient({ accessKeyId: env.R2_ACCESS_KEY_ID, secretAccessKey: env.R2_SECRET_ACCESS_KEY, service: "s3", region: "auto" });
}

async function signedR2(env, key, method = "GET", mime = "") {
  const endpoint = new URL(`https://${env.CLOUDFLARE_ACCOUNT_ID}.r2.cloudflarestorage.com/${env.R2_BUCKET_NAME}/${key.split("/").map(encodeURIComponent).join("/")}`);
  endpoint.searchParams.set("X-Amz-Expires", method === "PUT" ? "300" : "900");
  const headers = mime ? { "content-type": mime } : {};
  const signed = await r2Client(env).sign(new Request(endpoint.toString(), { method, headers }), { aws: { signQuery: true } });
  return signed.url;
}

async function messageData(env, row) {
  return {
    id: Number(row.id), sender_type: row.sender_type, message_type: row.message_type,
    body: row.body || "", media_url: row.object_key ? await signedR2(env, row.object_key, "GET") : null,
    object_key: row.object_key || "", mime_type: row.mime_type || "",
    delivery_status: row.delivery_status || "sent", created_at: row.created_at, read_at: row.read_at || null
  };
}

async function broadcast(env, agentId, event) {
  const id = env.CHAT_HUB.idFromName(String(agentId));
  await env.CHAT_HUB.get(id).fetch("https://hub.internal/broadcast", { method: "POST", body: JSON.stringify(event) });
}

async function login(request, env) {
  const input = await body(request);
  const upstream = await fetch(new URL("login", env.WORDPRESS_API_BASE), {
    method: "POST", headers: { "content-type": "application/json" },
    body: JSON.stringify({ username: String(input.username || ""), password: String(input.password || "") })
  });
  const result = await upstream.json().catch(() => ({ message: "WordPress login returned an invalid response." }));
  if (!upstream.ok || !result.agent) return json({ message: result.message || "Invalid customer-care login" }, upstream.status || 401);
  const token = randomToken();
  const tokenHash = await sha256(token);
  const agent = result.agent;
  const expiry = Math.floor(Date.now()/1000) + 180 * 86400;
  await env.DB.batch([
    env.DB.prepare("INSERT INTO agents(id,name,avatar,available,updated_at) VALUES(?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET name=excluded.name,avatar=excluded.avatar,updated_at=excluded.updated_at").bind(Number(agent.id), agent.name, agent.avatar || null, agent.available ? 1 : 0, nowIso()),
    env.DB.prepare("INSERT INTO sessions(token_hash,agent_id,expires_at,created_at) VALUES(?,?,?,?)").bind(tokenHash, Number(agent.id), expiry, nowIso())
  ]);
  return json({ token, agent: { id:Number(agent.id), name:agent.name, avatar:agent.avatar || null, available:Boolean(agent.available) } });
}

async function conversations(auth, env) {
  const rows = (await env.DB.prepare(`SELECT c.id,c.public_id,c.customer_name,c.customer_avatar,c.updated_at,
    (SELECT COALESCE(NULLIF(m.body,''),'['||m.message_type||']') FROM messages m WHERE m.conversation_id=c.id ORDER BY m.id DESC LIMIT 1) last_message,
    (SELECT COUNT(*) FROM messages m WHERE m.conversation_id=c.id AND m.sender_type='customer' AND m.read_at IS NULL) unread
    FROM conversations c WHERE c.agent_id=? AND c.status!='closed' ORDER BY c.updated_at DESC LIMIT 100`).bind(auth.agent_id).all()).results || [];
  return json(rows.map(r => ({ ...r, id:Number(r.id), unread:Number(r.unread || 0), customer_avatar:r.customer_avatar || null })));
}

async function messages(request, auth, env, conversationId) {
  const conversation = await env.DB.prepare("SELECT * FROM conversations WHERE id=? AND agent_id=?").bind(conversationId, auth.agent_id).first();
  if (!conversation) return json({ message:"Conversation not found" }, 404);
  const url = new URL(request.url), after = Number(url.searchParams.get("after") || 0);
  if (url.searchParams.get("mark_read") !== "0") {
    const unread = (await env.DB.prepare("SELECT external_id FROM messages WHERE conversation_id=? AND sender_type='customer' AND read_at IS NULL AND external_id IS NOT NULL").bind(conversationId).all()).results || [];
    await env.DB.prepare("UPDATE messages SET read_at=? WHERE conversation_id=? AND sender_type='customer' AND read_at IS NULL").bind(nowIso(), conversationId).run();
    for (const item of unread) await env.JOBS.send({type:"read",external_id:item.external_id});
    await broadcast(env, auth.agent_id, { type:"receipt", conversation_id:conversationId, side:"agent" });
  }
  const query = after
    ? env.DB.prepare("SELECT * FROM messages WHERE conversation_id=? AND id>? ORDER BY id ASC LIMIT 500").bind(conversationId, after)
    : env.DB.prepare("SELECT * FROM (SELECT * FROM messages WHERE conversation_id=? ORDER BY id DESC LIMIT 500) ORDER BY id ASC").bind(conversationId);
  const rows = (await query.all()).results || [], output = [];
  for (const row of rows) output.push(await messageData(env, row));
  const peerRead = await env.DB.prepare("SELECT COALESCE(MAX(id),0) value FROM messages WHERE conversation_id=? AND sender_type='agent' AND read_at IS NOT NULL").bind(conversationId).first();
  const activity = await env.DB.prepare("SELECT state FROM activity WHERE conversation_id=? AND side='customer' AND expires_at>?").bind(conversationId, Math.floor(Date.now()/1000)).first();
  return json({ messages:output, next_after:output.length ? output[output.length-1].id : after, peer_read_id:Number(peerRead?.value || 0), peer_activity:activity?.state || "", history_saved:true, history_total:0 });
}

async function sendToWhatsApp(env, conversation, type, text, mediaUrl) {
  const payload = { messaging_product:"whatsapp", recipient_type:"individual", to:conversation.customer_wa_id };
  if (type === "text") Object.assign(payload, { type:"text", text:{ body:text, preview_url:false } });
  else Object.assign(payload, { type, [type]:{ link:mediaUrl } });
  const response = await fetch(`https://graph.facebook.com/${env.META_GRAPH_VERSION}/${env.META_PHONE_NUMBER_ID}/messages`, {
    method:"POST", headers:{ authorization:`Bearer ${env.META_ACCESS_TOKEN}`, "content-type":"application/json" }, body:JSON.stringify(payload)
  });
  const result = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(result?.error?.message || `WhatsApp HTTP ${response.status}`);
  return result?.messages?.[0]?.id || "";
}

async function sendMessage(request, auth, env) {
  const input = await body(request), cid = Number(input.conversation_id || 0);
  const conversation = await env.DB.prepare("SELECT * FROM conversations WHERE id=? AND agent_id=?").bind(cid, auth.agent_id).first();
  if (!conversation) return json({ message:"Conversation not found" }, 404);
  const type = ["text","image","audio","document"].includes(input.type) ? input.type : "text";
  const text = String(input.body || "").trim(), key = String(input.object_key || "");
  if (type === "text" && !text) return json({ message:"Message cannot be empty" }, 400);
  if (type !== "text" && !key) return json({ message:"Uploaded media is required" }, 400);
  const created = nowIso();
  const insert = await env.DB.prepare("INSERT INTO messages(conversation_id,sender_type,message_type,body,object_key,mime_type,delivery_status,created_at) VALUES(?,'agent',?,?,?,?, 'sending',?)")
    .bind(cid, type, text, key || null, String(input.mime_type || ""), created).run();
  const id = Number(insert.meta.last_row_id);
  try {
    const mediaUrl = key ? await signedR2(env, key, "GET") : "";
    const externalId = await sendToWhatsApp(env, conversation, type, text, mediaUrl);
    await env.DB.batch([
      env.DB.prepare("UPDATE messages SET external_id=?,delivery_status='sent' WHERE id=?").bind(externalId || null,id),
      env.DB.prepare("UPDATE conversations SET updated_at=?,status='open' WHERE id=?").bind(created,cid)
    ]);
    const row = await env.DB.prepare("SELECT * FROM messages WHERE id=?").bind(id).first();
    const output = await messageData(env,row); await broadcast(env,auth.agent_id,{type:"message",conversation_id:cid,message:output});
    return json(output,201);
  } catch (error) {
    await env.DB.prepare("UPDATE messages SET delivery_status='failed' WHERE id=?").bind(id).run();
    return json({ message:`WhatsApp delivery failed: ${error.message}` },502);
  }
}

async function presign(request, auth, env) {
  const input = await body(request), cid = Number(input.conversation_id || 0);
  const conversation = await env.DB.prepare("SELECT id FROM conversations WHERE id=? AND agent_id=?").bind(cid,auth.agent_id).first();
  if (!conversation) return json({message:"Conversation not found"},404);
  const kind = String(input.kind || ""), mime = String(input.mime_type || "").split(";")[0].toLowerCase(), size = Number(input.size || 0);
  const allowed = kind === "image" ? {"image/jpeg":"jpg","image/png":"png","image/webp":"webp"} : {"audio/mp4":"m4a","audio/aac":"aac","audio/mpeg":"mp3","audio/webm":"webm","audio/ogg":"ogg","audio/3gpp":"3gp","audio/amr":"amr"};
  const limit = kind === "image" ? 8*1024*1024 : 20*1024*1024;
  if (!allowed[mime] || size < 1 || size > limit) return json({message:"Unsupported media type or size"},400);
  const month = new Date().toISOString().slice(0,7).replace("-","/");
  const key = `native-support/${month}/${cid}/${crypto.randomUUID()}.${allowed[mime]}`;
  return json({ upload_url:await signedR2(env,key,"PUT",mime), public_url:await signedR2(env,key,"GET"), object_key:key });
}

async function activity(request, auth, env) {
  const input = await body(request), cid = Number(input.conversation_id || 0), state = ["typing","recording"].includes(input.state) ? input.state : "";
  const conversation = await env.DB.prepare("SELECT id FROM conversations WHERE id=? AND agent_id=?").bind(cid,auth.agent_id).first();
  if (!conversation) return json({message:"Conversation not found"},404);
  if (state) await env.DB.prepare("INSERT INTO activity(conversation_id,side,state,expires_at) VALUES(?,'agent',?,?) ON CONFLICT(conversation_id,side) DO UPDATE SET state=excluded.state,expires_at=excluded.expires_at").bind(cid,state,Math.floor(Date.now()/1000)+8).run();
  else await env.DB.prepare("DELETE FROM activity WHERE conversation_id=? AND side='agent'").bind(cid).run();
  return json({saved:true});
}

async function verifyMetaSignature(request, env, raw) {
  if (!env.META_APP_SECRET) return false;
  const expected = request.headers.get("x-hub-signature-256") || "";
  const key = await crypto.subtle.importKey("raw",encoder.encode(env.META_APP_SECRET),{name:"HMAC",hash:"SHA-256"},false,["sign"]);
  const signature = await crypto.subtle.sign("HMAC",key,encoder.encode(raw));
  const actual = "sha256=" + [...new Uint8Array(signature)].map(x=>x.toString(16).padStart(2,"0")).join("");
  if (actual.length !== expected.length) return false;
  let diff=0; for(let i=0;i<actual.length;i++) diff |= actual.charCodeAt(i)^expected.charCodeAt(i); return diff===0;
}

async function pickAgent(env) {
  return await env.DB.prepare(`SELECT a.id FROM agents a WHERE a.available=1 ORDER BY
    (SELECT COUNT(*) FROM conversations c WHERE c.agent_id=a.id AND c.status!='closed') ASC,a.updated_at DESC LIMIT 1`).first();
}

async function markWhatsAppRead(env, externalId) {
  const response=await fetch(`https://graph.facebook.com/${env.META_GRAPH_VERSION}/${env.META_PHONE_NUMBER_ID}/messages`,{method:"POST",headers:{authorization:`Bearer ${env.META_ACCESS_TOKEN}`,"content-type":"application/json"},body:JSON.stringify({messaging_product:"whatsapp",status:"read",message_id:externalId})});
  if(!response.ok)throw new Error(`WhatsApp read receipt HTTP ${response.status}: ${await response.text()}`);
}

async function metaWebhook(request, env) {
  const raw = await request.text();
  if (!await verifyMetaSignature(request,env,raw)) return new Response("Invalid signature",{status:401});
  const payload = JSON.parse(raw), changes = payload?.entry?.flatMap(e=>e.changes||[]) || [];
  for (const change of changes) {
    const value = change.value || {};
    for (const status of value.statuses || []) {
      const delivery = ["sent","delivered","read","failed"].includes(status.status) ? status.status : "sent";
      const row = await env.DB.prepare("SELECT m.id,m.conversation_id,c.agent_id FROM messages m JOIN conversations c ON c.id=m.conversation_id WHERE m.external_id=?").bind(status.id).first();
      if (row) {
        await env.DB.prepare("UPDATE messages SET delivery_status=?,read_at=CASE WHEN ?='read' THEN ? ELSE read_at END WHERE id=?").bind(delivery,delivery,nowIso(),row.id).run();
        await broadcast(env,row.agent_id,{type:"status",conversation_id:Number(row.conversation_id),message_id:Number(row.id),status:delivery});
      }
    }
    const contactName = value?.contacts?.[0]?.profile?.name || "WhatsApp customer";
    for (const incoming of value.messages || []) {
      if (await env.DB.prepare("SELECT id FROM messages WHERE external_id=?").bind(incoming.id).first()) continue;
      const waId = incoming.from, type = ["text","image","audio","document"].includes(incoming.type) ? incoming.type : "text";
      let conversation = await env.DB.prepare("SELECT * FROM conversations WHERE customer_wa_id=? AND status!='closed' ORDER BY id DESC LIMIT 1").bind(waId).first();
      if (!conversation) {
        const previous = await env.DB.prepare("SELECT c.*,a.available FROM conversations c JOIN agents a ON a.id=c.agent_id WHERE c.customer_wa_id=? ORDER BY c.id DESC LIMIT 1").bind(waId).first();
        if(previous?.available){await env.DB.prepare("UPDATE conversations SET status='open',updated_at=? WHERE id=?").bind(nowIso(),previous.id).run();conversation=previous;}
        else {const agent = await pickAgent(env); if (!agent) continue;const created=nowIso(), result=await env.DB.prepare("INSERT INTO conversations(public_id,customer_wa_id,customer_name,agent_id,status,created_at,updated_at) VALUES(?,?,?,?, 'open',?,?)").bind(crypto.randomUUID(),waId,contactName,agent.id,created,created).run();conversation={id:Number(result.meta.last_row_id),agent_id:Number(agent.id),customer_name:contactName};}
      }
      const text = type==="text" ? incoming.text?.body || "" : "";
      const media = type!=="text" ? incoming[type] || {} : {};
      const created=nowIso(), result=await env.DB.prepare("INSERT INTO messages(conversation_id,sender_type,message_type,body,mime_type,external_id,delivery_status,created_at) VALUES(?,'customer',?,?,?,?, 'delivered',?)")
        .bind(conversation.id,type,text,media.mime_type||"",incoming.id,created).run();
      const messageId=Number(result.meta.last_row_id);
      await env.DB.prepare("UPDATE conversations SET customer_name=?,updated_at=?,status='open' WHERE id=?").bind(contactName,created,conversation.id).run();
      const row=await env.DB.prepare("SELECT * FROM messages WHERE id=?").bind(messageId).first();
      await broadcast(env,conversation.agent_id,{type:"message",conversation_id:Number(conversation.id),message:await messageData(env,row)});
      await env.JOBS.send({type:"push",agent_id:Number(conversation.agent_id),title:contactName,body:type==="text"?text:`New ${type} message`,conversation_id:Number(conversation.id)});
      if (media.id) await env.JOBS.send({type:"media",message_id:messageId,agent_id:Number(conversation.agent_id),conversation_id:Number(conversation.id),meta_media_id:media.id,mime_type:media.mime_type||"application/octet-stream"});
    }
  }
  return new Response("EVENT_RECEIVED");
}

function pemBytes(pem) {
  const raw=pem.replace(/-----[^-]+-----/g,"").replace(/\s/g,"");
  return Uint8Array.from(atob(raw),c=>c.charCodeAt(0));
}

async function fcmToken(env) {
  if (fcmCache.token && fcmCache.expires > Date.now()+60000) return fcmCache.token;
  const email=env.FIREBASE_CLIENT_EMAIL, pem=(env.FIREBASE_PRIVATE_KEY||"").replaceAll("\\n","\n");
  if (!email || !pem) throw new Error("Firebase Worker secrets are incomplete");
  const key=await crypto.subtle.importKey("pkcs8",pemBytes(pem),{name:"RSASSA-PKCS1-v1_5",hash:"SHA-256"},false,["sign"]);
  const now=Math.floor(Date.now()/1000), header=b64url(encoder.encode(JSON.stringify({alg:"RS256",typ:"JWT"}))), claims=b64url(encoder.encode(JSON.stringify({iss:email,scope:"https://www.googleapis.com/auth/firebase.messaging",aud:"https://oauth2.googleapis.com/token",iat:now,exp:now+3500})));
  const unsigned=`${header}.${claims}`, signature=b64url(await crypto.subtle.sign("RSASSA-PKCS1-v1_5",key,encoder.encode(unsigned)));
  const response=await fetch("https://oauth2.googleapis.com/token",{method:"POST",headers:{"content-type":"application/x-www-form-urlencoded"},body:new URLSearchParams({grant_type:"urn:ietf:params:oauth:grant-type:jwt-bearer",assertion:`${unsigned}.${signature}`})});
  const result=await response.json(); if(!response.ok||!result.access_token)throw new Error(result.error_description||"Firebase OAuth failed");
  fcmCache={token:result.access_token,expires:Date.now()+(Number(result.expires_in||3600)*1000)}; return fcmCache.token;
}

async function pushAgent(env, job) {
  const devices=(await env.DB.prepare("SELECT token FROM devices WHERE agent_id=?").bind(job.agent_id).all()).results||[];
  if(!devices.length)return;
  const access=await fcmToken(env);
  for(const device of devices){
    const response=await fetch(`https://fcm.googleapis.com/v1/projects/${env.FIREBASE_PROJECT_ID}/messages:send`,{method:"POST",headers:{authorization:`Bearer ${access}`,"content-type":"application/json"},body:JSON.stringify({message:{token:device.token,notification:{title:job.title,body:job.body},data:{title:job.title,body:job.body,conversation_id:String(job.conversation_id)},android:{priority:"high",notification:{channel_id:"kbcc_messages",sound:"default"}}}})});
    if(response.status===404||response.status===410)await env.DB.prepare("DELETE FROM devices WHERE token=?").bind(device.token).run();
    else if(!response.ok)throw new Error(`FCM HTTP ${response.status}: ${await response.text()}`);
  }
}

async function copyMetaMedia(env, job) {
  const metadata=await fetch(`https://graph.facebook.com/${env.META_GRAPH_VERSION}/${job.meta_media_id}`,{headers:{authorization:`Bearer ${env.META_ACCESS_TOKEN}`}});
  const info=await metadata.json(); if(!metadata.ok||!info.url)throw new Error(info?.error?.message||"Meta media lookup failed");
  const download=await fetch(info.url,{headers:{authorization:`Bearer ${env.META_ACCESS_TOKEN}`}}); if(!download.ok)throw new Error(`Meta media download failed: ${download.status}`);
  const ext=(job.mime_type.split("/")[1]||"bin").replace("mpeg","mp3").replace("mp4","m4a").replace(/[^a-z0-9]/g,"");
  const month=new Date().toISOString().slice(0,7).replace("-","/"), key=`native-support/${month}/${job.conversation_id}/${crypto.randomUUID()}.${ext}`;
  await env.MEDIA.put(key,download.body,{httpMetadata:{contentType:job.mime_type},customMetadata:{deleteAfter:new Date(Date.now()+Number(env.MEDIA_RETENTION_DAYS||90)*86400000).toISOString()}});
  await env.DB.prepare("UPDATE messages SET object_key=?,mime_type=? WHERE id=?").bind(key,job.mime_type,job.message_id).run();
  const row=await env.DB.prepare("SELECT * FROM messages WHERE id=?").bind(job.message_id).first();
  await broadcast(env,job.agent_id,{type:"message",conversation_id:job.conversation_id,message:await messageData(env,row)});
}

async function api(request, env, path) {
  if (request.method === "POST" && path === "/v1/login") return login(request,env);
  const auth = await requireAgent(request,env); if (auth instanceof Response) return auth;
  if(request.method==="GET"&&path==="/v1/me")return json(agentData(auth));
  if(request.method==="POST"&&path==="/v1/availability"){const input=await body(request),available=input.available?1:0;await env.DB.prepare("UPDATE agents SET available=?,updated_at=? WHERE id=?").bind(available,nowIso(),auth.agent_id).run();return json({...agentData(auth),available:Boolean(available)});}
  if(request.method==="POST"&&path==="/v1/device"){const input=await body(request);if(!input.token)return json({message:"Token required"},400);await env.DB.prepare("INSERT INTO devices(token,agent_id,platform,updated_at) VALUES(?,?,?,?) ON CONFLICT(token) DO UPDATE SET agent_id=excluded.agent_id,platform=excluded.platform,updated_at=excluded.updated_at").bind(String(input.token),auth.agent_id,String(input.platform||"android"),nowIso()).run();return json({saved:true});}
  if(request.method==="GET"&&path==="/v1/conversations")return conversations(auth,env);
  const messageMatch=path.match(/^\/v1\/conversations\/(\d+)\/messages$/);if(request.method==="GET"&&messageMatch)return messages(request,auth,env,Number(messageMatch[1]));
  const closeMatch=path.match(/^\/v1\/conversations\/(\d+)\/close$/);if(request.method==="POST"&&closeMatch){const result=await env.DB.prepare("UPDATE conversations SET status='closed',updated_at=? WHERE id=? AND agent_id=?").bind(nowIso(),Number(closeMatch[1]),auth.agent_id).run();return result.meta.changes?json({closed:true}):json({message:"Conversation not found"},404);}
  if(request.method==="POST"&&path==="/v1/activity")return activity(request,auth,env);
  if(request.method==="POST"&&path==="/v1/messages")return sendMessage(request,auth,env);
  if(request.method==="POST"&&path==="/v1/media/presign")return presign(request,auth,env);
  if(request.method==="GET"&&path==="/v1/realtime"){
    if(request.headers.get("upgrade")?.toLowerCase()!=="websocket")return json({message:"WebSocket upgrade required"},426);
    const id=env.CHAT_HUB.idFromName(String(auth.agent_id));return env.CHAT_HUB.get(id).fetch(request);
  }
  return json({message:"Not found"},404);
}

export class ChatHub {
  constructor(state){this.state=state;}
  async fetch(request){
    const url=new URL(request.url);
    if(url.pathname==="/broadcast"){const event=await request.text();for(const socket of this.state.getWebSockets()){try{socket.send(event);}catch{try{socket.close(1011,"Delivery failed");}catch{}}}return new Response("ok");}
    if(request.headers.get("upgrade")?.toLowerCase()!=="websocket")return new Response("Upgrade required",{status:426});
    const pair=new WebSocketPair(),client=pair[0],server=pair[1];this.state.acceptWebSocket(server);server.send(JSON.stringify({type:"connected",at:Date.now()}));return new Response(null,{status:101,webSocket:client});
  }
  async webSocketMessage(socket,message){if(message==="ping")socket.send("pong");}
  async webSocketError(socket){try{socket.close(1011,"Socket error");}catch{}}
}

export default {
  async fetch(request,env){
    if(request.method==="OPTIONS")return cors(request,new Response(null,{status:204}));
    const url=new URL(request.url);let response;
    try{
      if(url.pathname==="/health")response=json({ok:true,service:"kb-customer-care-edge"});
      else if(url.pathname==="/webhooks/meta"&&request.method==="GET")response=url.searchParams.get("hub.verify_token")===env.META_VERIFY_TOKEN?new Response(url.searchParams.get("hub.challenge")||""):new Response("Forbidden",{status:403});
      else if(url.pathname==="/webhooks/meta"&&request.method==="POST")response=await metaWebhook(request,env);
      else response=await api(request,env,url.pathname);
    }catch(error){console.error(error);response=json({message:"Service temporarily unavailable",request_id:crypto.randomUUID()},500);}
    // Rebuilding a 101 Response drops Cloudflare's WebSocket handle.
    return response.status===101 ? response : cors(request,response);
  },
  async queue(batch,env){
    for(const message of batch.messages){try{if(message.body.type==="push")await pushAgent(env,message.body);else if(message.body.type==="media")await copyMetaMedia(env,message.body);else if(message.body.type==="read")await markWhatsAppRead(env,message.body.external_id);message.ack();}catch(error){console.error(error);message.retry();}}
  }
};
