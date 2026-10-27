import test from "node:test";
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { Relay, TTL_MS, validateActivity } from "../src/relay.js";

const activity = (now = Date.now()) => ({ id: randomUUID(), title: "Run", body: "5k", startAt: now - 1_200_000, endAt: now - 1, timeZone: "Asia/Calcutta" });

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
