# vehplayer - Architecture

> Companion to `VEHPLAYER_Foundation.md` §6/§7. This is the technical single source of truth for the pipeline, the protocol, and the Gate-1 spike protocols. Evidence status tags: MEASURED (our car), REPORTED (other projects, forums), ASSUMED (verify at Gate 1).

## 1. System Overview

```
+--------------------- PHONE (Kotlin, native) ---------------------+
|                                                                  |
|  Capture             Encode              Serve                   |
|  MediaProjection --> MediaCodec H.264 --> Local HTTP+WS server   |
|  (main display or    (hw, low-latency)   (video/audio/input/     |
|   VirtualDisplay)                         control channels)      |
|                                                                  |
|  AudioPlaybackCapture --> AAC/PCM encode --^                     |
|                                                                  |
|  Input sink: AccessibilityService.dispatchGesture()              |
|              (Power Mode: Shizuku InputManager injection)        |
|                                                                  |
|  Reachability layer (ONE of, Gate 1 decides):                    |
|    a) IPv6 global-scope addr on hotspot iface (no VPN)           |
|    b) VpnService assigning CGNAT 100.64.0.0/10 addr              |
|    c) VpnService assigning public-style addr (TeslAA mechanism)  |
+------------------------------------------------------------------+
                 | phone hotspot Wi-Fi (car is a client)
                 v
+--------------------- CAR BROWSER (Chromium) ---------------------+
|  Web client (TypeScript), served from vepla.app, cached           |
|  WS connect --> auth token --> channels                           |
|  Video: WebCodecs VideoDecoder --> canvas (rAF-paced,            |
|         drop-to-latest queue)   [fallback: MSE fMP4]             |
|  Audio: AudioWorklet playback   [fallback: BT to car, no-op]     |
|  Input: pointer/touch events --> binary input channel            |
|  Control: stats, quality, thermal, reconnect                     |
+------------------------------------------------------------------+

Cloud (control plane only, never media):
  vepla.app  -> serves + OTA-updates the web client (CDN, cache-busted)
  api        -> entitlements/licensing, remote config per firmware,
                opt-in compatibility telemetry
```

Trust boundary note: the hotspot may have other clients. The WS server requires a short-lived pairing token (rendered as part of the /go URL or a 4-digit code on first connect). No open unauthenticated stream.

## 2. Video Pipeline

### Capture
- Two modes:
  - **Mirror mode:** MediaProjection of the main display. Universally available, phone screen stays on. v1 default.
  - **Virtual display mode:** apps launched onto a VirtualDisplay sized to the car screen (aspect-correct, e.g. 1200x800 region of the Tesla landscape browser viewport). Phone screen can sleep. Requires launching activities on a secondary display: needs Shizuku or system dispensation on most Android versions (ASSUMED, spike S3). Ships as Power Mode if Gate 1 confirms the constraint.
- Resolution follows the *car* viewport, not the phone: request the browser's reported canvas size on connect, configure the encoder to match. No client-side scaling of a portrait phone into a landscape car screen in v1 mirror mode without letterboxing (accept letterbox v1; virtual display solves it properly).

### Encode (MediaCodec)
- H.264 Baseline/Main, hardware encoder.
- Low-latency configuration: `KEY_LATENCY=1` where supported, realtime priority, `KEY_REPEAT_PREVIOUS_FRAME_AFTER` for static screens, repeat SPS/PPS before IDR (`PREPEND_HEADER_TO_SYNC_FRAMES`), short GOP (1-2 s) **or** intra-refresh (`KEY_INTRA_REFRESH_PERIOD`) to avoid large IDR bursts that stall TCP under Wi-Fi loss.
- Bitrate: CBR-ish 6-12 Mbps at 1080p, adaptive (see §5). Per-tier defaults: MCU2 720p30 @ 5 Mbps, MCU3 1080p60 @ 10 Mbps (ASSUMED tiers, calibrate at Gate 2).

