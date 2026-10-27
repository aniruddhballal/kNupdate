export const TTL_MS = 30_000;
export const POLL_MS = 25_000;
const PAIRING_TTL_MS = 10 * 60_000;
const MAX_INFLIGHT = 100;
const MAX_LISTENERS = 100;
const MAX_DEVICES = 10_000;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const PAIRING_CODE = /^[A-HJ-NP-Z2-9]{10}$/;
const PAIRING_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

export class HttpError extends Error {
  constructor(status, message) { super(message); this.status = status; }
}

export function validateSession(session) {
  if (typeof session !== "string" || !UUID.test(session)) throw new HttpError(400, "A random session UUID is required.");
  return session;
}

export function validateDeviceId(deviceId) {
  if (typeof deviceId !== "string" || !UUID.test(deviceId)) throw new HttpError(401, "A registered device identity is required.");
  return deviceId.toLowerCase();
}

export function validateDeviceToken(token) {
  if (typeof token !== "string" || !UUID.test(token)) throw new HttpError(401, "A registered device credential is required.");
  return token.toLowerCase();
}

export function validateListenerName(name) {
  if (name === null || name === undefined) return null;
  if (typeof name !== "string" || !name.trim() || name.trim().length > 80 ||
      /[\u0000-\u001f\u007f-\u009f\u202a-\u202e\u2066-\u2069]/u.test(name)) {
    throw new HttpError(400, "Use a display name of 1 to 80 characters without control characters.");
  }
  return name.trim();
}

function validateRecipientIds(value, { allowEmpty = true } = {}) {
  if (!Array.isArray(value) || value.length > MAX_LISTENERS || (!allowEmpty && value.length === 0) ||
      value.some(id => typeof id !== "string" || !UUID.test(id))) {
    throw new HttpError(400, "Choose one or more valid recipient devices.");
  }
  const result = value.map(id => id.toLowerCase());
  if (new Set(result).size !== result.length) throw new HttpError(400, "Recipient devices must be unique.");
  return result;
}

function validatePairingCode(code) {
  if (typeof code !== "string" || !PAIRING_CODE.test(code.trim().toUpperCase())) {
    throw new HttpError(400, "Enter a valid pairing code.");
  }
  return code.trim().toUpperCase();
}

function newPairingCode() {
  const values = new Uint8Array(10);
  crypto.getRandomValues(values);
  return [...values].map(value => PAIRING_ALPHABET[value % PAIRING_ALPHABET.length]).join("");
}

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
      typeof value.body !== "string" || value.body.length > 280 ||
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
        fix.capturedAt < value.startAt || fix.capturedAt > now + 60_000) {
      throw new HttpError(400, "Invalid coordinates or capture time.");
    }
    result.coordinates = Object.fromEntries(fields.map(key => [key, fix[key]]));
  }
  return result;
}

// Activity payloads remain ephemeral. Device identities and pair relationships are
// metadata and may be persisted by the Durable Object wrapper separately.
export class Relay {
  constructor({ now = Date.now, pollMs = POLL_MS } = {}) {
    this.records = new Map();
    this.waiters = new Map();
    this.sessions = new Map();
    this.sessionDevices = new Map();
    this.listenerNames = new Map();
    this.receipts = new Map();
    this.receiptWaiters = new Map();
    this.devices = new Map();
    this.pairingCodes = new Map();
    this.now = now;
    this.pollMs = pollMs;
    this.metadataDirty = false;
    this.prune();
  }

  prune() {
    for (const [id, record] of this.records) {
      if (record.expiresAt <= this.now()) this.records.delete(id);
    }
    for (const [session, until] of this.sessions) {
      if (until <= this.now()) {
        this.sessions.delete(session);
        this.sessionDevices.delete(session);
        this.listenerNames.delete(session);
      }
    }
    for (const [id, receipt] of this.receipts) {
      if (receipt.expiresAt <= this.now()) {
        this.receiptWaiters.get(id)?.finish(this.receiptView(receipt, true));
        this.receipts.delete(id);
      }
    }
    for (const [code, invite] of this.pairingCodes) {
      if (invite.expiresAt <= this.now()) this.pairingCodes.delete(code);
    }
  }

