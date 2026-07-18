# VEPLA - Architecture (v1)

> Companion to `VEPLA_Foundation.md` §6/§7. This is the technical single source of truth for the pipeline, the protocol, and the Gate-1 spike protocols. Evidence status tags: MEASURED (our car), REPORTED (other projects, forums), ASSUMED (verify at Gate 1).

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
- **Primary: WebCodecs.** `VideoDecoder` fed AnnexB-converted NALUs (or AVCC, decide with the framing in §4), `optimizeForLatency: true`. Output `VideoFrame` painted to canvas/WebGL. REPORTED working in current Tesla firmware (Castla). Feature-detect at runtime.
- **Fallback: MSE.** fMP4 (one moof/mdat per frame or small cluster), `SourceBuffer` in `'sequence'` mode, aggressive buffer trimming, playbackRate nudging to chase live edge. This is the TesAA-era path; it works everywhere video works but costs latency. Keep it dumb and reliable.
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

## 7. Reachability Layer (the RFC1918 problem)
- Constraint (REPORTED, re-confirmed Nov 2025): the Tesla browser refuses connections to RFC1918 IPv4 (10/8, 172.16/12, 192.168/16). Standard hotspot addressing is therefore unreachable.
- Candidate solutions, tested in this order at Gate 1:
  1. **IPv6 (ASSUMED, highest value):** give the phone's hotspot interface a global-scope IPv6 (delegated from cellular, or configured) and serve on it; DNS AAAA on go.vepla.app pointing to a stable ULA/GUA scheme is the open design question (per-device DNS is the hard part; a numeric-IPv6 URL QR code is the pragmatic v1 if DNS is awkward). If the browser block is IPv4-only, this eliminates VpnService entirely.
  2. **CGNAT via VpnService (ASSUMED):** VpnService assigns e.g. 100.100.x.x to the phone; DNS A record (or local DNS on the hotspot's DHCP) resolves go.vepla.app there. Not RFC1918, no squatting on real public space.
  3. **TeslAA mechanism (REPORTED working for years):** VpnService assigns an address from a range we control or coordinate; DNS points there; traffic never actually leaves the local link. Guaranteed floor.
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