### Decode + render (car)
- **The only path confirmed to work in Drive: WebCodecs to canvas.**
  `VideoDecoder` fed AnnexB-converted NALUs (or AVCC, decide with the
  framing in §4), `optimizeForLatency: true`. Output `VideoFrame` painted
  to **canvas**, not a `<video>` element. REPORTED, multiple independent
  sources: **Tesla's in-car browser suppresses `<video>` element playback
  while the car is in Drive** (`MARKET_AND_PRICING.md` §3), confirmed by a
  TeslaTap developer describing exactly this architecture: "the video
  component of the browser is not available, to workaround I convert the
  video to canvas images and use websocket to send the stream to the car
  browser and it render in the canvas using animationFrame." This is not
  an implementation detail, it's the entire reason a canvas-based decode
  path is viable in motion at all - promote this from "primary, with a
  fallback" to "the only path that matters for a nav/dashboard product",
  since in-motion is the only state where a driving-focused product needs
  to actually work.
  **MEASURED-FALSE, session 10 (docs/NEXT_SESSION.md). The suppression
  claim above does not hold.** `webclient/public/video-test.html` was
  deployed to `veh.modev.be/video-test` and run in the founder's real
  Model 3 while driving in Drive at highway speed (photographed at 77, 104,
  and 125 km/h on the center screen, gear indicator on D). Results:
  - **Row A, progressive MP4 in a plain `<video>`: PASS, smooth** in Drive.
    A bare `<video>` element renders and plays while driving. This is the
    exact element the claim said would be suppressed.
  - **Row B, native HLS: SKIP**, "no native HLS support reported (expected
    in Chromium)". This MEASURES the car's browser as a Chromium build
    without native HLS (consistent with Chromium 140; native HLS only
    landed in Chromium 142), so `<video src="...m3u8">` will not work on
    the car and HLS must go through hls.js/MSE.
  - **Row C, hls.js/MSE into a `<video>`: PLAYING** in Drive, but choppy,
    with heavy dropped-frame counts (e.g. 264/269, ~513/... over ~97 s) and
    repeated `hls.js error: mediaError / bufferSeekOverHole` in the log.
    So MSE playback is not suppressed either, but the hls.js path is
    unreliable/janky on this browser (buffer gap handling), which is the
    "laggy" the observers reported.
  The founder further reports the stream stays up across braking, shifting
  to Park and back to Drive, opening a door, and behind a Tesla
  software-update popup (dismiss it and playback continues), and that
  fullscreen video works while driving. Net: **the `<video>` element is not
  gear-gated at all.** WebCodecs-to-canvas stays the primary live-mirror
  path **for its latency and for the smoothness gap Row A/C shows (plain
  decode smooth, hls.js/MSE choppy), not because `<video>` is unusable in
  Drive.** `<video>`/MSE/HLS are now legitimate candidates for passive
  media in Drive as well as parked, with the caveat that the hls.js/MSE
  path needs buffer-tuning work before it is smooth (Row C is the evidence,
  a MediaMTX-repackaged fMP4 stream may behave better than the public
  bipbop test stream used here, verify).
- **`mseFallback.ts` reopened by the session-10 finding above.** The old
  reasoning here was that MSE renders through a real `<video>` element and
  would therefore be suppressed in Drive, so the path was deprioritized.
  That premise is now MEASURED-false (a plain `<video>` and an MSE `<video>`
  both played in Drive on the real car; see Row A/C above), so MSE/HLS is
  no longer disqualified for in-motion use. The unimplemented Annex-B to
  fMP4 muxer stays a real TODO rather than a shipped feature, but it should
  be reconsidered as a legitimate candidate path for passive/media playback
  (and pairs with the MediaMTX/HLS direction in
  `docs/MEDIAMTX_HLS_RESEARCH.md`), weighed on latency, smoothness (Row C
  was choppy), and effort, not ruled out on a suppression assumption that
  did not survive contact with the car.