  registerDevice(deviceId, token, name) {
    const id = validateDeviceId(deviceId);
    const credential = validateDeviceToken(token);
    const displayName = validateListenerName(name);
    const current = this.devices.get(id);
    if (current && current.token !== credential) throw new HttpError(401, "Device credential does not match.");
    if (!current) {
      if (this.devices.size >= MAX_DEVICES) throw new HttpError(503, "Device limit reached.");
      this.devices.set(id, { token: credential, name: displayName, paired: new Set(), createdAt: this.now() });
      this.metadataDirty = true;
    } else if (current.name !== displayName) {
      current.name = displayName;
      this.metadataDirty = true;
    }
    return this.profile(id);
  }

  authenticate(deviceId, token) {
    const id = validateDeviceId(deviceId);
    const credential = validateDeviceToken(token);
    const device = this.devices.get(id);
    if (!device || device.token !== credential) throw new HttpError(401, "Device is not registered.");
    return { id, device };
  }

  profile(deviceId) {
    const id = validateDeviceId(deviceId);
    const device = this.devices.get(id);
    if (!device) throw new HttpError(404, "Device is not registered.");
    return { id, name: device.name, createdAt: device.createdAt };
  }

  createPairingInvite(deviceId, token) {
    const { id } = this.authenticate(deviceId, token);
    this.prune();
    let code;
    do { code = newPairingCode(); } while (this.pairingCodes.has(code));
    const expiresAt = this.now() + PAIRING_TTL_MS;
    this.pairingCodes.set(code, { deviceId: id, expiresAt });
    return { code, expiresAt };
  }

  acceptPairing(deviceId, token, rawCode) {
    const { id } = this.authenticate(deviceId, token);
    const code = validatePairingCode(rawCode);
    this.prune();
    const invite = this.pairingCodes.get(code);
    if (!invite) throw new HttpError(404, "Pairing code is expired or unknown.");
    if (invite.deviceId === id) throw new HttpError(400, "A device cannot pair with itself.");
    const inviter = this.devices.get(invite.deviceId);
    if (!inviter) throw new HttpError(404, "The inviting device is no longer registered.");
    const device = this.devices.get(id);
    inviter.paired.add(id);
    device.paired.add(invite.deviceId);
    this.pairingCodes.delete(code);
    this.metadataDirty = true;
    return { paired: true, peer: this.profile(invite.deviceId) };
  }

  pairedDevices(deviceId, token) {
    const { device } = this.authenticate(deviceId, token);
    return { peers: [...device.paired].map(id => this.profile(id)) };
  }

  unpair(deviceId, token, peerId) {
    const { id, device } = this.authenticate(deviceId, token);
    const peer = validateDeviceId(peerId);
    device.paired.delete(peer);
    this.devices.get(peer)?.paired.delete(id);
    this.metadataDirty = true;
    return { unpaired: true };
  }

  identitySnapshot() {
    return {
      devices: [...this.devices.entries()].map(([id, device]) => ({ id, token: device.token, name: device.name, createdAt: device.createdAt })),
      pairs: [...this.devices.entries()].flatMap(([id, device]) => [...device.paired].filter(peer => id < peer).map(peer => [id, peer]))
    };
  }

  restoreIdentity(snapshot) {
    if (!snapshot || !Array.isArray(snapshot.devices) || !Array.isArray(snapshot.pairs)) return;
    for (const value of snapshot.devices) {
      try {
        const id = validateDeviceId(value.id);
        const token = validateDeviceToken(value.token);
        const name = validateListenerName(value.name);
        this.devices.set(id, { token, name, paired: new Set(), createdAt: Number.isSafeInteger(value.createdAt) ? value.createdAt : this.now() });
      } catch { /* Ignore malformed metadata rather than blocking the relay. */ }
    }
    for (const pair of snapshot.pairs) {
      if (!Array.isArray(pair) || pair.length !== 2) continue;
      const first = this.devices.get(pair[0]), second = this.devices.get(pair[1]);
      if (first && second) { first.paired.add(pair[1]); second.paired.add(pair[0]); }
    }
    this.metadataDirty = false;
  }

