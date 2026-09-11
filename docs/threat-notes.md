# kNupdate Threat Notes

This is a small privacy-sensitive application, not a proof or verification system. Users may enter implausible quantities; the app only enforces basic input validity.

## Threats and controls

| Threat | Required control |
| --- | --- |
| Unauthorized feed access | Authenticate every request and authorize each activity against current mutual connections on the server. |
| Guessable user identity | Use UUIDs; expose no phone number, username, directory, or sequential ID. |
| Phone-number exposure | Hash phone numbers in the application database; avoid returning or logging them. |
| OTP abuse | Rate-limit request and verification attempts; use provider-side expiry and attempt limits. |
| Connection-code guessing/replay | Store only a hash; make codes short-lived, single-use, rate-limited, and invalid after redemption. |
| Connection-rule bypass | Calculate mutual eligibility server-side; never trust client-supplied mutual counts. |
| Removed-user access | Re-check connection state on every feed/activity read and notification recipient selection. |
| Token theft on device | Store tokens in Android Keystore-backed encrypted storage; do not put them in logs or backups. |
| Duplicate activity submissions | Use an idempotency strategy or safely reject retries at the API boundary. |
| Activity retention beyond promise | Filter expired rows in reads, hide them locally, and run a deletion worker at least every 15 minutes. |
| Data surviving in operations systems | Omit activity payloads from logs, analytics, exception reports, queues, caches, exports, and audit trails. |
| Accidental social expansion | Keep absent features absent: no search, media, sharing, engagement, ranking, analytics, or public URLs. |
| Compromised backend credentials | Keep secrets outside git, use least-privilege database credentials, require TLS, and document rotation. |
| Malicious client | Treat the Android client as untrusted; enforce every product and privacy rule in the backend. |

## Things the product does not promise

- It does not verify that an activity actually happened.
- It cannot prevent a user from photographing or reproducing a screen with another device.
- It cannot prevent Android or a compromised device from taking screenshots.
- It does not promise recovery after account deletion or activity expiry.
- It should not be deployed with infrastructure backups or logs that contradict the hard-deletion promise.

## Security review gate

Before the first real-circle release, verify:

- [ ] Every data-bearing endpoint has an authorization test.
- [ ] Removed connections cannot retrieve new data.
- [ ] Expired activities are excluded before worker deletion and absent after worker deletion.
- [ ] Logs contain no sensitive payloads.
- [ ] Production secrets, signing keys, and database dumps are outside the repository.
- [ ] Dependencies and Android permissions have been reviewed for unnecessary tracking or access.
