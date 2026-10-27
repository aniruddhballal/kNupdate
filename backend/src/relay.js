export const TTL_MS = 30_000;
export const POLL_MS = 25_000;
const MAX_INFLIGHT = 100;
const MAX_LISTENERS = 100;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export class HttpError extends Error {
  constructor(status, message) { super(message); this.status = status; }
}

export function validateSession(session) {
  if (typeof session !== "string" || !UUID.test(session)) throw new HttpError(400, "A random session UUID is required.");
  return session;
}

export function validateListenerName(name) {
  if (name === null || name === undefined) return null;
  if (typeof name !== "string" || !name.trim() || name.trim().length > 80 ||
      /[\u0000-\u001f\u007f-\u009f\u202a-\u202e\u2066-\u2069]/u.test(name)) {
    throw new HttpError(400, "Use a display name of 1 to 80 characters without control characters.");
  }
  return name.trim();
}

// UTF-8 names travel in a header rather than a URL or the activity payload.
export function decodeListenerName(header) {
  if (header === null || header === undefined) return null;
  try {
    if (!header || header.length > 432 || header.length % 4 !== 0 || !/^[A-Za-z0-9+/]*={0,2}$/.test(header)) throw new Error();
    const binary = atob(header);
    if (btoa(binary) !== header) throw new Error();
    return validateListenerName(new TextDecoder("utf-8", { fatal: true }).decode(Uint8Array.from(binary, c => c.charCodeAt(0))));
  } catch { throw new HttpError(400, "Invalid listener display name."); }
}

export function validateActivity(value, now) {
  const keys = ["id", "title", "body", "startAt", "endAt", "timeZone", "coordinates"];
  if (!value || typeof value !== "object" || Array.isArray(value) ||
      Object.keys(value).some(key => !keys.includes(key))) {
    throw new HttpError(400, "Only activity fields are accepted; do not include personal details.");
  }
  if (typeof value.id !== "string" || !UUID.test(value.id) || typeof value.title !== "string" ||
      value.title.trim().length < 1 || value.title.length > 80 ||
      typeof value.body !== "string" || value.body.trim().length < 1 || value.body.length > 280 ||
      !Number.isSafeInteger(value.startAt) || value.startAt < 0 || value.startAt > now + 60_000 ||
      (value.endAt !== null && (!Number.isSafeInteger(value.endAt) ||
        value.endAt <= value.startAt || value.endAt > now + 60_000)) ||
      typeof value.timeZone !== "string" || value.timeZone.length > 80) {
    throw new HttpError(400, "Invalid activity or time range.");
  }
  try { new Intl.DateTimeFormat("en", { timeZone: value.timeZone }); }
  catch { throw new HttpError(400, "Invalid time zone."); }
  const result = Object.fromEntries(keys.filter(key => key !== "coordinates").map(key => [key, value[key]]));
  if (Object.hasOwn(value, "coordinates")) {
    const fix = value.coordinates;
    const fields = ["latitude", "longitude", "accuracyMeters", "capturedAt"];
    if (!fix || typeof fix !== "object" || Array.isArray(fix) || Object.keys(fix).length !== fields.length ||
        fields.some(key => !Object.hasOwn(fix, key) || !Number.isFinite(fix[key])) ||
        Object.keys(fix).some(key => !fields.includes(key)) ||
        fix.latitude < -90 || fix.latitude > 90 || fix.longitude < -180 || fix.longitude > 180 ||
        fix.accuracyMeters < 0 || !Number.isSafeInteger(fix.capturedAt) ||
        fix.capturedAt < value.startAt || fix.capturedAt > now + 60_000 || value.endAt !== null) {
      throw new HttpError(400, "Invalid coordinates or capture time.");
    }
    result.coordinates = Object.fromEntries(fields.map(key => [key, fix[key]]));
  }
  return result;
}

// All transitions are synchronous. Payloads live only in this object's memory.
// Sessions are random, short-lived listener handles, never user/device identities.
export class Relay {
  constructor({ now = Date.now, pollMs = POLL_MS } = {}) {
    this.records = new Map();
    this.waiters = new Map();
    this.sessions = new Map();
    this.listenerNames = new Map();
    this.receipts = new Map();
    this.receiptWaiters = new Map();
    this.now = now;
    this.pollMs = pollMs;
    this.prune();
  }

