import { DurableObject } from "cloudflare:workers";
import { Relay, HttpError, TTL_MS, POLL_MS } from "./relay.js";

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
    // The free plan requires a SQLite-backed namespace, but we never write payloads
    // to SQLite/KV: their recovery history would outlive a DELETE. Restarts lose data.
    this.relay = new Relay();
    this.cleanupTimer = null;
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
      const path = new URL(request.url).pathname;
      const session = request.headers.get("X-Relay-Session");
      if (request.method === "GET" && path === "/v1/poll") {
        const updates = await this.relay.poll(session, request.signal);
        this.scheduleCleanup();
        return json({ updates });
      }
      if (request.method === "DELETE" && path === "/v1/poll") {
        this.relay.close(session);
        this.scheduleCleanup();
        return json({ closed: true });
      }
      if (request.method === "GET" && path === "/v1/updates") return json({ updates: this.relay.publicUpdates() });
      if (request.method === "POST" && path === "/v1/updates") {
        const result = this.relay.publish(await readJson(request), session);
        this.scheduleCleanup();
        return json(result, 201);
      }
      if (request.method === "POST" && path === "/v1/ack") {
        const body = await readJson(request);
        const result = this.relay.acknowledge(session, body?.ids);
        this.scheduleCleanup();
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
      return json({ service: "knupdate-relay", protocol: 1, ttlMs: TTL_MS, pollMs: POLL_MS });
    }
    if (!path.startsWith("/v1/")) return json({ error: "Not found." }, 404);
    return env.RELAY.get(env.RELAY.idFromName("public-broadcast-v1")).fetch(request);
  }
};