- **Codec calibration, verify against a real published competitor
  rather than guess** (`COMPETITIVE_REASSESSMENT.md` §4.1): TeslaMirror
  (a real, 5.5-year-shipping competitor, per-firmware release notes)
  publishes: MCU3 → H.265 720p60 recommended (1080p60 H.264 or 1080p30
  H.265 as the ceiling), MCU2 → H.264 **540p30** recommended (notably
  lower than this doc's current 720p30 ASSUMED default below), and since
  Tesla software 2025.38.11, H.265 works on MCU2 too. Treat as a strong
  prior to verify at Gate 1, not as ground truth - but far better informed
  than the current guess. We currently ship H.264 only (no H.265), a real
  gap against this specific competitor worth a Gate-1-adjacent
  investigation, not a Gate-1 blocker.
- **Render policy:** decode queue max depth 1-2; if frames arrive faster than paint, drop to latest. Never accumulate delay to preserve smoothness; latency wins over smoothness for a driving UI.

## 3. Audio Pipeline
- Source: `AudioPlaybackCapture` (Android 10+; apps can opt out of capture, REPORTED edge case: some DRM apps produce silence, document it).
- Route A (default): **car Bluetooth**. Phone plays normally to the car over A2DP. Zero code, ~150-250 ms latency, rock solid. Nav prompts slightly late.
- Route B (Pro / toggle): capture -> AAC (or 16-bit PCM at low bitrates) -> WS audio channel -> AudioWorklet ring buffer. Target < 80 ms added latency. Must handle the car browser's autoplay policy: audio starts only after a user gesture on the page (the connect tap covers this).
- A/V sync: video is the master; audio buffer targets 40-60 ms and resamples/skips to stay inside the window. Perfect lip-sync is a non-goal (this is nav + music, not cinema).

## 4. Wire Protocol (draft, sender-agnostic by design)
- One WebSocket, binary frames, tiny fixed header:

```
byte 0      channel   (0x01 video, 0x02 audio, 0x03 input, 0x04 control)
byte 1      flags     (video: bit0 = keyframe/IDR, bit1 = config (SPS/PPS))
bytes 2-9   u64 timestamp_us (sender monotonic)
bytes 10..  payload
```

- Video payload: raw AnnexB access unit (WebCodecs path repackages to AVCC client-side if needed).
- Input payload (car -> phone): compact binary `{type: down|move|up|scroll, pointer_id, x_norm, y_norm}` with coordinates normalized to the video frame, so encoder resolution changes never break touch mapping.
- Control channel: JSON (low rate): hello/auth, viewport size, stats (fps, decode time, ws bufferedAmount mirror), quality change requests, thermal notices, ping/pong RTT.
- Versioned with a single `hello.version` int. The iOS sender and the desktop test-harness sender speak the same protocol from day one.

## 5. Adaptive Quality & Congestion
- Sender monitors WS `bufferedAmount` growth + control-channel RTT.
- Ladder: drop framerate first (60->30->24), then bitrate steps, then resolution tier. Recover in the same order, reversed, with hysteresis.
- Wi-Fi loss bursts: intra-refresh means recovery does not require a huge IDR; if the socket stalls > N ms, flush the encoder queue and resume from a recovery point rather than pushing stale frames.
- Thermal: subscribe to thermal status; step the ladder down proactively and surface a quiet notice in the car UI. Summer-dashboard test is part of Gate 2.

## 6. Web Client Delivery & Resilience
- Served from vepla.app (CDN). Cache-busted per release; the phone's control channel announces the minimum client version and forces a reload if stale.
- Resilience against the TeslAA domain failure mode: the phone's local HTTP server *also* hosts the last-known-good web client bundle. Normal flow uses vepla.app (nice URL, OTA); if the domain is unreachable, the local URL path still works. Bundle ships inside the APK and updates alongside the app.
- The /go page doubles as the compatibility probe: it reports UA, WebCodecs/MSE support, viewport, and (opt-in) uploads the result to the compatibility matrix.
- **MEASURED (session 10, real Model 3): Reverse closes the browser.**
  The one interruption the founder found while testing: shifting into
  Reverse hands the center screen to the backup camera and closes the
  browser tab; coming back to Drive does not restore it on its own.
  Everything else (braking, Park and back, door open, software-update
  popup, fullscreen) leaves the stream running.
- **Built, session 10: one-tap resume, so reopening the browser after
  Reverse is "click and it works again", not a re-pair.** Three pieces:
  - `webclient/src/main.ts` treats arriving with both `?token=` and `?ws=`
    set (i.e. via a fresh `/go` redirect, or a bookmarked full link) as a
    resume and skips the tap-to-connect screen entirely, calling the same
    connect path immediately on load. Audio still needs a real user
    gesture (browser autoplay policy), so it's offered as a small
    non-blocking "tap for sound" affordance instead of gating video on it;
    the manual Connect button remains for a bare dev load with no params.
  - `PairingToken.touch()` (android) slides a still-valid token's expiry
    forward on every successful `hello`, so a token doesn't lapse purely
    from wall-clock time mid-drive as long as reconnects happen within the
    TTL window.
  - `CaptureService`'s `onHello` now calls `encoder?.requestKeyframe()` in
    addition to `resize()`, so a reconnecting client gets an IDR+config
    immediately instead of waiting for the next scheduled keyframe
    (up to the intra-refresh cycle), making the resume visibly instant
    rather than a multi-second black screen.
  - Not yet built: pushing the bookmark recommendation into onboarding
    copy (app UI, website, store copy) - **recommend bookmarking `/go`
    itself, not the expanded `index.html?token=...` URL**, since `/go`
    always mints a fresh valid redirect on every visit regardless of any
    prior token's state.

## 7. Reachability Layer (the RFC1918 problem)
- Constraint (REPORTED, re-confirmed Nov 2025): the Tesla browser refuses connections to RFC1918 IPv4 (10/8, 172.16/12, 192.168/16). Standard hotspot addressing is therefore unreachable.
- **MEASURED (session 7, Galaxy S23 / Android 16, real Model 3): the VpnService virtual-address mechanism (tiers 2/3 below) is dead on modern Android.** Android 14+-era BPF "ingress discard" hardening pins each VPN address to its tun interface as the only allowed ingress path (`dumpsys connectivity trafficcontroller` → `sIngressDiscardMap: [100.99.9.1]: tun0, tun0`); TCP/UDP from any tethered peer (the car over hotspot, a laptop over USB) is dropped in BPF before the TCP stack ever sees the SYN (zero SYN-RECV observed live). ICMP is not covered by the check, so ping "working" is a false success signal. No app-side workaround exists without root. The mechanism may still work on pre-hardening Android versions, so tier (c) stays implemented as a legacy-device fallback, but **tier 1 (IPv6) is the only viable path on current Android** - it puts a real address on the AP interface itself, which the strong-host/ingress-discard model permits. Two prerequisites verified missing on the session-7 test SIM: the carrier APN must actually be dual-stack (phone had zero IPv6 GUA on cellular that night; check the APN protocol setting), and Android's downstream IPv6 tethering must delegate a prefix to hotspot clients.
- Candidate solutions, tested in this order at Gate 1:
  1. **IPv6 (now the primary path, see MEASURED note above):** give the phone's hotspot interface a global-scope IPv6 (delegated from cellular, or configured) and serve on it; DNS AAAA on go.vepla.app pointing to a stable ULA/GUA scheme is the open design question (per-device DNS is the hard part; a numeric-IPv6 URL QR code is the pragmatic v1 if DNS is awkward). If the browser block is IPv4-only, this eliminates VpnService entirely.
  2. **CGNAT via VpnService (MEASURED broken on Android 16, see above):** VpnService assigns e.g. 100.100.x.x to the phone; DNS A record (or local DNS on the hotspot's DHCP) resolves go.vepla.app there. Not RFC1918, no squatting on real public space.
  3. **TeslAA mechanism (REPORTED working for years, now MEASURED broken on Android 16 - same BPF hardening; plausibly the reason TeslAA-lineage apps' virtual-IP modes degrade on new phones):** VpnService assigns an address from a range we control or coordinate; DNS points there; traffic never actually leaves the local link.
- Whatever wins: fully automated. The user sees one Android VPN consent dialog at most, once, with honest copy.
- DNS dependency rule: the resolved address must work without internet on the car side where possible (Tesla uses the hotspot's DHCP-provided DNS; the phone can run a tiny DNS responder for go.vepla.app, spike S1 verifies the car accepts it).

## 8. Gate-1 Spike Protocols (run in the founder's Model 3)
- **S1 Reachability matrix.** A probe APK + probe page. For each of {IPv6 GUA, IPv6 ULA, CGNAT-via-VPN, RFC1918 control, public-via-VPN}: can the browser load HTTP? open a WS? sustain 10 Mbps for 60 s? Repeat after the next firmware update lands. Output: the §7 decision + evidence table.
- **S2 Browser capability probe.** Static page on the working address: UA + Chromium version, WebCodecs H.264 decode test (canned bitstream), MSE fMP4 test, WebSocket throughput + RTT, canvas/rAF timing, touch event fidelity (multi-touch? pressure? event rate), page-visibility behavior when shifting to reverse and back, autoplay policy for AudioWorklet.
- **S3 Input injection verdict.** On 2-3 test phones (different vendors): AccessibilityService `dispatchGesture` latency and drag fidelity on the main display; behavior targeting a VirtualDisplay per Android version; Shizuku path as the comparison baseline. Output: the §6 fallback-ladder decision with numbers.
- **S4 Audio A/B.** BT A2DP prompt latency vs AudioWorklet route, measured with the harness mic. Output: default + toggle decision.
- Each spike produces a short markdown report in `validate/` with MEASURED numbers; Foundation §6 OPEN items are then rewritten as settled.

## 9. Repo Layout (mirrors the FREL convention)
- `docs/` Foundation, this file, later: PROTOCOL.md (frozen wire spec), COMPAT_MATRIX.md.
- `android/` the Kotlin app (Gradle project lands at Gate 2).
- `webclient/` the TypeScript car client + desktop test harness (talks the same protocol to a fake sender for development without a car).
- `validate/` spike reports, latency-harness scripts, in-car test checklists.
- `brand/` name/domain/trademark research now; visual identity at Gate 5.