  prune() {
    for (const [id, record] of this.records) {
      if (record.expiresAt <= this.now()) this.records.delete(id);
    }
    for (const [session, until] of this.sessions) {
      if (until <= this.now()) { this.sessions.delete(session); this.listenerNames.delete(session); }
    }
    for (const [id, receipt] of this.receipts) {
      if (receipt.expiresAt <= this.now()) {
        this.receiptWaiters.get(id)?.finish(this.receiptView(receipt, true));
        this.receipts.delete(id);
      }
    }
  }

  available(session) {
    this.prune();
    return [...this.records.values()]
      .filter(record => record.pending.includes(session))
      .map(record => ({ ...record.activity, expiresAt: record.expiresAt }));
  }

  poll(session, signal, displayName = null) {
    validateSession(session);
    const name = validateListenerName(displayName);
    this.prune();
    if (!this.sessions.has(session) && this.sessions.size >= MAX_LISTENERS) throw new HttpError(503, "Listener limit reached.");
    this.listenerNames.set(session, name);
    // A short lease covers the network gap between a result, its ACK and the next poll.
    this.sessions.set(session, this.now() + this.pollMs + 5_000);
    const available = this.available(session);
    if (available.length) {
      this.sessions.set(session, this.now() + 5_000);
      return Promise.resolve(available);
    }
    this.waiters.get(session)?.finish([]);
    if (this.waiters.size >= MAX_LISTENERS) throw new HttpError(503, "Listener limit reached.");
    if (signal?.aborted) { this.sessions.delete(session); this.listenerNames.delete(session); return Promise.resolve([]); }
    this.sessions.set(session, this.now() + this.pollMs + 5_000);
    return new Promise(resolve => {
      const entry = {
        finish: updates => {
          clearTimeout(entry.timer);
          signal?.removeEventListener("abort", entry.abort);
          if (this.waiters.get(session) === entry) this.waiters.delete(session);
          this.sessions.set(session, this.now() + 5_000);
          resolve(updates);
        }
      };
      entry.abort = () => { entry.finish([]); this.sessions.delete(session); this.listenerNames.delete(session); };
      entry.timer = setTimeout(() => entry.finish([]), this.pollMs);
      this.waiters.set(session, entry);
      signal?.addEventListener("abort", entry.abort, { once: true });
    });
  }

  publish(input, senderSession, receiptKey = null) {
    const activity = validateActivity(input, this.now());
    if (senderSession) validateSession(senderSession);
    if (receiptKey !== null) validateSession(receiptKey);
    this.prune();
    const previous = this.records.get(activity.id);
    if (previous) {
      if (JSON.stringify(previous.activity) !== JSON.stringify(activity) || previous.receiptKey !== receiptKey) throw new HttpError(409, "Activity ID already in use.");
      return structuredClone(previous.acceptance);
    }
    if (this.receipts.has(activity.id)) throw new HttpError(409, "Activity ID has already completed.");
    if (this.records.size >= MAX_INFLIGHT) throw new HttpError(503, "Relay is busy. Try again shortly.");
    if (receiptKey !== null && this.receipts.size >= MAX_INFLIGHT) throw new HttpError(503, "Receipt limit reached. Try again shortly.");
    const recipients = [...this.sessions.keys()].filter(session => session !== senderSession);
    const expiresAt = this.now() + TTL_MS;
    const snapshot = recipients.map(session => ({ id: crypto.randomUUID(), name: this.listenerNames.get(session) ?? null, confirmedAt: null }));
    const acceptance = { accepted: true, listeners: recipients.length, recipients: snapshot,
      expiresAt: recipients.length ? expiresAt : null, receiptExpiresAt: receiptKey !== null && recipients.length ? expiresAt : null };
    if (recipients.length) {
      const record = { activity, pending: recipients, expiresAt, receiptKey, acceptance,
        receiptIds: new Map(recipients.map((session, index) => [session, snapshot[index].id])) };
      if (receiptKey !== null) this.receipts.set(activity.id, {
        key: receiptKey, expiresAt, revision: 0, recipients: structuredClone(snapshot)
      });
      this.records.set(activity.id, record);
      for (const session of recipients) this.waiters.get(session)?.finish(this.available(session));
    }
    // With nobody listening there is nothing to retain, including the payload.
    return structuredClone(acceptance);
  }

