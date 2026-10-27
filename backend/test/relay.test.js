import test from "node:test";
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { Relay, TTL_MS, validateActivity, decodeListenerName } from "../src/relay.js";

const activity = (now = Date.now()) => ({ id: randomUUID(), title: "Run", body: "5k", startAt: now - 1_200_000, endAt: now - 1, timeZone: "Asia/Calcutta" });

test("named listener snapshots and private Inbox receipts are separate from activity lifetime", async () => {
  const relay = new Relay();
  const alice = randomUUID(), bob = randomUUID(), key = randomUUID();
  const a = relay.poll(alice, undefined, "Asha"), b = relay.poll(bob, undefined, "Ravi");
  assert.deepEqual(relay.activeListeners(alice).listeners, [{ name: "Asha", self: true }, { name: "Ravi", self: false }]);
  const value = activity();
  const sent = relay.publish(value, null, key);
  await Promise.all([a, b]);
  assert.equal(sent.listeners, 2);
  assert.deepEqual(sent.recipients.map(r => r.name), ["Asha", "Ravi"]);
  const publicJson = JSON.stringify(relay.publicUpdates());
  for (const privateValue of [alice, bob, key, "Asha", "Ravi", ...sent.recipients.map(r => r.id)]) assert.ok(!publicJson.includes(privateValue));
  assert.throws(() => relay.pollReceipts(value.id, randomUUID()), { status: 404 });
  assert.equal((await relay.pollReceipts(value.id, key)).recipients.filter(r => r.confirmedAt !== null).length, 0);
  relay.acknowledge(sent.recipients[0].id, [value.id]);
  assert.equal((await relay.pollReceipts(value.id, key)).revision, 0, "Publicly returned per-update IDs cannot acknowledge for a private session");
  const confirmation = relay.pollReceipts(value.id, key, 0);
  relay.acknowledge(alice, [value.id]);
  const first = await confirmation;
  assert.equal(first.recipients.filter(r => r.confirmedAt !== null).length, 1);
  assert.equal(relay.records.size, 1);
  assert.equal(relay.publish(value, null, key).listeners, 2, "Retries retain the original eligible snapshot");
  assert.ok(sent.recipients.every(r => r.confirmedAt === null));
  relay.acknowledge(bob, [value.id]);
  assert.equal(relay.records.size, 0, "Last Inbox ACK immediately removes the activity payload");
  const final = await relay.pollReceipts(value.id, key, first.revision);
  assert.equal(final.complete, true);
  assert.equal(final.recipients.filter(r => r.confirmedAt !== null).length, 2);
  relay.forgetReceipts(value.id, key);
  assert.equal(relay.receipts.size, 0);
  assert.equal(relay.receiptWaiters.size, 0);
  relay.close(alice); relay.close(bob);
  assert.deepEqual(relay.activeListeners().listeners, []);
});

test("anonymous and duplicate names remain distinct recipients and old snapshots never rename", async () => {
  const relay = new Relay({ pollMs: 5 });
  const sessions = [randomUUID(), randomUUID(), randomUUID()];
  const polls = sessions.map((session, index) => relay.poll(session, undefined, index === 2 ? null : "Alex"));
  const value = activity(), key = randomUUID();
  const result = relay.publish(value, null, key);
  await Promise.all(polls);
  assert.deepEqual(result.recipients.map(r => r.name), ["Alex", "Alex", null]);
  assert.equal(new Set(result.recipients.map(r => r.id)).size, 3);
  await relay.poll(sessions[0], undefined, "Renamed");
  relay.acknowledge(sessions[0], [value.id]);
  const report = await relay.pollReceipts(value.id, key);
  assert.deepEqual(report.recipients.map(r => r.name), ["Alex", "Alex", null]);
  assert.equal(report.recipients.filter(r => r.confirmedAt !== null).length, 1);
  sessions.forEach(session => relay.close(session));
});

