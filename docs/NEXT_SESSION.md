# vehplayer - Next Session Kickoff

> For Claude Code, running locally with the real Android SDK + full internet
> access. Paste this alongside `VEPLA_Foundation.md`, `ARCHITECTURE.md`, and
> `GROWTH_SAAS.md` as project context. Three sessions in now: session 1 (chat
> sandbox, no Android SDK) built the Gate-2 pipeline blind. Session 2 (Claude
> Code, real SDK at `/mnt/DEV/Android/Sdk`, real emulator) compiled it,
> fixed what broke, and smoke-tested the app end to end on a real emulator.
> Session 3 added a cable-free update pipeline (GitHub Releases + in-app
> checker) and fixed `veh.modev.be` (the deployed webclient CDN), which was
> serving raw unbuilt source. **The car browser has not actually been tested
> against it yet** (only curl-verified), that's the very next thing to do.

## Cable-free update pipeline (session 3)
No Play Store, so no silent auto-update, Android always requires one install
tap for a sideloaded app. What this gets you instead: publish from anywhere
with `gh release create build-<N> <apk>#vehplayer-debug.apk --repo
mounssif/vehplayer` (tag must be `build-<versionCode>`, asset must be named
exactly `vehplayer-debug.apk`, `UpdateChecker.kt` parses both), and the app
finds it over the network on next launch, no USB/phone<->laptop transfer.
Build with `./gradlew assembleDebug -PvehplayerVersionCode=N
-PvehplayerVersionName=...` first, bump N each time. Verified end to end on
the emulator this session: banner appeared, tap opened Chrome to the correct
`github.com/.../releases/download/...` URL. `android/app/debug.keystore` is
now committed and pinned in `signingConfigs` specifically so this works, a
build signed with a different (e.g. freshly-generated CI) keystore would
fail to install as an update over the existing app.

## veh.modev.be (fixed this session, but re-verify before trusting it)
It's a Cloudflare Workers **static-assets deployment** (name `vehplayer`,
account `757744f732aa0682e6bb0dda487ba082`), not Pages - `pages/projects`
comes back empty for this account, don't waste time looking there.
`veh.modev.be` is a Workers custom domain bound to it (DNS is an
auto-managed, read-only AAAA `100::` record, don't try to edit DNS
directly). It had been deployed once via the dashboard's drag-and-drop
flow with no build step, so it was serving raw `webclient/src/main.ts` as
literal TypeScript (confirmed broken by the user on both a phone browser
and the Tesla browser: blank page). Fixed by adding `webclient/wrangler.toml`
(`name = "vehplayer"`, `[assets] directory = "./dist"`) and running
`npm run build && npx wrangler deploy` from `webclient/` with
`CLOUDFLARE_API_TOKEN` + `CLOUDFLARE_ACCOUNT_ID` set. Re-deploy the same way
any time `webclient/` changes and should reach the car; there's no CI for
this yet, it's a manual step (deliberately, the user asked to avoid GitHub
Actions where avoidable and do things directly instead).
**Verified only via curl this session (correct content-type, correct hashed
asset refs) - never actually loaded in the Tesla or a real phone browser
post-fix.** `HttpAssetServer.kt`'s `/go` redirects to `https://veh.modev.be`
now (CDN-delivered webclient updates without a new APK). Confirm a real
`/go` -> Tesla -> connect round trip before assuming this works.

## Still not deployed
- **`cloud/` Worker** (KV namespace IDs still `REPLACE_ME`): the session-3
  API token had Workers Scripts:Edit by the end but not Workers KV
  Storage:Edit, so the namespaces still don't exist. Not urgent, Gate 5
  scope. If picking this up, same account, same token-scope pattern as
  above (ask for KV Storage:Edit specifically, Scripts:Edit alone won't
  cover it, confirmed by an empty-with-auth-error response from
  `storage/kv/namespaces` this session).
- A `cloudflared` quick tunnel (trycloudflare.com) was tried this session
  for live request capture from the Tesla and was unreliable in this
  environment (registered successfully but never actually routed traffic
  server-side, tested with 3 separate tunnel instances). Don't retry that
  approach without a reason to think it'll behave differently; if live
  request capture is needed again, prefer adding a small POST-on-load beacon
  to the actual webclient page reporting to a deployed cloud/ Worker route
  once that's unblocked, over an ad hoc tunnel.
- Note: the CF API token given this session was described as temporary and
  will be rotated by the user; don't assume `~/.config/vehplayer/cloudflare.env`
  on this machine still has a valid one in a future session, verify first.

## What's real and verified