  acknowledge(session, ids) {
    validateSession(session);
    if (!Array.isArray(ids) || ids.length > MAX_INFLIGHT || ids.some(id => typeof id !== "string" || !UUID.test(id))) {
      throw new HttpError(400, "Expected an array of activity UUIDs.");
    }
    this.prune();
    for (const id of ids) {
      const record = this.records.get(id);
      if (!record?.pending.includes(session)) continue;
      const receipt = this.receipts.get(id);
      if (receipt) {
        const recipient = receipt.recipients.find(value => value.id === record.receiptIds.get(session));
        recipient.confirmedAt = this.now();
        receipt.revision++;
        this.receiptWaiters.get(id)?.finish(this.receiptView(receipt));
      }
      record.pending = record.pending.filter(value => value !== session);
      if (!record.pending.length) this.records.delete(id);
    }
    return { acknowledged: true };
  }

  publicUpdates() {
    this.prune();
    return [...this.records.values()].map(record => ({ ...record.activity, expiresAt: record.expiresAt }));
  }

  activeListeners(session = null) {
    if (session !== null) validateSession(session);
    this.prune();
    return { asOf: this.now(), listeners: [...this.sessions.keys()].map(id => ({ name: this.listenerNames.get(id) ?? null, self: id === session })) };
  }

  receiptView(receipt, expired = false) {
    return { recipients: structuredClone(receipt.recipients), revision: receipt.revision, expiresAt: receipt.expiresAt,
      complete: receipt.recipients.every(value => value.confirmedAt !== null), expired };
  }

  findReceipt(id, key) {
    validateSession(id);
    validateSession(key);
    this.prune();
    const receipt = this.receipts.get(id);
    if (!receipt || receipt.key !== key) throw new HttpError(404, "Receipt report is unavailable.");
    return receipt;
  }

  pollReceipts(id, key, after = -1, signal) {
    const receipt = this.findReceipt(id, key);
    if (!Number.isSafeInteger(after) || after < -1 || after > MAX_LISTENERS) throw new HttpError(400, "Invalid receipt revision.");
    const view = this.receiptView(receipt);
    if (view.revision > after || view.complete || signal?.aborted) return Promise.resolve(view);
    this.receiptWaiters.get(id)?.finish(view);
    return new Promise(resolve => {
      const entry = {
        finish: result => {
          clearTimeout(entry.timer);
          signal?.removeEventListener("abort", entry.abort);
          if (this.receiptWaiters.get(id) === entry) this.receiptWaiters.delete(id);
          resolve(result);
        }
      };
      entry.abort = () => entry.finish(this.receiptView(receipt));
      entry.timer = setTimeout(() => {
        this.prune();
        if (this.receiptWaiters.get(id) === entry) entry.finish(this.receiptView(receipt));
      }, Math.max(1, Math.min(this.pollMs, receipt.expiresAt - this.now())));
      this.receiptWaiters.set(id, entry);
      signal?.addEventListener("abort", entry.abort, { once: true });
    });
  }

  forgetReceipts(id, key) {
    const receipt = this.findReceipt(id, key);
    this.receiptWaiters.get(id)?.finish(this.receiptView(receipt, true));
    this.receipts.delete(id);
    return { forgotten: true };
  }

  close(session) {
    this.waiters.get(validateSession(session))?.finish([]);
    this.sessions.delete(session);
    this.listenerNames.delete(session);
  }
  get nextExpiry() {
    return Math.min(...[...this.records.values()].map(record => record.expiresAt), ...this.sessions.values(),
      ...[...this.receipts.values()].map(receipt => receipt.expiresAt));
  }
}