test("uncollected receipt metadata and names expire without durable history", async () => {
  let now = Date.now();
  const relay = new Relay({ now: () => now });
  const session = randomUUID(), key = randomUUID(), value = activity(now);
  const poll = relay.poll(session, undefined, "Temporary name");
  relay.publish(value, null, key);
  await poll;
  const waiting = relay.pollReceipts(value.id, key, 0);
  now += TTL_MS;
  relay.prune();
  const report = await waiting;
  assert.equal(report.expired, true);
  assert.equal(report.complete, false);
  assert.equal(report.recipients[0].confirmedAt, null);
  assert.equal(relay.records.size, 0);
  assert.equal(relay.receipts.size, 0);
  assert.equal(relay.listenerNames.size, 0);
  assert.equal(relay.receiptWaiters.size, 0);
  assert.throws(() => relay.pollReceipts(value.id, key), { status: 404 });
  const alone = relay.publish(activity(now), null, randomUUID());
  assert.deepEqual(alone.recipients, []);
  assert.equal(alone.receiptExpiresAt, null);
});

test("listener headers support UTF-8 names and reject malformed or misleading control text", () => {
  const encode = name => Buffer.from(name, "utf8").toString("base64");
  assert.equal(decodeListenerName(null), null);
  assert.equal(decodeListenerName(encode("  अनिरुद्ध  ")), "अनिरुद्ध");
  for (const header of ["!!!", "/w==", encode(" "), encode("a".repeat(81)), encode("A\nB"), encode("A\u202eB")]) {
    assert.throws(() => decodeListenerName(header), { status: 400 });
  }
});

test("coordinates reach both listeners unchanged and disappear after the last ACK", async () => {
  const now = Date.now();
  const relay = new Relay({ now: () => now });
  const first = randomUUID(), second = randomUUID();
  const a = relay.poll(first), b = relay.poll(second);
  const coordinates = { latitude: 12.345678, longitude: -76.54321, accuracyMeters: 24.5, capturedAt: now };
  const value = { ...activity(now), endAt: null, coordinates };
  assert.equal(relay.publish(value).listeners, 2);
  assert.deepEqual((await a)[0].coordinates, coordinates);
  assert.deepEqual((await b)[0].coordinates, coordinates);
  relay.acknowledge(first, [value.id]);
  assert.deepEqual(relay.publicUpdates()[0].coordinates, coordinates);
  relay.acknowledge(second, [value.id]);
  assert.deepEqual(relay.publicUpdates(), []);
});

test("optional coordinates are strictly validated and cannot carry extra identity fields", () => {
  const now = Date.now();
  const fix = { latitude: 0, longitude: 0, accuracyMeters: 0, capturedAt: now };
  const value = { ...activity(now), endAt: null, coordinates: fix };
  assert.deepEqual(validateActivity(value, now).coordinates, fix);
  for (const coordinates of [null, [], {}, { ...fix, latitude: 91 }, { ...fix, longitude: -181 },
    { ...fix, accuracyMeters: -1 }, { ...fix, accuracyMeters: NaN }, { ...fix, latitude: "0" },
    { ...fix, capturedAt: value.startAt - 1 }, { ...fix, capturedAt: now + 60_001 },
    { ...fix, capturedAt: now + 0.5 }, { ...fix, name: "private" }]) {
    assert.throws(() => validateActivity({ ...value, coordinates }, now), { status: 400 });
  }
  assert.throws(() => validateActivity({ ...value, endAt: now }, now), { status: 400 });
  assert.ok(!Object.hasOwn(validateActivity(activity(now), now), "coordinates"));
});

test("all listeners receive; first ACK does not delete another listener's update", async () => {
  const relay = new Relay();
  const alice = randomUUID(), bob = randomUUID();
  const a = relay.poll(alice), b = relay.poll(bob);
  const message = activity();
  assert.equal(relay.publish(message).listeners, 2);
  assert.equal((await a)[0].id, message.id);
  assert.equal((await b)[0].id, message.id);
  relay.acknowledge(alice, [message.id]);
  assert.equal(relay.publicUpdates().length, 1);
  relay.acknowledge(bob, [message.id]);
  assert.deepEqual(relay.publicUpdates(), []);
});

test("offline or new listeners cannot retrieve earlier updates", async () => {
  const relay = new Relay({ pollMs: 5 });
  const a = randomUUID(), offline = randomUUID();
  const waiting = relay.poll(a);
  relay.publish(activity());
  await waiting;
  assert.deepEqual(await relay.poll(offline), []);
  assert.equal(relay.publicUpdates().length, 1);
});