  takeMetadataDirty() {
    const dirty = this.metadataDirty;
    this.metadataDirty = false;
    return dirty;
  }

  available(session) {
    this.prune();
    return [...this.records.values()]
      .filter(record => record.pending.includes(session))
      .map(record => ({ ...record.activity, expiresAt: record.expiresAt }));
  }

  assertSessionOwner(session, deviceId) {
    const owner = this.sessionDevices.get(session);
    if (owner && owner !== deviceId) throw new HttpError(403, "This session belongs to another device.");
  }

  poll(session, signal, displayName = null, deviceId = null, token = null) {
    validateSession(session);
    if (deviceId !== null) {
      const auth = this.authenticate(deviceId, token);
      this.assertSessionOwner(session, auth.id);
      deviceId = auth.id;
    } else {
      deviceId = `legacy:${session}`;
    }
    const name = validateListenerName(displayName);
    this.prune();
    if (!this.sessions.has(session) && this.sessions.size >= MAX_LISTENERS) throw new HttpError(503, "Listener limit reached.");
    this.sessionDevices.set(session, deviceId);
    this.listenerNames.set(session, name);
    this.sessions.set(session, this.now() + this.pollMs + 5_000);
    const available = this.available(session);
    if (available.length) {
      this.sessions.set(session, this.now() + 5_000);
      return Promise.resolve(available);
    }
    this.waiters.get(session)?.finish([]);
    if (this.waiters.size >= MAX_LISTENERS) throw new HttpError(503, "Listener limit reached.");
    if (signal?.aborted) { this.close(session); return Promise.resolve([]); }
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
      entry.abort = () => { entry.finish([]); this.close(session); };
      entry.timer = setTimeout(() => entry.finish([]), this.pollMs);
      this.waiters.set(session, entry);
      signal?.addEventListener("abort", entry.abort, { once: true });
    });
  }

  publish(input, senderSession, receiptKey = null, senderDeviceId = null, token = null) {
    const targeted = input && Object.hasOwn(input, "activity");
    const activity = validateActivity(targeted ? input.activity : input, this.now());
    const mode = targeted ? (input.mode ?? "leaf") : "tree";
    if (mode !== "leaf" && mode !== "tree") throw new HttpError(400, "Choose leaf or tree delivery.");
    const recipientIds = mode === "leaf" ? validateRecipientIds(input.recipientIds, { allowEmpty: false }) : null;
    if (targeted && mode === "tree" && Object.hasOwn(input, "recipientIds")) {
      throw new HttpError(400, "Tree delivery cannot include recipient devices.");
    }
    let sender = null;
    if (senderDeviceId !== null) {
      sender = this.authenticate(senderDeviceId, token);
      if (senderSession) { validateSession(senderSession); this.assertSessionOwner(senderSession, sender.id); }
      if (mode === "leaf") {
        if (recipientIds.includes(sender.id)) throw new HttpError(400, "The sender cannot be a recipient.");
        for (const recipient of recipientIds) {
          if (!sender.device.paired.has(recipient)) throw new HttpError(403, "Every recipient must be paired with the sender.");
        }
      }
    } else if (senderSession) validateSession(senderSession);
    if (receiptKey !== null) validateSession(receiptKey);
    this.prune();
    const previous = this.records.get(activity.id);
    const requestedRecipients = recipientIds ? [...recipientIds].sort() : null;
    const inputFingerprint = JSON.stringify({ activity, mode, recipientIds: requestedRecipients });
    if (previous) {
      if (previous.fingerprint !== inputFingerprint || previous.receiptKey !== receiptKey) throw new HttpError(409, "Activity ID already in use.");
      return structuredClone(previous.acceptance);
    }
    if (this.receipts.has(activity.id)) throw new HttpError(409, "Activity ID has already completed.");
    if (this.records.size >= MAX_INFLIGHT) throw new HttpError(503, "Relay is busy. Try again shortly.");
    if (receiptKey !== null && this.receipts.size >= MAX_INFLIGHT) throw new HttpError(503, "Receipt limit reached. Try again shortly.");
    const eligible = [...this.sessions.keys()].filter(session => {
      if (session === senderSession) return false;
      const sessionDevice = this.sessionDevices.get(session);
      return mode === "tree" ? true : recipientIds.includes(sessionDevice);
    });
    const expiresAt = this.now() + TTL_MS;
    const snapshot = eligible.map(session => {
      const device = this.devices.get(this.sessionDevices.get(session));
      return { id: crypto.randomUUID(), name: mode === "leaf" ? (device?.name ?? null) : this.listenerNames.get(session) ?? null, confirmedAt: null };
    });
    const acceptance = { accepted: true, mode, listeners: eligible.length, recipients: snapshot,
      expiresAt: eligible.length ? expiresAt : null, receiptExpiresAt: receiptKey !== null && eligible.length ? expiresAt : null };
    if (eligible.length) {
      const record = { activity, mode, pending: eligible, expiresAt, receiptKey,
        fingerprint: inputFingerprint, acceptance, receiptIds: new Map(eligible.map((session, index) => [session, snapshot[index].id])) };
      if (receiptKey !== null) this.receipts.set(activity.id, {
        key: receiptKey, senderDeviceId: sender?.id ?? null, expiresAt, revision: 0, recipients: structuredClone(snapshot)
      });
      this.records.set(activity.id, record);
      for (const session of eligible) this.waiters.get(session)?.finish(this.available(session));
    }
    return structuredClone(acceptance);
  }

  acknowledge(session, ids, deviceId = null, token = null) {
    validateSession(session);
    if (deviceId !== null) {
      const auth = this.authenticate(deviceId, token);
      this.assertSessionOwner(session, auth.id);
    }
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

  activeListeners(session = null, deviceId = null, token = null) {
    if (deviceId !== null) {
      const auth = this.authenticate(deviceId, token);
      if (session !== null) { validateSession(session); this.assertSessionOwner(session, auth.id); }
    } else if (session !== null) validateSession(session);
    this.prune();
    return { asOf: this.now(), listeners: [...this.sessions.keys()].map(id => ({ name: this.listenerNames.get(id) ?? this.devices.get(this.sessionDevices.get(id))?.name ?? null, self: id === session })) };
  }

  receiptView(receipt, expired = false) {
    return { recipients: structuredClone(receipt.recipients), revision: receipt.revision, expiresAt: receipt.expiresAt,
      complete: receipt.recipients.every(value => value.confirmedAt !== null), expired };
  }

  findReceipt(id, key, deviceId = null, token = null) {
    validateSession(id);
    validateSession(key);
    if (deviceId !== null) this.authenticate(deviceId, token);
    this.prune();
    const receipt = this.receipts.get(id);
    if (!receipt || receipt.key !== key || (deviceId !== null && receipt.senderDeviceId !== deviceId)) throw new HttpError(404, "Receipt report is unavailable.");
    return receipt;
  }

  pollReceipts(id, key, after = -1, signal, deviceId = null, token = null) {
    const receipt = this.findReceipt(id, key, deviceId, token);
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

  forgetReceipts(id, key, deviceId = null, token = null) {
    const receipt = this.findReceipt(id, key, deviceId, token);
    this.receiptWaiters.get(id)?.finish(this.receiptView(receipt, true));
    this.receipts.delete(id);
    return { forgotten: true };
  }

  close(session, deviceId = null, token = null) {
    validateSession(session);
    if (deviceId !== null) {
      const auth = this.authenticate(deviceId, token);
      this.assertSessionOwner(session, auth.id);
    }
    this.waiters.get(session)?.finish([]);
    this.sessions.delete(session);
    this.sessionDevices.delete(session);
    this.listenerNames.delete(session);
  }

  get nextExpiry() {
    return Math.min(...[...this.records.values()].map(record => record.expiresAt), ...this.sessions.values(),
      ...[...this.receipts.values()].map(receipt => receipt.expiresAt), ...[...this.pairingCodes.values()].map(invite => invite.expiresAt));
  }
}
