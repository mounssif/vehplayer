# vehplayer - Processing Register (draft, Art. 30 GDPR-style)

> **Status: draft, not legal advice.** A working internal record of
> processing activities, written from the actual codebase so a real
> privacy/legal review has an accurate starting point rather than a blank
> page. Not itself a substitute for that review.
> (`docs/MARKET_AND_PRICING.md` §9.4).

| Processing activity | Data involved | Purpose | Legal basis (draft) | Retention | Where it happens | Third parties |
|---|---|---|---|---|---|---|
| Screen/audio mirroring | Live screen content, live audio | Core product function | Contract (providing the requested service) | Not retained - never stored, in transit only, phone-to-car | Local network only (phone ↔ car), never our infrastructure | None |
| Touch input relay | Touch coordinates/gesture events from the car screen | Core product function (control) | Contract | Not retained | Local network only | None |
| Now Playing | Currently-playing media metadata (title/artist/art), read via Android's `MediaSessionManager` | Display feature | Contract | Not retained, read live, not stored | On-device only | None |
| Messages overview | Notification metadata (sender, preview text, timestamp) from messaging-app notifications | Display feature | Contract | Not retained, read live, not stored | On-device only | None - explicitly does not read WhatsApp/RCS message content (no such API exists) |
| Phone tile | Call log entries, contact names/numbers | Display feature | Contract | Not retained, read live, not stored | On-device only | None |
| Navigate / location | Coarse + fine device location | Positioning the map, routing | Contract | Not retained beyond the active map session | On-device + Mapbox (see below) | Mapbox (map tiles, geocoding, routing - `[MENS: confirm Mapbox's own DPA/processor status before this is published]`) |
| Entitlements check | Device identifier, purchase status | Verifying Pro purchase | Contract | `[MENS: confirm actual retention once entitlements.ts moves off the placeholder device-ID check, see docs/NEXT_SESSION.md]` | `cloud/` Cloudflare Worker | Cloudflare (infrastructure processor) |
| Compatibility telemetry | Reachability tier result, Tesla firmware version | Building the public compatibility matrix | Consent (opt-in) | `[MENS: confirm retention period]` | `cloud/` Cloudflare Worker | Cloudflare (infrastructure processor) |
| App update check | App version, requests to GitHub Releases | Checking for/installing updates | Legitimate interest (keeping the app functional/secure) | Not retained by us - handled by GitHub's own infrastructure | GitHub | GitHub |

## What is explicitly NOT processed

- Message content (SMS, WhatsApp, RCS, or any other messaging app) - no
  API exists to read it, and none is used.
- Call audio or recordings.
- Contact data beyond name/number needed to display and dial.
- Any data sold, shared for advertising, or used for cross-app tracking.

## Open items before this is a real Art. 30 register

- `[MENS]`: legal entity name, registered address, DPO contact (if
  applicable) - required fields for a real Art. 30 register, not
  fabricated here.
- `[MENS]`: confirm Mapbox's and Cloudflare's processor agreements are in
  place before asserting their role in this table as final.
- `[MENS]`: confirm actual data retention periods for the `cloud/` Worker
  once `entitlements.ts`'s placeholder device-ID check is replaced with a
  real signed-token approach (`docs/NEXT_SESSION.md` flags this as not
  trustworthy yet).