- **android/**: `./gradlew assembleDebug` passes clean, zero warnings.
  Installed and ran on a real emulator (`Medium_Phone_API_36.1`, Android 16).
  Walked the full onboarding flow live: launch -> enable accessibility
  (confirmed bound via `dumpsys accessibility`, zero crashes) -> tap Start ->
  reachability ladder correctly falls through to VPN consent (no tier-1 IPv6
  on the emulator, expected) -> VPN consent granted -> correctly reports
  `VpnReachabilityService` as the known stub. `HttpAssetServer`'s `/go`
  endpoint verified over `adb forward`: real 301 redirect with a real minted
  token and a correctly URL-encoded `ws=` param, `index.html` serves from
  the copied webclient bundle. One real crash was found and fixed this way
  (below) that no amount of static review would have caught.
- **webclient/**: `npm install && npm run typecheck && npm run build` all
  pass. Real WebCodecs decode path, real AudioWorklet audio, real input
  channel, real adaptive-quality client logic, real WS client, real
  Annex-B/AVCC NALU conversion + SPS/PPS extraction, now including a real
  `avc1.PPCCLL` codec string derived from the SPS instead of a hardcoded
  guess. Fake desktop sender (`harness/fakeSender.mjs`) works.
- **cloud/**: `npm install && npm run typecheck` passes.
- **validate/latency-harness/measure_latency.py**: real, self-tested
  (`--selftest`: 116.7ms measured vs 120ms injected).

## Fixed this session (was broken or stubbed, now real)

- **Crash, found by actually running the app**: `AndroidManifest.xml`
  declared `android:theme="@android:style/Theme.Material.Light.NoActionBar"`,
  a platform theme, but `MainActivity` extends `AppCompatActivity`, which
  throws `IllegalStateException` at `setContentView`. Fixed to
  `@style/Theme.AppCompat.Light.NoActionBar`. This would have blocked
  100% of real-device testing had it shipped.
- `HttpAssetServer.kt`: `NanoHTTPD.encodeUri()` doesn't exist in nanohttpd
  2.3.1, real compile error, fixed with `java.net.URLEncoder`.
- `CaptureService.kt`: shadowed `flags` param, renamed to `videoFlags`.
- `app/build.gradle.kts`: added `copyWebclientDist` Gradle task (runs on
  `preBuild`), copies `webclient/dist/` into `assets/webclient/`. Verified
  it round-trips end to end (see above).
- `VehplayerAccessibilityService.kt`: MOVE events now extend the in-flight
  stroke live via `StrokeDescription.continueStroke()` (API 26+, minSdk is
  29) instead of buffering and replaying the whole gesture on UP. Segment
  duration derived from real wall-clock spacing between events, clamped
  8-100ms.
- `H264Encoder.kt`: `deviceSupportsIntraRefresh()` now does a real
  `MediaCodecList` / `CodecCapabilities.isFeatureSupported(FEATURE_IntraRefresh)`
  check instead of a hardcoded `true`.
- `MainActivity.kt`: real hotspot IP via `NetworkInterface` enumeration
  instead of the literal `<phone-hotspot-ip>` placeholder string.
- `CaptureService.kt` / `LocalMediaServer.kt`: wired the real
  "browser reports its viewport on first connect" flow (ARCHITECTURE.md
  §2). `LocalMediaServer` takes an `onHello` callback, fired from the
  `hello` control message; `CaptureService.resize()` tears down and
  recreates the VirtualDisplay + encoder at the reported
  `viewportW/H * dpr`, rounded to even dimensions. Boot-time capture still
  starts at a 1280x800 placeholder (no car has connected yet at that
  point), corrected automatically once the first `hello` arrives.
- Confirmed (not just written-to-agree) that `main.ts`'s `?token=`/`?ws=`
  query param reads match `HttpAssetServer.kt`'s `/go` redirect, live, over
  `adb forward`.

## Every open thread that's still genuinely open, by file

These need either a real car (Gate-1 spikes), a real Cloudflare account, or
a product/design decision, none of which this session could resolve alone.

**android/**
- `capture/H264Encoder.kt`: bitrate/intra-refresh-period constants are still
  ARCHITECTURE.md §2 *defaults*, not MEASURED against a real device.
- `capture/CaptureService.kt`: quality ladder only steps bitrate, not
  framerate/resolution yet (ARCHITECTURE.md §5's full ladder).
- `audio/PlaybackAudioCapture.kt`, `audio/AacEncoder.kt`: sampleRate/channel
  assumptions (48kHz stereo) unverified against a real device's audio output.
- `net/VpnReachabilityService.kt`: entirely unimplemented (tiers b/c),
  intentionally deferred until S1 spike data exists (confirmed live this
  session: the app correctly reports this as a stub rather than silently
  pretending to work).
- `MainActivity.kt`: no QR code for the setup URL (Foundation §3 flow
  implies a URL/code, not necessarily a QR, worth a product call).
- `server/PairingToken.kt`: expiry policy still a placeholder default,
  needs a real decision at Gate 2.

**webclient/**
- `mseFallback.ts`: the Annex-B -> fMP4 muxer (`muxToFmp4MediaSegment`) is
  unimplemented, needs a real H.264 stream from `android/`'s actual encoder
  to test against.
- `audioPlayer.ts`: assumes AAC-LC 48kHz stereo, unverified against
  `android/`'s real encoder output.
- `worklets/ringBufferProcessor.ts`: assumes already-decoded PCM in, worth
  reconfirming once real audio flows end to end.

**cloud/**
- `entitlements.ts`: `deviceId` auth is a placeholder, forgeable, do not
  treat Pro-gating as trustworthy until replaced with a real signed token
  (Gate 5).
- `wrangler.toml`: KV namespace IDs are `REPLACE_ME`, needs a real
  Cloudflare account (`wrangler kv namespace create`).

**validate/**
- `latency-harness/measure_latency.py`: no interactive ROI selection yet
  (numeric x,y,w,h only); no per-stage (encode/transport/decode) breakdown.

## Real hardware is the actual next blocker
Everything static-analyzable and emulator-testable is now done. What's left
needs one of: a real Model 3 (Gate-1 spikes S1-S4), a real Android phone
(H.264/AAC encoder output to test the webclient decode path and
`mseFallback`'s muxer against), or a real Cloudflare account (`cloud/`'s KV
namespaces). None of that is remotely doable.

## Also still open from before (Foundation TODOs, unchanged)
- Domain + trademark check (Foundation §1), including the VEPLA-name
  collision noted previously.

## Scope-creep tripwires (Working Agreement, Foundation §12)
- Any pull toward a second car brand, Fleet tier, or paid marketing before
  Gate 4 is complete.
- Any dependency that reintroduces Android Auto/CarPlay protocol code
  (Foundation §4 Lesson 1).
- Any code copied or closely modeled from Castla (GPL-3.0), architecture
  ideas only.
