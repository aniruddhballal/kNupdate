import { DurableObject } from "cloudflare:workers";
import { Relay, HttpError, TTL_MS, POLL_MS, decodeListenerName } from "./relay.js";

function json(value, status = 200) {
  return Response.json(value, { status, headers: {
    "Cache-Control": "no-store, max-age=0",
    "X-Content-Type-Options": "nosniff"
  } });
}

async function readJson(request) {
  if (!request.headers.get("content-type")?.startsWith("application/json")) {
    throw new HttpError(415, "Use application/json.");
  }
  const reader = request.body?.getReader();
  if (!reader) throw new HttpError(400, "JSON body required.");
  let size = 0;
  const chunks = [];
  while (true) {
    const { value, done } = await reader.read();
    if (done) break;
    size += value.length;
    if (size > 4096) { await reader.cancel(); throw new HttpError(413, "Maximum request size is 4 KiB."); }
    chunks.push(value);
  }
  const bytes = new Uint8Array(size);
  let offset = 0;
  for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.length; }
  try { return JSON.parse(new TextDecoder().decode(bytes)); }
  catch { throw new HttpError(400, "Invalid JSON."); }
}

export class ActivityRelay extends DurableObject {
  constructor(ctx, env) {
    super(ctx, env);
    // Payloads remain memory-only. Identity metadata is durable so a relay restart
    // does not silently destroy pairings.
    this.relay = new Relay();
    this.storage = ctx.storage;
    this.cleanupTimer = null;
    this.ready = ctx.blockConcurrencyWhile(async () => {
      this.relay.restoreIdentity(await ctx.storage.get("identity-metadata"));
    });
  }

  scheduleCleanup() {
    clearTimeout(this.cleanupTimer);
    if (Number.isFinite(this.relay.nextExpiry)) {
      this.cleanupTimer = setTimeout(() => {
        this.relay.prune();
        this.scheduleCleanup();
      }, Math.max(1, this.relay.nextExpiry - Date.now()));
    }
  }

  async fetch(request) {
    try {
      await this.ready;
      const path = new URL(request.url).pathname;
      const session = request.headers.get("X-Relay-Session");
      const deviceId = request.headers.get("X-Device-Id");
      const deviceToken = request.headers.get("X-Device-Token");
      const requireDeviceHeaders = () => {
        if (!deviceId || !deviceToken) throw new HttpError(401, "A registered device identity is required.");
      };
      const persist = async result => {
        if (this.relay.takeMetadataDirty()) await this.storage.put("identity-metadata", this.relay.identitySnapshot());
        this.scheduleCleanup();
        return result;
      };
      if (request.method === "POST" && path === "/v1/devices/register") {
        const body = await readJson(request);
        return json(await persist(this.relay.registerDevice(body?.deviceId, body?.deviceToken, body?.name)));
      }
      if (request.method === "POST" && path === "/v1/pairing/invite") {
        requireDeviceHeaders();
        return json(await persist(this.relay.createPairingInvite(deviceId, deviceToken)));
      }
      if (request.method === "POST" && path === "/v1/pairing/accept") {
        requireDeviceHeaders();
        const body = await readJson(request);
        return json(await persist(this.relay.acceptPairing(deviceId, deviceToken, body?.code)));
      }
      if (request.method === "GET" && path === "/v1/peers") {
        requireDeviceHeaders();
        return json(await persist(this.relay.pairedDevices(deviceId, deviceToken)));
      }
      const peerPath = /^\/v1\/peers\/([^/]+)$/.exec(path);
      if (peerPath && request.method === "DELETE") {
        requireDeviceHeaders();
        return json(await persist(this.relay.unpair(deviceId, deviceToken, peerPath[1])));
      }
      if (request.method === "GET" && path === "/v1/poll") {
        requireDeviceHeaders();
        const updates = await this.relay.poll(session, request.signal, decodeListenerName(request.headers.get("X-Relay-Name")), deviceId, deviceToken);
        await persist(updates);
        return json({ updates });
      }
      if (request.method === "GET" && path === "/v1/listeners") {
        requireDeviceHeaders();
        const result = this.relay.activeListeners(session, deviceId, deviceToken);
        await persist(result);
        return json(result);
      }
      const receiptPath = /^\/v1\/receipts\/([^/]+)$/.exec(path);
      if (receiptPath && (request.method === "GET" || request.method === "DELETE")) {
        requireDeviceHeaders();
        const key = request.headers.get("X-Receipt-Key");
        const result = request.method === "GET"
          ? await this.relay.pollReceipts(receiptPath[1], key, Number(new URL(request.url).searchParams.get("after") ?? -1), request.signal, deviceId, deviceToken)
          : this.relay.forgetReceipts(receiptPath[1], key, deviceId, deviceToken);
        await persist(result);
        return json(result);
      }
      if (request.method === "DELETE" && path === "/v1/poll") {
        requireDeviceHeaders();
        this.relay.close(session, deviceId, deviceToken);
        await persist({ closed: true });
        return json({ closed: true });
      }
      if (request.method === "POST" && path === "/v1/updates") {
        requireDeviceHeaders();
        const result = this.relay.publish(await readJson(request), session, request.headers.get("X-Receipt-Key"), deviceId, deviceToken);
        await persist(result);
        return json(result, 201);
      }
      if (request.method === "POST" && path === "/v1/ack") {
        requireDeviceHeaders();
        const body = await readJson(request);
        const result = this.relay.acknowledge(session, body?.ids, deviceId, deviceToken);
        await persist(result);
        return json(result);
      }
      return json({ error: "Not found." }, 404);
    } catch (error) {
      // Never log request bodies or return runtime internals.
      return json({ error: error instanceof HttpError ? error.message : "Relay unavailable." }, error.status ?? 503);
    }
  }
}

export default {
  async fetch(request, env) {
    const path = new URL(request.url).pathname;
    if (request.method === "GET" && path === "/health") {
      return json({ service: "knupdate-relay", protocol: 2, ttlMs: TTL_MS, pollMs: POLL_MS,
        features: ["device-identities", "pairing", "targeted-delivery", "listener-directory", "listener-receipts"] });
    }
    if (!path.startsWith("/v1/")) return json({ error: "Not found." }, 404);
    return env.RELAY.get(env.RELAY.idFromName("private-sharing-v1")).fetch(request);
  }
};
