# vehplayer , asset & tooling repo

A car dashboard for your Tesla. Not a phone on a big screen. No box, no dongle, no
Shizuku required for the free tier. `vehplayer` is the real, locked product name
(see docs/VEHPLAYER_Foundation.md §1 - the old "VEPLA" name is retired).
See `docs/VEHPLAYER_Foundation.md` for full project context (name, vision, business model) and
`docs/ARCHITECTURE.md` for the technical single source of truth (pipeline, wire protocol, Gate-1 spikes).
Growth/SaaS reasoning in full: `docs/GROWTH_SAAS.md`. Real competitive/market research:
`docs/COMPETITIVE_REASSESSMENT.md`, `docs/MARKET_AND_PRICING.md`. Session-to-session state: `docs/NEXT_SESSION.md`.

## docs/
- `VEHPLAYER_Foundation.md`  single source of truth. Load into project context every session.
- `ARCHITECTURE.md`      video/audio pipeline, wire protocol, reachability ladder, Gate-1 spike protocols.
- `GROWTH_SAAS.md`       positioning, monetization, GTM sequencing, moat reasoning.
- `COMPETITIVE_REASSESSMENT.md` / `MARKET_AND_PRICING.md`  real competitor + market data (19 July 2026), evidence-tagged.
- `NEXT_SESSION.md`      kickoff prompt for the next session, also the changelog of what's real vs stubbed.

## android/ (real Gate-2 pipeline code, BUILDS clean and RUNS on a real emulator)
Kotlin, package `app.vehplayer.android`. `CaptureService` owns MediaProjection mirror-mode
capture, `H264Encoder` (MediaCodec, low-latency config per ARCHITECTURE.md §2),
`PlaybackAudioCapture` + `AacEncoder` (Route B), `LocalMediaServer` (Java-WebSocket, the wire
protocol), `HttpAssetServer` (NanoHTTPD, serves the cached webclient bundle + mints pairing
tokens at `/go`), `VehplayerAccessibilityService` (dispatchGesture input injection with live
`StrokeDescription` continuation, not buffer-and-replay), `ReachabilityLadder` +
`ReachabilityProbe` (tier (a) IPv6 detection, ARCHITECTURE.md §6b), `VpnReachabilityService`
(tier (c), CGNAT address assignment, evidence-backed against a real competitor, NOT yet
real-hardware-confirmed - see NEXT_SESSION.md session 6). `MainActivity` is the real two-minute setup flow.
`./gradlew assembleDebug` passes clean; installed and smoke-tested end to end on a real
emulator (onboarding flow, accessibility binding, HTTP asset serving, reachability ladder),
see NEXT_SESSION.md for exactly what was exercised and what still needs a real car/phone.

## webclient/ (TypeScript car client + desktop test harness, TYPE-CHECKED and BUILT clean)
`npm install && npm run typecheck && npm run build` all pass as of this session. Real
WebCodecs decode path (`videoDecoder.ts`, `nalu.ts` Annex B <-> AVCC + SPS/PPS extraction),
real AudioWorklet playback (`audioPlayer.ts`, `worklets/ringBufferProcessor.ts`), real input
channel (`inputSender.ts`), real adaptive-quality client logic (`qualityLadder.ts`), real WS
client (`wsClient.ts`). MSE fallback (`mseFallback.ts`) has full plumbing but the actual
Annex-B-to-fMP4 muxer is a scoped TODO, deliberately not hand-rolled blind (see that file's
doc comment for why and the exact box layout to implement). `harness/fakeSender.mjs` is a
working fake phone for developing without a car (`npm run harness`).
`probe/` is the separate, already-deployed S2 capability probe (`DEPLOY_CF_PAGES.md`), unrelated
to this real client beyond sharing a repo.

## cloud/ (Cloudflare Workers control plane, TYPE-CHECKED clean)
Entitlements, per-firmware remote config/kill-switches, opt-in compat telemetry ingest. See
`cloud/README.md` for setup and what's stubbed (KV namespace IDs, entitlements auth).

## validate/ (Gate-1 spike templates + a working latency harness)
`S1_reachability.md`, `S2_browser_capability.md` templates. `latency-harness/measure_latency.py`
is real and self-tested (`--selftest` generates a synthetic clip with a known injected delay
and confirms the detection recovers it, already run once this session: 116.7ms measured vs
120ms injected). Real-footage measurement still needs an actual car (see its README).

## brand/  (name/domain/trademark research now, visual identity at Gate 5)
Reserved. No assets yet, Foundation §1's domain/trademark TODO blocks anything shipping here.

## house rules (also in Foundation)
- No em-dashes. No specific delivery time estimates (plan by gate/sequence, never by weeks or months).
- Never claim to *be* Android Auto or CarPlay, anywhere user-facing (`GROWTH_SAAS.md` §2).
- Data plane is local, always. Cloud is control plane only, always.
- Every architecture claim about the car browser carries an evidence tag: MEASURED (our car),
  REPORTED (other projects), or ASSUMED (verify at Gate 1).
- Every file with real unverified assumptions is marked `TODO(claude-code):` inline, search the
  tree for that string to find every open thread from this session.
