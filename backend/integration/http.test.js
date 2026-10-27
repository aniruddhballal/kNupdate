import test from "node:test";
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { setTimeout as delay } from "node:timers/promises";

const base = process.env.RELAY_TEST_URL ?? "http://127.0.0.1:8787";
const device = name => ({ id: randomUUID(), token: randomUUID(), name });
const headers = (value, session = null, receiptKey = null) => ({ "Content-Type": "application/json",
  ...(value ? { "X-Device-Id": value.id, "X-Device-Token": value.token } : {}),
  ...(session ? { "X-Relay-Session": session } : {}),
  ...(receiptKey ? { "X-Receipt-Key": receiptKey } : {})
});
const message = () => ({ id: randomUUID(), title: "Run", body: "5k", startAt: Date.now() - 1_200_000, endAt: Date.now() - 1, timeZone: "Asia/Calcutta" });
async function jsonResponse(response) {
  const context = response.headers.get("cf-mitigated") === "challenge" ? " Hosting returned a browser challenge." : "";
  assert.match(response.headers.get("content-type") ?? "", /application\/json/, "Relay must return JSON, not a hosting page.");
  const body = await response.json();
  assert.ok(response.ok, `HTTP ${response.status}: ${JSON.stringify(body)}.${context}`);
  return body;
}
const request = async (method, path, value, body = undefined, session = null, receiptKey = null) => {
  const response = await fetch(base + path, { method, headers: headers(value, session, receiptKey),
    body: body === undefined ? undefined : JSON.stringify(body) });
  return jsonResponse(response);
};
const poll = (value, session, controller) => fetch(base + "/v1/poll", { signal: controller?.signal, headers: headers(value, session) })
  .then(jsonResponse).then(response => ({ response }), error => ({ error }));
const pollResult = async pending => {
  const result = await pending;
  if (result.error) throw result.error;
  return result.response;
};

test("HTTP pairing and targeted delivery never exposes a pending update publicly", { timeout: 40_000 }, async t => {
  const alice = device("HTTP Alice"), bob = device("HTTP Bob"), carol = device("HTTP Carol");
  for (const value of [alice, bob, carol]) await request("POST", "/v1/devices/register", value, { deviceId: value.id, deviceToken: value.token, name: value.name });
  const invite = await request("POST", "/v1/pairing/invite", alice);
  const paired = await request("POST", "/v1/pairing/accept", bob, { code: invite.code });
  assert.equal(paired.peer.id, alice.id);
  assert.deepEqual((await request("GET", "/v1/peers", alice)).peers.map(value => value.name), ["HTTP Bob"]);
  assert.deepEqual((await request("GET", "/v1/peers", bob)).peers.map(value => value.name), ["HTTP Alice"]);

  const bobSession = randomUUID(), carolSession = randomUUID(), key = randomUUID();
  const carolAbort = new AbortController();
  const bobWaiting = poll(bob, bobSession);
  const carolWaiting = poll(carol, carolSession, carolAbort);
  const activity = { ...message(), title: "Current location", body: "Synthetic location test", endAt: null,
    coordinates: { latitude: 12.345678, longitude: -76.54321, accuracyMeters: 24.5, capturedAt: Date.now() } };
  const sent = await request("POST", "/v1/updates", alice, { activity, recipientIds: [bob.id] }, null, key);
  assert.equal(sent.listeners, 1);
  assert.equal((await pollResult(bobWaiting)).updates[0].id, activity.id);
  carolAbort.abort();
  const carolResult = await carolWaiting;
  assert.ok(carolResult.error, "The unselected listener must not receive an update.");

  assert.equal((await request("POST", "/v1/ack", bob, { ids: [activity.id] }, bobSession)).acknowledged, true);
  const receipt = await request("GET", `/v1/receipts/${activity.id}?after=-1`, alice, undefined, null, key);
  assert.equal(receipt.complete, true);
  await request("DELETE", `/v1/receipts/${activity.id}`, alice, undefined, null, key);

  const publicResponse = await fetch(base + "/v1/updates");
  assert.equal(publicResponse.status, 404);
  assert.equal((await fetch(base + "/v1/poll", { headers: { "X-Relay-Session": randomUUID() } })).status, 401);
  t.after(async () => {
    await Promise.allSettled([
      fetch(base + "/v1/poll", { method: "DELETE", headers: headers(bob, bobSession) }),
      fetch(base + "/v1/poll", { method: "DELETE", headers: headers(carol, carolSession) })
    ]);
  });
});

test("HTTP tree delivery reaches all active listeners without requiring pairing", { timeout: 40_000 }, async t => {
  const alice = device("Tree Alice"), bob = device("Tree Bob"), carol = device("Tree Carol");
  for (const value of [alice, bob, carol]) await request("POST", "/v1/devices/register", value, { deviceId: value.id, deviceToken: value.token, name: value.name });
  const bobSession = randomUUID(), carolSession = randomUUID();
  const bobWaiting = poll(bob, bobSession);
  const carolWaiting = poll(carol, carolSession);
  const activity = { ...message(), title: "Tree update", body: "Visible to all active listeners" };
  const sent = await request("POST", "/v1/updates", alice, { activity, mode: "tree" });
  assert.equal(sent.mode, "tree");
  assert.equal(sent.listeners, 2);
  assert.equal((await pollResult(bobWaiting)).updates[0].id, activity.id);
  assert.equal((await pollResult(carolWaiting)).updates[0].id, activity.id);
  t.after(async () => {
    await Promise.allSettled([
      fetch(base + "/v1/poll", { method: "DELETE", headers: headers(bob, bobSession) }),
      fetch(base + "/v1/poll", { method: "DELETE", headers: headers(carol, carolSession) })
    ]);
  });
});
