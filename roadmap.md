# kNupdate roadmap

## Implemented

- Local display name and structured activity composer, including detail choices and Run / 5k.
- Real timestamps and local sent/received SQLite histories.
- Migration of the previous local history without uploading it.
- Public plaintext relay using Cloudflare Workers and one Durable Object.
- Activity payloads held in memory only; no cloud database or file history.
- Long polling, ephemeral listener sessions, multiple simultaneous recipients and updates.
- Local commit before acknowledgement; duplicate suppression.
- Deletion after all eligible acknowledgements, with a 30-second fallback expiry.
- Live-only delivery; no offline backlog or automatic delayed sends.
- Android foreground receiving service, incoming notifications and stop action.
- Timeout handling, offline status and retry backoff.
- Relay configuration and health check from the app.
- Disabled cloud/device-transfer backups for local data.
- Relay unit/HTTP tests and Android SQLite/model tests.

## Deployment and device acceptance

- [x] Claim the deployed Cloudflare relay account (26 October 2026).
- [x] Pass HTTP integration and Android client/SQLite round-trip tests against the claimed live endpoint.
- Install the configured APK on at least two phones.
- Verify notification permissions, screen-off receiving, internet toggling and OEM battery behavior.
- Verify Android foreground-service timeout and explicit restart on a physical device.
- Monitor free-tier quotas during real use; retain the Free plan to avoid spending.

## Later

1. Design routing only to connections without putting names in activity payloads.
2. Design and review end-to-end encryption and any shared-folder transport.
3. Decide whether best-effort background receiving meets the product need or requires a push transport.
4. Add abuse controls before treating the unrestricted public endpoint as a broad public service.

No accounts, permanent device identifiers, connection graph, offline catch-up, cloud activity history, or encryption are implemented in this phase.
