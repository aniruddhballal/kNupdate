import test from "node:test";
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { setTimeout as delay } from "node:timers/promises";

const base = process.env.RELAY_TEST_URL ?? "http://127.0.0.1:8787";
const headers = (session, name, receiptKey) => ({ "Content-Type": "application/json",
  ...(session ? { "X-Relay-Session": session } : {}),
  ...(name ? { "X-Relay-Name": Buffer.from(name, "utf8").toString("base64") } : {}),
  ...(receiptKey ? { "X-Receipt-Key": receiptKey } : {}) });
const message = () => ({ id: randomUUID(), title: "Run", body: "5k", startAt: Date.now() - 1_200_000, endAt: Date.now() - 1, timeZone: "Asia/Calcutta" });
async function jsonResponse(response) {
  const context = response.headers.get("cf-mitigated") === "challenge" ? " Hosting returned a browser challenge." : "";
  assert.ok(response.ok, `HTTP ${response.status}.${context}`);
  assert.match(response.headers.get("content-type") ?? "", /application\/json/, "Relay must return JSON, not a hosting page.");
  return response.json();
}
const post = async (path, body, session, receiptKey) => {
  const result = await fetch(base + path, { method: "POST", headers: headers(session, null, receiptKey), body: JSON.stringify(body) });
  return jsonResponse(result);
};
// Observe rejection immediately while POST and ACK requests proceed concurrently.
const poll = (session, name) => fetch(base + "/v1/poll", { headers: headers(session, name) }).then(jsonResponse)
  .then(response => ({ response }), error => ({ error }));
const pollResult = async pending => {
  const result = await pending;
  if (result.error) throw result.error;
  return result.response;
};
const current = async () => (await jsonResponse(await fetch(base + "/v1/updates"))).updates;

test("HTTP broadcast, independent ACKs, public schema, offline miss, and expiry", { timeout: 90_000 }, async t => {
  const health = await fetch(base + "/health");
  assert.equal((await jsonResponse(health)).protocol, 1);
  assert.match(health.headers.get("cache-control"), /no-store/);
  const a = randomUUID(), b = randomUUID(), late = randomUUID();
  const expirySession = randomUUID();
  t.after(async () => {
    await Promise.allSettled([a, b, late, expirySession].map(session =>
      fetch(base + "/v1/poll", { method: "DELETE", headers: headers(session) })));
  });
  const first = poll(a), second = poll(b);
  await delay(750);
  const activity = { ...message(), title: "Current location", body: "Synthetic location test", endAt: null,
    coordinates: { latitude: 12.345678, longitude: -76.54321, accuracyMeters: 24.5, capturedAt: Date.now() } };
  assert.equal((await post("/v1/updates", activity)).listeners, 2);
  const receivedFirst = (await pollResult(first)).updates[0];
  const receivedSecond = (await pollResult(second)).updates[0];
  assert.equal(receivedFirst.id, activity.id);
  assert.equal(receivedSecond.id, activity.id);
  assert.deepEqual(receivedFirst.coordinates, activity.coordinates);
  assert.deepEqual(receivedSecond.coordinates, activity.coordinates);
  const publicJson = JSON.stringify(await current());
  assert.ok(!publicJson.includes(a) && !publicJson.includes(b));
  const latePoll = poll(late);
  await post("/v1/ack", { ids: [activity.id] }, a);
  assert.equal((await current()).length, 1);
  await post("/v1/ack", { ids: [activity.id] }, b);
  assert.equal((await current()).length, 0);
  await fetch(base + "/v1/poll", { method: "DELETE", headers: headers(late) });
  assert.deepEqual((await pollResult(latePoll)).updates, []);

  const waiting = poll(expirySession);
  await delay(500);
  const expiring = message();
  await post("/v1/updates", expiring);
  assert.equal((await pollResult(waiting)).updates[0].id, expiring.id);
  assert.equal((await current()).length, 1);
  await delay(31_000);
  assert.equal((await current()).length, 0);
  for (const session of [a, b, expirySession]) await fetch(base + "/v1/poll", { method: "DELETE", headers: headers(session) });
  assert.equal((await post("/v1/updates", message())).listeners, 0);
  assert.deepEqual(await current(), []);
});

test("HTTP rejects identity fields, malformed requests and oversized payloads", async () => {
  for (const [body, expected] of [[{ ...message(), name: "private" }, 400], ["bad json", 400], ["x".repeat(5000), 413]]) {
    const response = await fetch(base + "/v1/updates", { method: "POST", headers: headers(), body: typeof body === "string" ? body : JSON.stringify(body) });
    assert.equal(response.status, expected);
    assert.ok(!JSON.stringify(await response.json()).includes("private"));
  }
  assert.equal((await fetch(base + "/v1/poll")).status, 400);
});

test("HTTP directory, frozen names and private confirmations follow real recipient acknowledgements", { timeout: 20_000 }, async t => {
  const first = randomUUID(), second = randomUUID(), key = randomUUID();
  const value = { ...message(), title: "Synthetic named-listener test", body: "Local test only" };
  t.after(async () => {
    await Promise.allSettled([first, second].map(session => fetch(base + "/v1/poll", { method: "DELETE", headers: headers(session) })));
    await fetch(base + "/v1/receipts/" + value.id, { method: "DELETE", headers: headers(null, null, key) });
  });
  const a = poll(first, "HTTP Asha"), b = poll(second, "HTTP Ravi");
  await delay(500);
  const directory = await jsonResponse(await fetch(base + "/v1/listeners", { headers: headers(first) }));
  assert.equal(directory.listeners.length, 2);
  assert.ok(directory.listeners.some(listener => listener.name === "HTTP Asha" && listener.self));
  assert.ok(!JSON.stringify(directory).includes(first) && !JSON.stringify(directory).includes(second));
  const sent = await post("/v1/updates", value, null, key);
  assert.equal(sent.listeners, 2);
  assert.deepEqual(new Set(sent.recipients.map(r => r.name)), new Set(["HTTP Asha", "HTTP Ravi"]));
  const received = await Promise.all([pollResult(a), pollResult(b)]);
  for (const response of received) {
    const content = JSON.stringify(response);
    for (const privateValue of ["HTTP Asha", "HTTP Ravi", key, first, second]) assert.ok(!content.includes(privateValue));
  }
  const endpoint = base + "/v1/receipts/" + value.id;
  assert.equal((await fetch(endpoint, { headers: headers(null, null, randomUUID()) })).status, 404);
  const waiting = fetch(endpoint + "?after=0", { headers: headers(null, null, key) }).then(jsonResponse);
  await post("/v1/ack", { ids: [value.id] }, first);
  const partial = await waiting;
  assert.equal(partial.complete, false);
  assert.equal(partial.recipients.filter(r => r.confirmedAt !== null).length, 1);
  assert.equal((await current()).length, 1);
  await post("/v1/ack", { ids: [value.id] }, second);
  assert.deepEqual(await current(), []);
  const final = await jsonResponse(await fetch(endpoint + "?after=" + partial.revision, { headers: headers(null, null, key) }));
  assert.equal(final.complete, true);
  assert.equal(final.recipients.filter(r => r.confirmedAt !== null).length, 2);
  await jsonResponse(await fetch(endpoint, { method: "DELETE", headers: headers(null, null, key) }));
  assert.equal((await fetch(endpoint, { headers: headers(null, null, key) })).status, 404);
});
