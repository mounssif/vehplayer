# CLAUDE.md

Project conventions for `vehplayer`. Load this first, every session - it's
the fast-path summary of rules that otherwise have to be re-derived from
`docs/VEHPLAYER_Foundation.md`, `docs/ARCHITECTURE.md`, `docs/GROWTH_SAAS.md`,
and `docs/NEXT_SESSION.md`. Read those four (plus `docs/COMPETITIVE_REASSESSMENT.md`
and `docs/MARKET_AND_PRICING.md` for market/pricing context, and
`docs/DIFFERENTIATOR_FEATURES.md` for the Android Auto/CarPlay structural-
limits research and feature ideas that follow from it) for the full
reasoning; this file is the checklist, not the argument.

## What this is

A car dashboard for Tesla, delivered by mirroring a purpose-built Android
Activity to the car's in-car browser over the phone's own WiFi hotspot.
**Not** a phone-mirroring utility that happens to have a dashboard bolted
on - the dashboard is the product, mirroring is plumbing
(`VEHPLAYER_Foundation.md` §3). No Android Auto/CarPlay protocol
dependency, anywhere, ever (Lesson 1, the single load-bearing
architectural bet).

## Commands

See `Makefile` at repo root for the canonical targets
(`make setup|android|webclient|deploy-web|harness|latency|release`). The
manual equivalents, if the Makefile isn't available for some reason:

- **Android**: `cd android && ./gradlew assembleDebug` (add
  `-PvehplayerVersionCode=N -PvehplayerVersionName=...` when building
  something that will actually be published - see the release lesson
  below).
- **Webclient**: `cd webclient && npm install && npm run typecheck && npm run build`.
- **Webclient deploy**: auto via Cloudflare Workers Builds on push to
  main, once the founder completes the one-time connect (session 8
  decision, delegated by the founder: two real stale-deploy incidents in
  sessions 3/5 made "deploy equals latest main" the end-user-safest
  option; CF-side builds, so still no GitHub Actions, honoring the
  original preference). Until connected - and as fallback - manual:
  `cd webclient && npm run build && npx wrangler deploy` (needs
  `npx wrangler login` or `CLOUDFLARE_API_TOKEN`/`CLOUDFLARE_ACCOUNT_ID`).
  Setup checklist lives in `docs/NEXT_SESSION.md` session 8.
- **Cloud Worker**: `cd cloud && npm install && npm run typecheck`.
- **Latency harness**: `cd validate/latency-harness && python measure_latency.py --selftest`.
- **Fake sender (dev, no car needed)**: `cd webclient && npm run harness`.

## House rules

- **No em-dashes.** No specific delivery time estimates - plan by
  gate/sequence, never by weeks or months.
- **Never claim to *be* Android Auto or CarPlay**, anywhere user-facing.
  "Android Auto"/"CarPlay" only appear in descriptive comparison copy
  (store long description, SEO), never in the app name, icon, title, or
  as an implied endorsement (`VEHPLAYER_Foundation.md` §1, §2).
- **Data plane is local, always. Cloud is control plane only, always**
  (`cloud/src/index.ts`'s own header comment: "Cloud never sees a video
  frame"). Any future architecture proposal that routes media through
  cloud infrastructure - even ephemeral, even unstored - needs to clear
  this bar explicitly before being built. A cloud WebSocket relay was
  proposed and rejected for exactly this reason in session 6; the
  peer-to-peer VpnService/CGNAT-address fix was built instead.
- **Never declare `isAccessibilityTool=true`** in the Play Console
  declaration. vehplayer is not an assistive product; a false declaration
  risks account termination (`MARKET_AND_PRICING.md` §7.1).
- **The free tier is the product, not a demo.** Full dashboard experience
  (now-playing, phone, messages, mirror + touch) is free, no time limit,
  no nag. Only Pro-gated features cost real ongoing money to run (in-app
  navigation) or unlock a genuine convenience upgrade (Power Mode,
  low-latency audio) - see `GROWTH_SAAS.md` §4.
- **WebCodecs-to-canvas, not `<video>`, for anything that needs to work in
  Drive.** Tesla's in-car browser suppresses `<video>` element playback
  while the car is in Drive (`ARCHITECTURE.md` §2, `MARKET_AND_PRICING.md`
  §3). `mseFallback.ts`'s `<video>`-based path is real but deliberately
  deprioritized, not a required deliverable.
- **GPL hygiene**: architecture *ideas* from Castla are fair game to learn
  from; no code is ever copied or closely modeled from it (GPL-3.0).
- **Evidence tags on every browser/market claim**: MEASURED (verified on
  our own car/repo), REPORTED (a public source says so, cite it), ASSUMED
  (our inference, flagged as such). Don't state a Tesla-browser behavior
  or a market number as fact without one of these.
- **`[MENS]` facts stay open until confirmed.** Purchases, registrations,
  payments, domain buys - document as an intention with an open status
  until the founder confirms it actually happened, never as a settled fact
  in a table (a lesson learned the hard way on an unrelated project's
  domain purchase, applied here proactively - `VEHPLAYER_Foundation.md`
  §12).
- **Session-end doc refresh**: update `docs/NEXT_SESSION.md` with what
  actually got built/verified/still-open before ending a session. It's
  the changelog and the next session's kickoff prompt at once.
- **The release lesson** (`NEXT_SESSION.md`, a real incident, builds 5/6):
  always run `aapt dump badging <apk> | head -1` on the *exact file* about
  to be uploaded and confirm the versionCode before `gh release create` -
  an unversioned `./gradlew assembleDebug` run "just to be sure" silently
  shipped versionCode=1 under a `build-6` tag once. `make release` encodes
  this check so it can't recur silently.
- **Scope-creep tripwires** (`NEXT_SESSION.md`): any pull toward a second
  car brand, Fleet tier, or paid marketing before Gate 4 is complete; any
  dependency reintroducing Android Auto/CarPlay protocol code; any code
  copied or closely modeled from Castla.

## Where things live

- `android/` - the real Kotlin app. `CaptureService` (MediaProjection
  capture), `H264Encoder`, `HttpAssetServer` (local pairing server),
  `VehplayerAccessibilityService` (input injection), `net/` (the
  reachability ladder - `ReachabilityProbe`/`ReachabilityLadder`/
  `VpnReachabilityService`), `dashboard/` (`CarDashboardActivity` and its
  overlays - Navigate, Messages, Phone, the destination search keyboard),
  `media/` (`VehplayerNotificationListenerService`, shared by Now Playing
  and Messages).
- `webclient/` - TypeScript car-side client + desktop test harness.
  `videoDecoder.ts`/`nalu.ts` (WebCodecs decode, the real path),
  `mseFallback.ts` (deprioritized, see above), `wsClient.ts`,
  `inputSender.ts`, `audioPlayer.ts`.
- `cloud/` - Cloudflare Workers control plane (entitlements, remote
  config/kill-switches, compat telemetry). **Never media.**
- `validate/` - Gate-1 spike templates + the latency harness.
- `docs/` - see the list at the top of this file.
- `legal/` - privacy policy, terms, processing register, trademark note.
  Required before any Play Store listing.

## Current real blocker

A `/go` round trip has never been completed in an actual Tesla, across
every session so far (`docs/NEXT_SESSION.md`). Everything else is
speculative-but-evidence-backed until that passes.
