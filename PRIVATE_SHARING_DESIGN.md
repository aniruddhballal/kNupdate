# Private Recipient Sharing

## Decision

kNupdate should support both:

1. **Recipient targeting:** the relay delivers an update only to explicitly selected, authenticated profiles.
2. **End-to-end encryption (E2EE):** only the sender and selected recipients can decrypt the activity or location payload.

These controls solve different problems. Recipient targeting prevents unrelated listeners from receiving an update. E2EE prevents the relay, its operator, or an infrastructure compromise from reading the payload.

The recommended implementation order is:

1. Design stable profile identity and key management.
2. Implement authenticated, targeted delivery.
3. Add E2EE before treating private location sharing as production-secure.

The identity and key model must be designed together from the beginning, even though targeted delivery is the first user-visible feature.

## Current state

The current prototype is a public broadcast system:

- It has no accounts or connection graph.
- `/v1/poll` uses a temporary random listener session, not an authenticated identity.
- Listener display names are self-entered labels and do not prove who someone is.
- The relay snapshots every active listener when an update is published.
- `/v1/updates` exposes pending updates through a public relay endpoint.
- Location coordinates are plaintext in the relay process memory until delivery, acknowledgement, or expiry.

The random session UUID and HTTPS transport are not sufficient access control. HTTPS protects traffic in transit; it does not decide which application profile may receive an update or prevent the relay from reading the request body.

## Proposed model

### Profiles and pairing

- Create a stable profile/device identity instead of treating a listener session as a person.
- Generate a public/private key pair per device.
- Keep the private key on the device, protected by Android Keystore where supported.
- Register only the public key and profile metadata with the backend.
- Pair profiles using an invite link, short-lived code, or QR code.
- Show a key fingerprint or equivalent verification step when stronger identity verification is required.
- Keep connection sessions separate from profile identity; a session may expire or rotate without changing the profile.

Display names may remain as user-facing labels, but they must not be used as authorization identifiers.

### Targeted delivery

Each send should specify a recipient set containing profile IDs or group IDs. The backend must:

- Authenticate the sender on every relevant request.
- Verify that the sender owns or may use the sending profile.
- Verify that each selected recipient is currently authorized.
- Deliver only to the selected recipients.
- Snapshot the authorized recipient set at publish time.
- Apply deny-by-default behavior when identity, membership, or policy cannot be verified.
- Prevent an unselected profile from polling, acknowledging, or reading the update.

Groups should support membership changes. Removing someone must prevent future sends, although it cannot revoke an update that the person already received or decrypted.

### End-to-end encrypted payloads

For each update:

1. Generate a fresh random content key.
2. Encrypt the activity and coordinates with authenticated encryption.
3. Wrap the content key separately for each recipient public key.
4. Send the ciphertext and recipient key envelopes to the relay.
5. Let each authorized recipient unwrap and decrypt its copy locally.

For the initial small-recipient use case, per-recipient key envelopes are simpler than a shared group key. A later group-key optimization must rotate keys when membership changes.

Use an established, audited cryptographic protocol or library. Do not invent a custom key exchange, encryption format, or ratchet. E2EE does not hide all metadata: the relay may still know that a sender contacted particular recipient IDs, when it happened, message size, and delivery status.

## First implementation milestone

The first private-sharing release should provide:

- Stable profile/device identity.
- Pairing or invitation flow.
- One-to-one recipient selection.
- A small group recipient list.
- Authenticated backend requests.
- Server-side recipient authorization.
- Default-deny behavior.
- Targeted delivery and targeted acknowledgements.
- Removal/revocation of future access.
- Tests proving an unauthorized profile cannot poll, read, acknowledge, or appear as an eligible recipient.

The existing public broadcast path should not remain the default once private sharing is enabled. If public broadcasts are retained, they should be an explicit user choice with a clear warning.

## Security and product constraints

- An authorized recipient can still copy, screenshot, or forward decrypted data.
- Revoking a profile affects future updates; it cannot erase data already delivered.
- Lost devices require key revocation and re-pairing from another trusted device.
- Device replacement and multi-device profiles need an explicit key lifecycle policy.
- Receipts should reveal only the minimum delivery information required by the sender.
- Private location sharing should not be considered complete until the payload is encrypted end to end.

## References

- [OWASP Authorization Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html)
- [Android Keystore system](https://developer.android.com/privacy-and-security/keystore)
- [Signal X3DH specification](https://signal.org/docs/specifications/x3dh/)
