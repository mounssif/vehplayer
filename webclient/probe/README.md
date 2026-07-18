# S1 + S2 probe , how to run in the car

Two halves of the same spike (ARCHITECTURE.md §8), built this session, not yet run in the car:

1. **S1 (phone side)**: install `android/` (`./gradlew installDebug`, needs a local Android SDK,
   not available in this sandbox, run it from a real machine). Open the `vehplayer (dev)` app.
   It shows whether a global-scope IPv6 address exists on any active interface right now, tier
   (a) of the fallback ladder (Foundation §6b). If it reports `no-ipv6-gua`, tier (a) is out for
   that network and the real S1 spike still needs the VpnService tiers (b)/(c), not built yet
   (Gate 2 scope, this session only covers what tier (a) needs).
2. **S2 (car side)**: open `webclient/probe/index.html` in the car's browser. Two ways to serve it:
   - quick/local: `python3 -m http.server 8080` from `webclient/probe/`, phone and laptop on the
     same hotspot, point the car at `http://<laptop-ip>:8080/`.
   - against the phone directly once the WS server exists (Gate 2): the phone serves this same
     file at its local HTTP address, per ARCHITECTURE.md §6 ("the /go page doubles as the
     compatibility probe").
   Read every row: WebCodecs support, H.264 Baseline decode support (the exact profile the
   encoder will emit, ARCHITECTURE.md §2), MSE fallback support, AudioContext initial state,
   touch event fidelity, rAF jitter. Screenshot or copy the table into `validate/S2_browser_capability.md`.
3. The WebSocket round-trip box at the bottom of the S2 page is inert until `android/` has a real
   WS server (Gate 2). Wire it up then, it is already scaffolded to accept a `ws://` URL and
   report connect time + a ping reply.

Nothing here is a finished product screen. Both are throwaway instrumentation whose only job is
to turn ARCHITECTURE.md's REPORTED/ASSUMED tags into MEASURED ones, per the Working Agreement
(Foundation §12).