test("unacknowledged payload expires even with no further requests", async () => {
  let now = Date.now();
  const relay = new Relay({ now: () => now });
  const session = randomUUID();
  const waiting = relay.poll(session);
  relay.publish(activity(now));
  await waiting;
  now += TTL_MS;
  relay.prune();
  assert.equal(relay.records.size, 0);
  assert.equal(relay.sessions.size, 0);
});

test("a retry after a lost ACK returns same payload; repeated ACKs are safe", async () => {
  const relay = new Relay();
  const session = randomUUID(), message = activity();
  const waiting = relay.poll(session);
  relay.publish(message);
  await waiting;
  assert.equal((await relay.poll(session))[0].id, message.id);
  relay.acknowledge(session, [message.id]);
  relay.acknowledge(session, [message.id]);
  assert.equal(relay.records.size, 0);
});

test("new sends during the ACK / next-poll gap are preserved for active listeners", async () => {
  const relay = new Relay();
  const session = randomUUID();
  const waiting = relay.poll(session);
  const first = activity(), second = activity();
  relay.publish(first);
  await waiting;
  relay.acknowledge(session, [first.id]);
  assert.equal(relay.publish(second).listeners, 1);
  assert.equal((await relay.poll(session))[0].id, second.id);
});

test("sender's listener is excluded; a send with no other listeners is never retained", () => {
  const relay = new Relay();
  const sender = randomUUID();
  const waiting = relay.poll(sender);
  assert.equal(relay.publish(activity(), sender).listeners, 0);
  assert.equal(relay.records.size, 0);
  relay.close(sender);
  return waiting;
});

test("only an eligible recipient may acknowledge its copy", async () => {
  const relay = new Relay();
  const session = randomUUID(), message = activity();
  const waiting = relay.poll(session);
  relay.publish(message);
  await waiting;
  relay.acknowledge(randomUUID(), [message.id]);
  assert.equal(relay.records.size, 1);
});

test("public JSON has no listener tokens or identity fields", async () => {
  const relay = new Relay();
  const session = randomUUID();
  const waiting = relay.poll(session);
  relay.publish(activity());
  await waiting;
  const json = JSON.stringify(relay.publicUpdates());
  assert.ok(!json.includes(session));
  assert.ok(!json.includes("pending"));
  assert.throws(() => relay.publish({ ...activity(), username: "secret" }), { status: 400 });
});

test("invalid timestamps, bodies, zones and non-UUID IDs are rejected", () => {
  const now = Date.now();
  for (const changes of [{ startAt: now + 120_000 }, { endAt: now - 2_000_000 }, { body: "" },
    { body: "x".repeat(281) }, { timeZone: "invalid" }, { id: "x" }, { id: [randomUUID()] }, { startAt: "today" }, { endAt: undefined }]) {
    assert.throws(() => validateActivity({ ...activity(now), ...changes }, now), { status: 400 });
  }
});

test("aborted and explicitly closed listeners do not receive new sends", async () => {
  const relay = new Relay();
  const session = randomUUID(), controller = new AbortController();
  const waiting = relay.poll(session, controller.signal);
  controller.abort();
  assert.deepEqual(await waiting, []);
  assert.equal(relay.publish(activity()).listeners, 0);
  const second = relay.poll(session);
  relay.close(session);
  assert.deepEqual(await second, []);
  assert.equal(relay.publish(activity()).listeners, 0);
});

test("simultaneous activity IDs stay independent and duplicate pending POSTs are idempotent", async () => {
  const relay = new Relay();
  const session = randomUUID(), first = activity(), second = activity();
  const waiting = relay.poll(session);
  relay.publish(first);
  relay.publish(second);
  relay.publish(first);
  await waiting;
  assert.equal(relay.records.size, 2);
  assert.equal((await relay.poll(session)).length, 2);
  assert.throws(() => relay.publish({ ...first, body: "10k" }), { status: 409 });
});

test("a relay restart cannot recover payloads", async () => {
  const relay = new Relay();
  const waiting = relay.poll(randomUUID());
  relay.publish(activity());
  await waiting;
  assert.equal(new Relay().records.size, 0);
});
