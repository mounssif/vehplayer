# vehplayer - Next Session Kickoff

> For Claude Code, running locally with the real Android SDK + full internet
> access. Paste this alongside `VEHPLAYER_Foundation.md`, `ARCHITECTURE.md`,
> `GROWTH_SAAS.md`, `COMPETITIVE_REASSESSMENT.md` and `MARKET_AND_PRICING.md`
> as project context. Four sessions in now: session 1 (chat
> sandbox, no Android SDK) built the Gate-2 pipeline blind. Session 2 (Claude
> Code, real SDK at `/mnt/DEV/Android/Sdk`, real emulator) compiled it,
> fixed what broke, and smoke-tested the app end to end on a real emulator.
> Session 3 added a cable-free update pipeline (GitHub Releases + in-app
> checker) and fixed `veh.modev.be` (the deployed webclient CDN), which was
> serving raw unbuilt source. Session 4 fixed two real crashes on stream
> start, a versionCode publishing bug that made the update loop never
> terminate, and an `ERR_CONNECTION_REFUSED` bug where the local server died
> as soon as the user left the app screen (all three found from real-device
> evidence a user provided - a screen recording, then a "my hotspot was
> definitely on" pushback that turned out to be right and pointed at the
> real bug - not from emulator testing alone, the emulator had missed all
> three). Session 4 also added `CarDashboardActivity` (Phase 1 of a real
> car-optimized home screen, replacing raw phone mirroring). Session 5 found
> `veh.modev.be` had regressed back to serving raw source (a stale/never-
> completed deploy, not a code bug - re-deploying the already-correct
> `dist/` fixed it) and built Phase 3's Navigate page: a real embedded
> Mapbox map with search + routing, replacing the plain launcher-intent
> tile. Session 6 built out the rest of the dashboard for real - a custom
> car keyboard + live destination suggestions, real Now Playing
> (MediaSessionManager), a real Messages overview and Phone tab (call log +
> contacts), a Navigate recenter button - and fixed a real connectivity bug:
> tier (a) (IPv6 GUA) was being found but then silently ignored when
> building the shown connection URL, and tier (c) (VpnService non-RFC1918
> address assignment, previously just a TODO stub) is now implemented,
> evidence-backed against a real shipping competitor (see that section).
> **None of tier (a)/(c) has been confirmed against a real Tesla** - only
> verified as far as the emulator can go (address assignment, URL building,
> the app-level flow), the actual "can a second real device on the phone's
> hotspot reach that address" question needs real hardware. **The car
> browser has still not actually been tested against veh.modev.be** either
> (only curl-verified) - both are still the next real-hardware things to do.
> Session 6 also absorbed a real competitive/market research pass
> (`docs/COMPETITIVE_REASSESSMENT.md`, `docs/MARKET_AND_PRICING.md`) and
> restructured the docs/business layer around it: retired the "VEPLA" name
> for real (not just a working codename), replaced the phantom-"v1"
> `VEPLA_Foundation.md` with a complete `VEHPLAYER_Foundation.md`, rewrote
> `GROWTH_SAAS.md`'s pricing and moat reasoning against real competitor
> install numbers instead of assumptions, and added `CLAUDE.md`,
> `brand.json`, a `Makefile`, and a `legal/` directory (privacy policy,
> terms, processing register, trademark note - all drafts, all flagged
> `[MENS]`/needs-real-legal-review, not published-ready).
> Session 7 picked up three of the non-hardware items this doc had left
> open: fixed `MainActivity.localIpAddress()`'s known latent gap (now
> prefers the hotspot AP interface over a simultaneously-up cellular one),
> implemented the full ARCHITECTURE.md §5 adaptive-quality ladder
> (framerate then bitrate then resolution, with hysteresis - previously
> bitrate-only), and built the generic AppWidgetHost "pin any widget" tile.
> The widget tile surfaced a real, non-obvious platform bug along the way
> (`ACTION_APPWIDGET_BIND` returns `RESULT_CANCELED` on this Android 16
> build even when the user approves and the bind genuinely succeeds) - see
> that section for the fix and how it was isolated from a second, unrelated
> failure (this emulator's Google Play services stub can't render any
> Google-widget content at all, not an app bug).
> Session 8 (after the real-Tesla session below): all WebRTC pre-car
> groundwork (research, probe page, phone STUN responder), plus a
> feedback-driven rework moving pinned widgets into hero slides - see
> the session 8 section.
> Session 9: second real in-car probe run reproduced every session-8
> WebRTC PASS, and exposed why THE test keeps failing: subnet mismatch
> (car on 192.168.93.x, probe targeting 10.118.219.223). Fixes on all
> three sides shipped - see the session 9 section.

## Session 8: WebRTC probe built + widget slides rework (real user feedback round)

Two tracks: the WebRTC direction from session 7's wrap-up got its full
pre-car groundwork (research pass, probe page, phone-side STUN responder,
all verified as far as a desk can verify), and a real-phone feedback round
from the founder drove a rework of the widget/dashboard UX.

### WebRTC research pass (backgrounded agent, all REPORTED unless tagged)

- **WebRTC is available and functional in the Tesla browser - HIGH
  confidence.** SideDisplay (sidedisplay.co, a shipping Mac/Windows→Tesla
  extended-monitor product) explicitly runs "over WebRTC through the
  built-in browser" at ~100ms latency over a laptop hotspot. TMC thread
  151891 (May 2019, first Chromium firmware): WebRTC video decode worked
  in videoconferencing sites; browser had no audio playback and no
  camera/mic then. TeslaMirror's Play listing says its streaming "will be
  changed from MJPEG to WebRTC & H.264" (planned, not confirmed shipped).
- **Tesla's RFC1918 block is at the TCP-connect/packet layer, not URL
  filtering**: teslamotors/fleet-telemetry issue #423 shows a public
  hostname resolving to 192.168.0.100 timing out at `dial tcp` - so
  hostname tricks don't help, and WSS to a private IP dies the same way.
  A tesla-carplay GitHub project routes via 240.3.3.x (class E) with
  iptables; SideDisplay's FAQ states the 10/8, 172.16/12, 192.168/16
  block outright. Whether the block also covers **UDP** (ICE) is publicly
  untested - that is exactly what the probe below settles.
- **mDNS ICE obfuscation** (Chrome 76+, on regardless of http/https
  origin): SDP-only - host candidates read `<uuid>.local`, but actual
  connectivity checks go out from real sockets, so the phone side (which
  will publish real, un-obfuscated candidates) discovers the car via
  peer-reflexive candidates and ICE completes without anyone resolving
  `.local` names. Verified live this session that desktop Chrome 147
  obfuscates exactly this way (probe page output).
- **Correction to the research agent's own conclusion, from session 7's
  MEASURED data**: it recommended advertising the tier (c) CGNAT VPN
  address in ICE. That is wrong on modern Android - the BPF
  ingress-discard drops **UDP too** (`sIngressDiscardMap` covers TCP/UDP
  both), so ICE to 100.99.9.1 dies exactly like HTTP did in the car.
  The phone's ICE candidates must use the **AP interface's own RFC1918
  address** (strong-host model permits it); the only open question is
  whether *Tesla's* side refuses to send UDP to RFC1918, hence the probe.
- **Highest-stakes unknown for a full WebRTC video track**: does Tesla's
  Drive-mode `<video>` suppression also kill a WebRTC remote track? Zero
  public evidence. If yes, the endgame is data-channel transport feeding
  the existing WebCodecs-to-canvas pipeline (swap WS for RTCDataChannel,
  keep everything else) - which sidesteps `<video>` entirely and is the
  architecture-conservative option anyway.

### WebRTC probe: built and desk-verified, needs one car visit + a deploy

- **`webclient/public/probe-webrtc.html`** (vite copies it into `dist/`,
  so it ships with the normal webclient deploy): huge photographable
  PASS/FAIL rows - WebRTC API, ICE gathering + mDNS check, in-page
  loopback data channel, UDP to public STUN (control), **UDP to the phone
  via `stun:<phone-ip>:3478` (THE test)**, HTTP fetch to the phone
  (editable port - remember the 8081 fallback state), WS to tcp/8787.
  Detects https and marks the fetch/WS rows SKIP with a "reload via
  http://" warning (mixed content would block them regardless of Tesla
  policy; the WebRTC rows are immune to mixed content).
- **`ProbeStunServer.kt`** (new, `net/`): minimal RFC 5389 Binding
  responder on udp/3478, started/stopped with `CaptureService` alongside
  HttpAssetServer, deliberately wildcard-bound (packets to the AP
  interface's own address are delivered under strong-host; this is NOT
  the ingress-discarded VPN-address path). **MEASURED on the emulator**:
  full capture flow up, python STUN client through an emulator UDP
  redirect got a correct Binding Success with XOR-MAPPED-ADDRESS
  decoding to the right source address/port.
- **Probe page MEASURED in desktop Chrome 147** (playwright + the real
  emulator servers): API/ICE/loopback/public-STUN/fetch/WS rows all
  behave, mDNS obfuscation observed live. The phone-STUN row FAILed only
  in this desk rig because Chromium won't do STUN via loopback
  (127.0.0.1) - known desk-rig limitation, not a page/responder bug; in
  the car the STUN server sits on a real LAN address. Interpretation
  guide for the car: public-STUN PASS + phone-STUN FAIL = Tesla blocks
  UDP to RFC1918; both PASS = **WebRTC transport is GO**.
- **NOT DONE - blocks the car test: veh.modev.be deploy.** wrangler is
  unauthenticated in this environment (`wrangler login` never run, no
  CLOUDFLARE_API_TOKEN). Founder: `make deploy-web` (or
  `cd webclient && npm run build && npx wrangler deploy`), then in the
  car open `http://veh.modev.be/probe-webrtc.html` (http, not https, so
  the fetch/WS rows run), phone streaming first, type the phone's shown
  IP + port, RUN ALL, photograph.

### Widget slides rework (founder feedback with screenshots)

Feedback was: a pinned WhatsApp widget in the small 4th tile was
unreadable; "pin a widget" should live in the hero slides with an
always-present empty placeholder slide (e.g. after the map); Google Maps
was missing from the widget picker on the real phone even though the
phone's own launcher offered it; and the Navigate slide should be
replaceable by a maps widget. Built:

- **Widgets are hero slides now**: `HeroPagerAdapter` is dynamic - Now
  Playing, Navigate, one full-hero slide per pinned widget
  (`WidgetSlideFragment`), and always a trailing "Pin a widget"
  placeholder slide (`PinWidgetSlideFragment`). The 4th tile is gone
  (tiles are back to Navigate/Phone/Messages). Multiple widgets pin as
  multiple slides. `PinnedWidgetHost` persists an ordered id list
  (legacy single-id pref migrates automatically). Each widget slide gets
  `updateAppWidgetSize()` with the real hero dimensions so providers
  render their large-cell layouts, and has its own unpin "x".
- **Custom widget picker** (`WidgetPickerOverlayView`, same overlay
  pattern as the others) replaces `ACTION_APPWIDGET_PICK`: built from
  `AppWidgetManager.installedProviders`, grouped by app, car-styled.
  Reason it exists: the system picker demonstrably omits providers - the
  founder's Galaxy S23 picker had no Google Maps entry, and this
  emulator's system picker (session 7) showed only Battery/At a
  Glance/Analog while `installedProviders` lists both Google Maps
  widgets ("Nearby Traffic", "Quickly find places nearby"), MEASURED
  both places. Bind consent still goes through
  `bindAppWidgetIdIfAllowed`/`ACTION_APPWIDGET_BIND` exactly as before
  (including session 7's dont-trust-resultCode lesson).
- **Real Maps widget content RENDERS on this emulator** - pinned "Nearby
  Traffic" showed Google Maps' real RemoteViews ("Maps needs your
  location" onboarding state) at full hero size. This softens session
  7's "GMS stub can't render any Google widget" finding: that failure
  was specific to those three providers' RemoteViews resources, not all
  Google widgets. Content rendering is now MEASURED working for at least
  one real-world widget.
- **Two real bugs found live on the way**:
  1. Page dots built from `onPageSelected` never rendered: ViewPager2
     fires that callback *during its own initial layout pass*, where the
     freshly-added dot views' requestLayout is silently dropped - they
     stay unmeasured at 0x0 (`dumpsys activity top` showed them dirty).
     Fixed by posting the dot rebuild out of the layout pass.
  2. You cannot reliably swipe PAST the Navigate page: the embedded
     Mapbox MapView consumes horizontal drags as map panning. The dot
     strip is therefore tappable now (28dp touch targets around 6dp
     dots) and is the guaranteed way to reach widget slides. Worth
     remembering for any future page added after Navigate.
- **Verified live end to end on the emulator**: pick (Maps) → bind →
  slide inserted before placeholder → real content renders → survives
  force-stop + relaunch (id-list persistence) → unpin reverts to
  placeholder. Screenshots in the session log.
- **Explicitly NOT built yet**: hiding/replacing the built-in Navigate
  slide with a maps widget (the founder's "replace the navigate slide"
  wish is only half-served by pinning a Maps widget as its own slide) -
  needs a small setting + adapter flag, deferred. Also note the founder
  reported the Navigate settings gear "gone" - it is still in the source
  and wired (`fragment_navigate_map.xml` navAppSettingsButton →
  `openNavAppPicker()`), so check on the real device whether it's a
  z-order/visibility issue rather than assuming it was removed.

### Smaller session 8 items

- **Update banner now polls**: `MainActivity` re-checks GitHub releases
  every 5 minutes while the screen is resumed (was: once in onCreate -
  the founder kept the app open across a release and never saw the
  banner until a force-close). Deduped by versionCode. Verified live on
  the emulator: banner appeared on resume without a restart.
- **"Tesla" removed from user-facing copy**: `activity_main.xml` tagline
  is now "A car dashboard for your vehicle"; `brand.json`
  tagline_primary updated + a tagline_note recording the founder call
  (same nominative-use-only rule as Android Auto/CarPlay - "Tesla" only
  in descriptive store/SEO comparison copy). Verified rendering live.
- **CarPlay/Android Auto question (founder asked, with Ford CarPlay
  photos)**: building CarPlay/AA apps or widgets is a hard no - it is
  exactly the protocol dependency Lesson 1 and the scope-creep tripwires
  forbid (template-locked categories, Apple entitlement/Google review
  gates, and the TeslAA death-by-one-validation-change precedent). Cars
  that have CarPlay/AA are a solved problem and not this product's
  market; vehplayer exists for browser-cars without them. The *visual
  style* of those photos (split view: map dominant + sidebar cards for
  next-turn and now-playing) is fair game as design inspiration for our
  own dashboard - logged as an open design direction, e.g. a combined
  "driving" hero layout.

### Still open after session 8

- ~~Founder: connect Workers Builds~~ - **DONE, live-verified same
  evening.** Founder connected the repo to the existing `vehplayer`
  worker (Settings → Build: root `/webclient`, build `npm ci && npm run
  build`, deploy `npx wrangler deploy`, own `vehplayer build token`);
  an empty-commit push triggered the first build and
  `https://veh.modev.be/probe-webrtc` went 404→live in about a minute,
  MEASURED by curl. Deploys are now automatic on every push to main -
  the sessions 3/5 stale-deploy failure class is closed. Notes: CF's
  static-asset html_handling strips `.html` (the canonical probe URL is
  `/probe-webrtc`, the `.html` form 307s there); manual
  `make deploy-web` remains as fallback. **One sub-item still open
  [MENS]**: founder was asked to set Branch control → "Builds for
  non-production branches" to Disabled (it was Enabled with the version
  command equal to a production deploy, so a stray branch push would
  deploy to production) - not yet confirmed done.
- ~~In-car probe run~~ - **RAN SAME NIGHT, REAL TESLA (photos in
  session log). Result: WebRTC works in the car; THE test is
  inconclusive only because the phone's real AP address was unknown.**
  MEASURED in the real car browser:
  - **Tesla browser = Chromium 140** (`Chrome/140.0.7339.207`, X11
    Linux x86_64 UA).
  - **WebRTC API PASS, ICE gathering PASS, in-page loopback data
    channel PASS (83-100ms)** - the full WebRTC stack functions in the
    car.
  - **ICE shows 1 real host candidate, 0 mDNS-obfuscated** - Tesla's
    Chromium does NOT do mDNS obfuscation (simplifies the phone side).
  - **UDP out to public STUN PASS (79-137ms), with the car's internet
    running over the phone's hotspot** - so UDP across the hotspot
    link itself works; Tesla's block did not stop UDP to a *public*
    destination routed via the phone.
  - **"UDP to PHONE" FAILed for both addresses tried, but neither was
    verified to be the phone**: `10.118.219.201` turned out to be the
    CAR's own IP (Tesla wifi-diagnose screen), `10.118.219.1` was a
    gateway guess - Samsung hotspots don't reliably sit at `.1`, and
    nothing on the phone's UI shows the AP address (Settings→Status
    shows the VPN's 100.99.9.1 while the VPN is up). So FAIL ≠ "Tesla
    blocks UDP to RFC1918" yet - wrong-IP is equally likely. Real
    founder time sink, root-caused to the missing diagnostics info.
  - Fixed the time sink immediately: **build-17 shows `hotspot <ip>`
    as a second line under the connection URL on the dashboard** (the
    AP interface's real RFC1918 address via `localIpAddress()`,
    regardless of which tier the ladder picked).
  - **Next step, no car needed: home hotspot test.** Phone streaming,
    laptop on the hotspot, `ip route` gives "default via X" = the
    phone's true AP IP, run the probe from the laptop against X. PASS
    there = our side + the IP are right, and one short car retest with
    that IP is the definitive GO/NO-GO. FAIL there = our side is
    broken (check `adb logcat -s ProbeStunServer` for "answered STUN
    binding" lines to see whether requests even arrive).
  - fetch/WS rows SKIPped over https as designed (zone still forces
    https; optional scoped rule documented above).
- **HTTPS redirect stays ON** (zone-level "Always Use HTTPS", verified
  live: http→301→https). Safest default for the product; the probe's
  decisive STUN/UDP rows are unaffected by https. Only if the fetch/WS
  rows should also run in the car: add a Cloudflare Configuration Rule
  skipping the https redirect for `veh.modev.be/probe*` - optional,
  scoped, not required for the GO/NO-GO answer.
- Founder: APN protocol check (IPv4/IPv6) on the data SIM for tier (a).
- In-app diagnostics screen (chosen tier, resolved address, "did
  anything connect" counters) - still not built.
- Tier (a) IPv6 end-to-end on the emulator - still not done.
- Navigate slide replace/hide setting; real-device check of the
  "missing" nav settings gear; the split-view driving layout direction.
- **Scan-to-probe QR shipped (build-18, founder idea)**: probe page
  accepts `?ip=&port=` and autoruns; tapping the dashboard's connection
  URL opens a full-screen QR encoding
  `https://veh.modev.be/probe-webrtc?ip=<hotspot-ip>&port=<port>` - any
  second device scans and the whole probe runs with zero typing.
  Verified end to end: QR rendered on the emulator dashboard, decoded
  back to the exact URL (opencv), page autofills + autoruns (headless
  Chrome). zxing:core dependency (encoder only, no camera code).
- **Next product step, same founder thread ("richting het product
  zelf")**: cloud pairing - phone registers `{hotspot ip, port}` as a
  tiny JSON blob under a short code with the cloud Worker (control
  plane only, no media, passes the architecture bar), the car just
  opens `veh.modev.be` and enters the short code from the dashboard
  (Teslas have no camera, so QR can't serve the car itself). This
  short-code rendezvous is the same mechanism WebRTC signaling needs
  anyway - build it once, it serves both.

## Session 9: second real in-car probe run - subnet mismatch found, THE test still open

Founder ran the full probe again in the real Tesla (photos in session
log, ~00:18), this time via the dashboard-shown values: probe at
`veh.modev.be/probe-webrtc` with `ip=10.118.219.223` (the app's shown
`hotspot` line, build-18) and `port=8081`. Founder's own read was
"still no connection via the hotspot IP" - the actual result is more
specific and more useful than that:

- **Re-confirmed MEASURED, second independent run**: Chromium 140 UA;
  WebRTC API PASS; ICE PASS with 1 real host candidate and **0
  mDNS-obfuscated**; loopback data channel PASS 82ms; UDP out to
  public STUN PASS 155ms (srflx via `94.109.177.82`). The whole
  session-8 result set reproduced.
- **THE test (UDP to phone) FAILed for a now-visible reason: subnet
  mismatch, MEASURED.** The car's own host candidate was
  `192.168.93.142`, but the probe targeted `10.118.219.223` - not on
  that /24 at all. Whatever else is true, that packet was never a
  valid hotspot-LAN test. So "Tesla blocks UDP to the phone" is STILL
  not established; the run was invalid the same way session 8's was,
  one layer deeper.
- **Two rival explanations, not yet settled**: (a) the app picked the
  wrong phone interface again - `localIpAddress()` ranked generic
  `wlan*` (usually the *client* radio) equal to AP-mode names, so a
  client-WiFi/other 10.x interface could win by enumeration order; or
  (b) **the car was not on the phone's hotspot at all** but on another
  WiFi (home network reachable from the driveway; the test happened at
  home). Evidence FOR (b): session 8's Tesla wifi-diagnose showed the
  car at `10.118.219.201` - the SAME /24 the app now reports as
  `hotspot` - which fits `10.118.219.x` really being the Samsung AP
  subnet and `192.168.93.x` being some other network the car
  auto-joined tonight. Unresolved; the fixes below make the next run
  self-diagnosing either way.

### Fixes shipped this session (all three sides)

- **`MainActivity.localIpAddress()` rank rewrite**: strict scoring
  replaces the two-bucket filter - AP-mode names (`ap*`, `swlan*`,
  `softap*`) score 100, generic `wlan*` with a gateway-style `.1`/
  `.129` last octet 80, other `wlan*` 60, any other RFC1918 40;
  point-to-point (VPN tun) interfaces are excluded entirely. Closes
  scenario (a) as far as ranking can.
- **Dashboard now shows the interface name**: `hotspot <ip> (<iface>)`.
  A car-screen photo alone now distinguishes "real AP address"
  (`swlan0`/`ap0`) from "suspect client-radio pick" (`wlan0`).
- **Probe page subnet sanity check + gateway auto-fallback**: the page
  records its own host-candidate IPs during ICE gathering; if the
  entered phone IP isn't on any of the device's own /24s, a loud
  warning names both explanations (wrong WiFi vs wrong interface).
  New `stunGw` row: after a stunPhone FAIL it auto-tries
  `<own-subnet>.1` and `.129` via STUN; a PASS there prints the
  correct IP to retype. Derivation logic unit-verified in node against
  the exact candidate lines from tonight's photos.

- **Dashboard "connect info" chip (build-20, founder ask)**: the URL +
  hotspot lines were creeping toward the tiles, so the header now shows
  one compact chip; tapping opens the QR overlay extended with the /go
  URL and `hotspot <ip> (<iface>)`. QR shrunk 300dp→200dp (caption fell
  off a landscape screen at 300dp). Verified live on the emulator:
  chip, overlay content, tap-dismiss. Bonus evidence from the founder's
  build-19 photo: the line read `hotspot 10.184.170.12 (rmnet_data0)` -
  cellular, i.e. the hotspot was off at that moment and the iface
  suffix made that visible exactly as intended.

### Session 9, later same night: THIRD in-car run - the mismatch is the CAR's own NAT, MEASURED

Founder re-ran with build-19/20 (photos ~01:04-01:08). This run finally
pins the mystery, because all three vantage points were photographed at
once:

- **App side**: dashboard showed `hotspot 10.118.219.223 (ap_br_swlan0)`
  - the interface-ranking fix works, `ap_br_swlan0` is Samsung's real AP
  bridge. The entered probe IP was therefore RIGHT this time.
- **Phone side (decisive new evidence)**: Samsung's hotspot
  connected-devices screen showed **Tesla_Model_3 connected at
  `10.118.219.201`**, 2 minutes connected, shared data counter rising -
  during the exact test window. The car WAS on the hotspot. Explanation
  (a) dead.
- **Car side**: the probe still reported the browser's own host
  candidate as `192.168.93.142` and stunPhone FAILed; the new stunGw row
  auto-tried `192.168.93.1`/`.129` - both FAIL (expected in hindsight).

Conclusion, MEASURED: **Tesla's browser sits behind the car's internal
NAT.** Its network interface lives on a car-internal subnet
(`192.168.93.x`) even while the car's WiFi is associated to the hotspot
as `10.118.219.201`. Host candidates from the car are therefore
unroutable garbage for the phone, and subnet comparison against the
browser's own IP says nothing about which WiFi the car is on. WebRTC
can still work through this (car->phone direction initiates, phone
answers via prflx) IF car->AP UDP passes - which is exactly what
stunPhone tests and what still FAILs. Remaining suspects, now narrowed
to two: the car's NAT not forwarding to RFC1918 WiFi-side destinations,
or the packets arriving and something phone-side eating them
(`adb logcat -s ProbeStunServer` during the next run answers that
between-the-two).

Probe page updated: the mismatch note now names explanation (c)
(car-internal NAT, entered IP still right, gateway scan meaningless
under it) and prints the single decisive next test: **type
`http://10.118.219.223:8081/go` straight into the car's address bar**
(top-level navigation, no mixed-content rules apply). That is
simultaneously the first-ever real /go round-trip attempt at the
correct address - the project's actual blocker.

### Founder-feedback fixes, same night (build-21)

- **Update button no-ops while streaming** (founder: "werkt wel na
  gedwongen stoppen, niet als actief"): consistent with the
  platform/OEM anti-scam block on installer sheets during an active
  MediaProjection share. `startUpdate` now stops CaptureService + VPN
  first, then downloads - the update replaces the process anyway.
  Root-cause note: NOT the VPN (it routes only its own /32 and
  excludes the app).
- **Start button was amber while step 1 was still pending** (founder:
  confusing) - emphasis now follows setup state: step 1 amber until
  the accessibility service is on, then flips to "Input control
  enabled ✓" (neutral) + Start amber. Verified live in both states on
  the emulator.
- **Connect-info overlay scrollable** (founder ask): card wrapped in a
  ScrollView with margins, never clips on small/portrait screens.

### Session 9, ~01:25: the decisive /go attempt ran - result ERR_CONNECTION_REFUSED, verdict OPEN between two readings

Founder typed `http://10.118.219.223:8081/go` into the car's address
bar (photo): **"10.118.219.223 heeft de verbinding geweigerd" /
ERR_CONNECTION_REFUSED - a fast active refusal, not a timeout.** A
refusal means a TCP RST came back from *something*. Two readings, not
yet separable:

1. **Car-side active block**: Tesla's browser stack/proxy refuses
   RFC1918 destinations outright (fits the REPORTED historical
   private-range block; fits the same-minute probe where UDP to the
   same address died *silently* while TCP got an instant RST).
2. **The packet reached the phone and nothing was listening**: the
   port-8081 server may genuinely have been down at 01:25 - the
   build-21 update flow *stops the stream* to install, and the founder
   updated around that window. A phone with no listener sends exactly
   this RST. Under this reading the transport WORKS and nine sessions
   of blocker fall.

The same-run probe rerun (photos) reproduced: all WebRTC PASSes, THE
test FAIL silent, gateway auto-scan `.1/.129` FAIL (meaningless under
the car-internal-NAT finding, as the page itself now says).

**Fix shipped so the next attempt is unambiguous (build-22): zero-adb
reachability counters.** HttpAssetServer counts every served request,
ProbeStunServer counts answered bindings, and the connect-info overlay
shows both live ("reached from network: HTTP Nx / STUN Nx - reopen
after a car attempt"). Protocol: confirm streaming + port on the chip,
attempt /go in the car, reopen the chip on the phone. HTTP counter
moved = reading 2 (transport works, everything is suddenly close).
Still 0 = reading 1 (car refuses RFC1918 TCP; combined with silent UDP
drop that closes the direct-RFC1918 route and promotes tier (a) IPv6
GUA to plan A - founder APN IPv4/IPv6 check becomes the gating
question).

### Session 9, ~01:44: counter stayed 0 in the car - reframes the suspect toward the PHONE/AP firewall, not Tesla

Founder retested /go in the car on the correct AP IP (10.118.219.223)
AND port, plus 8080 for safety. Result photographed: the connect-info
counter stayed **HTTP 0x / STUN 0x**. The counter mechanism is verified
working (emulator hit it 4x via adb-forwarded curls this session), so
0 in the car means **the phone's HTTP server never saw the connection**
- the ERR_CONNECTION_REFUSED RST was synthesized *before* any packet
reached the app.

Combined with the earlier failure that never got its due weight: the
**home hotspot test also failed** (a laptop/second phone ON the hotspot
could not reach the phone's AP IP either - session start note "thuistest
zonder succes, nog steeds geen verbinding via hotspot ip"). A *non-Tesla*
client on the same hotspot failing to reach the phone's own AP address
points at the **Samsung/Android tethering firewall dropping inbound
traffic to the phone's own hotspot IP**, a known Android behavior
(tethering iptables permit client->internet and DHCP/DNS to the AP host,
but often DROP client->AP-host service ports). If that is the cause it
is PHONE-SIDE and Tesla is not the blocker at all - which also fits every
prior symptom (UDP to public STUN works = client->internet forwarded;
TCP to veh.modev.be works = same; but anything to 10.118.219.223 itself
dies).

**This is now the single most important thing to isolate, and it needs
no car.** The new /diag page + counters do it: put a laptop or second
phone on the hotspot and open `http://<phone-ap-ip>:<port>/diag`.
- Counter moves / page loads -> the AP does NOT block client->AP-host,
  so the car's failure is Tesla-specific (RFC1918 block) -> IPv6 (tier a)
  becomes plan A, founder APN check gates it.
- Counter still 0 / page won't load -> phone/AP firewall confirmed,
  Tesla exonerated. Fix is phone-side: research Android tethering
  iptables (the `iptables -t filter -L` / `ip rule` on the AP host), a
  bind-address workaround, or driving the connection the other direction
  (phone dials car - but car has no listener, so this needs the WebRTC
  offer/answer rendezvous the docs already plan).

### Tooling shipped this session for the above (build-23)

- **Phone-served `/diag` page** (`webclient/public/diag.html`, bundled
  into assets, served by HttpAssetServer): same-origin http, so its
  fetch / WS / XHR / STUN rows all RUN (no mixed-content SKIP, unlike the
  cloud https probe). Loading it at all proves TCP; it then measures
  HTTP/WS/UDP to the phone and POSTs a JSON report to `/diag-report`.
  Also captures read-only Chrome-140 metrics: Network Information API
  (wifi/cellular, rtt, downlink), userAgentData high-entropy (model,
  platform version, arch), getStats srflx detail, viewport/cpu/mem.
- **New HttpAssetServer endpoints**: `/ping` (200 pong, cheap timing
  target), `/diag-config` (JSON {wsPort, httpPort}), `POST /diag-report`
  (stores the last report), `/diag` alias -> diag.html. All CORS-open.
- **Cloud probe-webrtc.html** also now logs the same read-only metric
  block on every run (Network Info, uaData, getStats).
- **Connect-info overlay** now shows the last in-car /diag summary
  (self/ping/ws/stun/rtc + Chromium version) so the founder reads the
  car result on the phone with no photo. The in-car test URL the app
  hands out is now `http://<ap-ip>:<port>/diag` (was the cloud probe).
- All endpoints + counters + overlay summary MEASURED end to end on the
  emulator this session (ping 200, config JSON, report POST -> ok,
  summary rendered).

### Firewall-bypass path prepared (build-24): LocalOnlyHotspot toggle

After a design discussion (founder proposed Bluetooth PAN, DNS tunneling,
Wi-Fi Direct, LocalOnlyHotspot), the one idea that survives the "the
Tesla is a locked Wi-Fi-only browser client" filter is **LocalOnlyHotspot**
- it is a normal joinable WPA2 AP on a different code path from Settings
tethering, so the carrier/OEM client-isolation firewall (our leading
suspect) is often absent, and the car can join it like any hotspot. The
others are dead at the car: BT-PAN (Tesla won't network its browser over
Bluetooth), DNS-tunnel (no JS DNS API in the browser), Wi-Fi Direct (no
P2P-join UI in the car). Honest caveat baked into the UI: LOH's IP is
still RFC1918, so it only helps if the block is the phone firewall, NOT
if it is Tesla's RFC1918 filter - the /diag test still decides which.

Built as a reversible toggle so it can be tested in the same sitting:
- `net/LocalOnlyHotspotController.kt`: wraps `startLocalOnlyHotspot`,
  surfaces SSID/passphrase, resolves the AP's 192.168.x address, stop().
- MainActivity "Firewall-bypass test" button: guards on the server being
  up (Start streaming first) and location services on, requests
  NEARBY_WIFI_DEVICES/FINE_LOCATION, then shows the SSID + password to
  type into the car and the exact `http://<ap>:<port>/diag` URL. Tap
  again to stop.
- Manifest: added NEARBY_WIFI_DEVICES.
- UI wiring MEASURED on the emulator (button, guard message); LOH itself
  needs the real phone (emulator has no Wi-Fi radio for it).

### One-sitting test plan for tomorrow (build-24)

Everything below is now in one build so it can be run in a single go:

A. **Normal hotspot, laptop /diag (isolates firewall vs Tesla):** phone
   Start streaming; note IP+port on the connect-info chip; from a laptop
   or 2nd phone ON the hotspot open `http://<ip>:<port>/diag`. Page loads
   / counter moves = not the firewall. Won't load / counter stays 0 =
   phone-side firewall.
B. **Normal hotspot, car /diag:** in the car open the same
   `http://<ip>:<port>/diag`. Compare with A: if a laptop can reach it
   but the car cannot, the block is Tesla-specific (RFC1918).
C. **LocalOnlyHotspot, car:** tap the firewall-bypass button, join the
   car to the shown SSID/password, open the shown /diag URL. If it loads
   here where the normal hotspot refused -> the SIM-hotspot firewall was
   the blocker and LOH is the product direction. If it still refuses ->
   the block follows RFC1918 regardless of code path -> IPv6 (tier a),
   APN check gates it.

Read the counters / the /diag verdict / the connect-info summary after
each - all three report without a photo.

## Session 9 RESOLVED THE BLOCKER CLASS: Tesla's RFC1918 block is real and covers UDP too; the phone/hotspot is clean

The A/B test ran (build-24, MEASURED, founder photos + console):

- **A - laptop on the normal hotspot -> `http://10.118.219.223:8081/diag`:
  FULL GO.** Every row PASS: self/TCP, /diag-config, /ping (52ms),
  XHR, **WebSocket connected (42ms)**, **UDP-to-phone STUN srflx (22ms) -
  "direct UDP car->phone WORKS"**, WebRTC loopback. Verdict "GO: TCP +
  HTTP + UDP all reach the phone." So the **Samsung/Android tethering
  firewall does NOT block client->AP-host** - a normal client on the
  hotspot reaches the phone on TCP, HTTP, WS and UDP. The phone-firewall
  hypothesis is DEAD, and the earlier "home test failed" was the
  mixed-content https cloud probe being misread, not a real block.
- **B - the same URL in the car: FAIL** (prior runs: /go TCP refused with
  counter 0; STUN-to-phone no srflx). A laptop reaches everything the car
  cannot.

**Conclusion, MEASURED and now settled:** the block is **Tesla-specific
and it is the RFC1918 filter, covering BOTH TCP and UDP.** The
previously "publicly untested" question - does Tesla's private-range
block also cover UDP/ICE - is answered: **YES.** Laptop UDP-to-phone
passed; car UDP-to-phone failed on the identical address. So ICE host
candidates on an RFC1918 address are dead in the car, exactly as HTTP was.

**Consequences:**
- **LocalOnlyHotspot (build-24) is now moot for the car**: its address is
  still 192.168.x (RFC1918), which Tesla blocks regardless of code path.
  It only ever helped the phone-firewall hypothesis, now disproven. (The
  founder didn't spot the button - no need, it's off the critical path.)
- **Plan A is tier (a): IPv6 GUA.** A public-range IPv6 address on the
  phone is NOT in the RFC1918 block, and the laptop test proves that once
  the car can *reach* the phone, the entire local transport (TCP/HTTP/WS/
  UDP/WebRTC) works. The gate is whether the SIM/APN hands out IPv6 -
  **the founder APN check is now THE critical-path question**, not a side
  note. If IPv6 is available: assign the phone's AP a GUA, advertise it in
  ICE + as the /go host, done. If not: the CGNAT (tier c) path is dead
  (Android ingress-discard) and the options narrow hard.

### Client-over-https ws:// bug fixed (build-25)

The founder also hit, loading the client from veh.modev.be (https):
`DOMException: The operation is insecure` at wsClient.connect. Root cause:
a client served over **https** can never open **ws://** to the phone
(browsers reject it as insecure). `/go` used to redirect to the https CDN
copy, so the mirror data channel could never connect from there. **Fixed:
`/go` now redirects to the phone's OWN http origin
(`http://<host>/index.html?...`), serving the bundled client same-origin
over http so ws:// is legal.** MEASURED on the emulator: /go -> local
http index, index + hashed JS asset both 200, ws param
`ws://<host>:8787`. This is also strictly more "data plane local". The
CDN stays the host for the probe/diag pages only.

**Still open, separate real bug (not yet fixed):** the audio AudioWorklet
is loaded as **untranspiled TypeScript** (`private ring: Float32Array` ->
"unexpected token: identifier"), so audio init throws in Firefox AND
would in the car's Chromium. It is caught (`.catch`) and audio is Route B
(Pro), so video mirror is unaffected, but it needs a real fix (vite isn't
transpiling the worklet loaded via `new URL('./x.ts', import.meta.url)` +
addModule; write it as plain .js or force transpile).

### LocalOnlyHotspot tested in the car - doubly dead (build-25)

The founder got LOH to start (second try, after the SIM hotspot was
torn down): SSID AndroidShare_8121. The car **refused to join it**:
"Geen verbinding met internet. Controleer de firewall en
internetverbinding." LocalOnlyHotspot has NO internet by design, and
**Tesla rejects a Wi-Fi network that has no internet** (connectivity
check fails). So LOH is dead on two counts for the car: the no-internet
rejection, and its 192.168.x address is still RFC1918 (which Tesla blocks
even if it did join). Off the table, confirmed by test not assumption.

Also noted from the photos: the phone is **dual-SIM (Proximus + Orange,
"Orange B")**. The IPv6/APN question must target whichever SIM feeds the
hotspot's data.

### IPv6 viability reporter added (build-26)

Tier (a) is now the only live plan, gated entirely on "does the SIM/APN
hand out IPv6, and does the hotspot expose it." `net/Ipv6Report.kt`
enumerates global-unicast (2000::/3) IPv6 per interface, tags cellular
(rmnet/ccmni) vs hotspot (ap/swlan/wlan), and both MainActivity and the
connect-info overlay show a one-line summary:
- "IPv6: none (SIM/APN likely IPv4-only -> tier (a) not available)", or
- "IPv6 global: <iface> <addr> ... [SIM has IPv6] [hotspot exposes IPv6]".
Verified rendering on the emulator (shows "none", as expected - no
cellular IPv6 there). **This is the founder's APN check made
zero-effort: open the app on mobile data and read the line.** [SIM has
IPv6] present -> tier (a) is worth building; absent -> IPv6 is out and
the direct-connection options are effectively exhausted (escalate to the
WebRTC-signaling rendezvous / rethink).

### Next run protocol (either home hotspot or car, 2 minutes)

1. Update app (banner), Start, read the dashboard line: `hotspot <ip>
   (<iface>)`. If iface is `swlan0`/`ap0`, that IP is trustworthy.
2. In the car: Tesla WiFi settings, confirm the connected SSID **is
   the phone's hotspot** (forget the home network if it keeps
   grabbing the car), then open the probe with that IP.
3. Read three things off the result: the stunPhone row, the mismatch
   warning (should be absent now), and the stunGw row. stunPhone PASS
   = GO for WebRTC transport. stunGw PASS with a different IP = app
   detection still wrong but the correct IP is on screen - retype and
   rerun. Both FAIL with no mismatch warning = first genuine evidence
   of a UDP/client-isolation block (check `adb logcat -s
   ProbeStunServer` for "answered STUN binding" to see if requests
   arrive at all).

## Session 7, part 2: FIRST REAL TESLA TEST - tier (c) is dead on modern Android, and we know exactly why

The single most important finding of the whole project so far, MEASURED
end to end with the founder parked in the real Model 3, laptop
USB-tethered to the real phone (Galaxy S23, SM-S911B, Android 16), live
adb the whole time:

1. **The real Tesla connected to the real hotspot and loaded the app's
   shown URL (`http://100.99.9.1:8081/go`) → `ERR_CONNECTION_TIMED_OUT`.**
   First-ever real-car attempt, session 6's tier (c) implementation, build
   13.
2. **Isolated it off the car entirely**: a laptop curl to the same address
   over USB tethering timed out identically, while curl to the phone's
   real tether-interface address (`10.162.212.228:8080`) got a normal HTTP
   response, and ping to `100.99.9.1` got normal replies. So: not
   Tesla-specific, not WiFi-specific, not a dead server - TCP specifically,
   to the VPN address specifically, from any external device.
3. **Found a real second bug on the way**: our own server sockets carried
   the VPN fwmark (a VpnService applies to its owning app too by default),
   which routes their reply packets into the VPN routing table - confirmed
   live: table 1057 contained only `100.99.9.1/32 dev tun0`, no route back
   to any tether subnet, so even if a SYN got through, the SYN-ACK died.
   Fixed with `addDisallowedApplication(packageName)` in
   `VpnReachabilityService` (verified live via `ip rule`: our uid 10310 now
   excluded from the VPN uid ranges). **This fix is real and correct but
   was not sufficient** - connections still timed out after it.
4. **Root cause, with direct evidence**: zero `SYN-RECV` entries on the
   phone during a live connection attempt (SYN never reaches the TCP
   stack), and `dumpsys connectivity trafficcontroller` shows
   `sIngressDiscardMap: [/::ffff:100.99.9.1]: 58(tun0), 58(tun0)` -
   Android's BPF ingress-discard hardening (anti-spoofing for VPN
   addresses, Android 14+ era) drops any TCP/UDP packet addressed to a VPN
   address that arrives on any interface other than the tun itself. ICMP
   is not covered by the check, which is exactly why ping "worked" and
   made the routing look fine - **ping is a false success signal for this
   entire class of problem, never trust it again as reachability
   evidence**. `tether_offload_disabled` was already 1, ruling out the
   Qualcomm IPA hardware-offload theory that was briefly the lead suspect.
5. **Consequences**: the whole TeslAA/TeslaMirror-style virtual-IP
   mechanism - the industry-standard trick this project's tier (c) copied,
   evidence-backed from a shipping competitor - has been closed by Google
   on modern Android. No app-side workaround without root. Tier (c) stays
   in the codebase as a legacy-device (pre-hardening Android) fallback,
   with its doc comment rewritten to state all of this. The emulator
   "verification" in session 6 was a false positive - a single virtual
   device can't exercise the cross-interface ingress path the BPF check
   guards. **Tier (a) IPv6 is now the only viable path on current
   Android**, and it should genuinely survive this hardening: the address
   lives on the AP interface itself, which the strong-host model permits.
6. **Tier (a) blocker found the same night**: the phone had zero IPv6 GUA
   on cellular (checked `ip -6 addr` with WiFi off, cellular active,
   dual SIM Orange B + Proximus). Either the active data SIM's carrier
   doesn't do mobile IPv6 or the APN protocol is set IPv4-only. **Next
   concrete step: founder checks APN protocol → IPv4/IPv6 on the data
   SIM (and/or switches data to the other SIM), then re-check
   `adb shell ip -6 addr` for a `2xxx:` address, then re-test in the
   car.** Proximus mobile reportedly supports IPv6 (REPORTED, unverified);
   Orange BE historically lagged.
7. Also for the record: TeslaMirror's `TSL6.com` resolves to Cloudflare
   (their CDN webclient, same pattern as our `veh.modev.be`), and their
   public FAQ shows no explicit Android-14+ note - whether their
   virtual-IP mode still works on current Android is unknown; their FAQ's
   heavy "toggle hotspot off and on" troubleshooting suggests flakiness.
   If they ARE broken on modern phones, tier (a) IPv6 becomes a genuine
   competitive differentiator, not just a workaround.

Also from this pass: the emulator-session earlier in session 7 hit a
stale-`HttpAssetServer` port fallback on the real phone too (8080 held by
a previous session's process → new session on 8081, both listening,
`/go` URL correctly showing 8081) - worked as designed, but worth knowing
when reading `netstat` output during debugging: two listeners is normal
after a restart, the shown URL's port is the live one.

### Session 7 wrap-up: the remote-research plan (written as the car/laptop battery died)

Full diagnostic dumps from the real phone captured before disconnect
(connectivity, trafficcontroller incl. the ingress-discard map, tethering,
telephony, carrier_config, getprop, all routing tables/rules, softap
state) - were in the session scratchpad; the load-bearing findings are all
already written into this doc and ARCHITECTURE.md §7. The real Tesla's
hotspot-assigned IP that night was `10.117.161.201` (RFC1918, only useful
as a probe target next in-car session, not as a serving address).

**The genuinely promising unexplored angle: WebRTC.** Every app in the
TeslAA/TeslaMirror lineage (including our tier (c)) fights the RFC1918
block at the HTTP-navigation layer with virtual-IP tricks - which Android
14+ has now killed. WebRTC sidesteps the entire fight:
- The car loads our webclient from `veh.modev.be` (public CDN, always
  allowed). Signaling (SDP/ICE exchange) goes through the cloud Worker -
  **control plane only, tiny JSON blobs, fully compliant with the
  "cloud never sees a video frame" principle**.
- Media then flows peer-to-peer over the hotspot link as WebRTC
  UDP/SRTP - the phone's endpoint binds on the AP interface's own real
  address (strong-host model satisfied, no VPN, no tun0, no ingress
  discard involvement at all).
- Open unknowns to research/test, in order: (1) does Tesla's Chromium
  expose working WebRTC APIs (REPORTED evidence to find online); (2) does
  the RFC1918 block apply only to HTTP navigation/fetch or also to ICE
  UDP flows (likely navigation-only, since ICE isn't an HTTP load - but
  ASSUMED until the in-car probe says otherwise); (3) Chrome's mDNS ICE
  candidate obfuscation - the phone side may need to answer/resolve
  `.local` mDNS on the AP link (a plain multicast UDP socket, no special
  permissions, doable in-app); (4) whether a data-channel-only transport
  (keeping the existing WebCodecs pipeline, just swapping WS for
  RTCDataChannel) or a full WebRTC H.264 video track (native hardware
  decode, built-in congestion control) is the better endgame.
- **Next in-car session should carry an S1-style probe page** hosted on
  veh.modev.be: big pass/fail text on screen (readable/photographable in
  the car without adb) testing (a) fetch to an RFC1918 address, (b) WS to
  RFC1918, (c) WebRTC ICE candidate gathering + a loopback/data-channel
  attempt against the phone. One page, five minutes of car time, settles
  the whole direction.

**Also do before the next car trip** (no car needed): the in-app
diagnostics screen (chosen tier, resolved address, live "did anything
ever connect" counters - this session burned a lot of time reconstructing
exactly that from photos); tier (a) IPv6 end-to-end on the emulator; the
founder's APN-protocol check (IPv4/IPv6) on the data SIM.

## Session 7: three non-hardware NEXT_SESSION items

### `MainActivity.localIpAddress()`: prefer the hotspot interface
Small, self-contained fix for the gap flagged since session 4: the old
"first non-loopback IPv4 address" heuristic had no preference for the
actual hotspot/AP interface over e.g. a simultaneously-up cellular data
interface. Now prefers an interface whose name suggests AP/hotspot mode
(`ap*`, `wlan*`, `swlan*`, `softap*`) AND whose address is RFC1918
private range; falls back to any RFC1918 address; falls back to the first
non-loopback address as a last resort. Compiles clean; not separately
exercised live this session since the emulator's actual connection path
went through the tier (c) VPN address the whole time (see below), which
bypasses this function entirely - it's the same fallback path documented
as a latent gap since session 4, still real, just not the path that fired
during this session's testing.

### Adaptive quality ladder: framerate + bitrate + resolution, with hysteresis
`CaptureService.kt` previously only implemented the bitrate lever of
ARCHITECTURE.md §5's three-lever ladder ("drop framerate first
(60->30->24), then bitrate steps, then resolution tier. Recover in the
same order, reversed, with hysteresis"). Now implements all three:

- `frameRateSteps = [30, 24, 18]` - starts one tier "in" from the doc's
  literal 60->30->24 sequence since `H264Encoder`'s real default here is
  already 30, not 60; extended one step further (18) since bitrate is the
  next lever after framerate is exhausted either way. ASSUMED tiers, not
  yet MEASURED.
- `resolutionScaleSteps = [1.0, 0.75, 0.5]` - scales from `baseWidth`/
  `baseHeight` (the real, unscaled viewport dims from the car's `hello`),
  never from the currently-scaled `currentWidth`/`currentHeight`, so
  repeated down-steps don't compound a shrink onto an already-shrunk size.
  A fresh `hello` (e.g. the car reconnecting) updates `baseWidth`/
  `baseHeight` and reconfigures at whatever resolution-ladder tier is
  currently active, rather than discarding the active tier.
- Framerate and resolution changes both require a full encoder/
  VirtualDisplay reconfigure (`reconfigureEncoderForLadder()`, same
  stop/recreate pattern `resize()` already used) - MediaCodec doesn't
  support live resize or live framerate change of a Surface-input encoder.
  Bitrate remains the one lever `MediaCodec.setParameters
  (PARAMETER_KEY_VIDEO_BITRATE)` can step live, no reconfigure needed.
  **Real bug caught by this refactor**: recreating the encoder for a
  framerate/resolution step used to implicitly reset bitrate to
  `H264Encoder`'s 8 Mbps default via its constructor default parameter,
  silently undoing any prior bitrate-ladder step the moment a
  framerate/resolution step fired after it - fixed by threading
  `currentBitrateBps` through explicitly on every reconstruction.
- Down-steps try framerate first, then bitrate, then resolution (matches
  the doc's stated priority - framerate is a barely-visible sacrifice, a
  full IDR-triggering resolution change is the most visible/disruptive so
  it's the option of last resort). Up-steps reverse the order (resolution
  recovers first since it was dropped last, framerate recovers last).
- Hysteresis: a 3-second cooldown (`ladderStepCooldownMs`, unmeasured
  placeholder like the step sizes) between any two ladder moves, since a
  bursty congestion signal calling `adjustQualityForRequest()` rapidly
  would otherwise thrash the framerate/resolution levers' visible
  reconfigure hiccup on every call.

Compiles clean, zero warnings. **Not exercised live this session** - there
is no way to synthesize WS `bufferedAmount` congestion or drive
`onQualityRequest` from the emulator without a real strained network link
or a fake congestion-injecting harness, neither of which exist yet. The
ladder logic itself was read through carefully for correctness (each lever
transition, the hysteresis gate, the bitrate-reset bug above) but the
actual on-device behavior under real congestion is still ASSUMED, same
honesty bar as the constants themselves - a real Gate-2 measurement pass
is still the thing that turns these into MEASURED defaults.

### Generic "pin any widget" tile - real, built, one real platform bug found and fixed
The deferred feature from session 6's Messages-widget research
(`AppWidgetHost` embedding, "right fit for things that will never get
bespoke integration - Home Assistant, weather, a manufacturer's own
widget"). New files: `dashboard/PinnedWidgetHost.kt` (thin `AppWidgetHost`/
`AppWidgetManager` wrapper, persists one pinned widget id in
`SharedPreferences`), `res/layout/dash_tile_widget.xml` (empty-state "+"
placeholder matching the existing tile look, a host `FrameLayout` for the
real `AppWidgetHostView` once pinned, a small unpin "x" button). Added as
a 4th tile in `tilesColumn` (`activity_car_dashboard.xml`) alongside
Navigate/Phone/Messages - verified live the 4-tile column still fits and
reads cleanly at car-viewport proportions, no layout squeeze.

Flow (`CarDashboardActivity.kt`): allocate a widget id ->
`ACTION_APPWIDGET_PICK` (the system's own picker, no special permission
needed to show it) -> `AppWidgetManager.bindAppWidgetIdIfAllowed()` ->
`ACTION_APPWIDGET_BIND` consent dialog if that returns false ->
`ACTION_APPWIDGET_CONFIGURE` if the provider declares one -> persist +
render. `AppWidgetHost.startListening()`/`stopListening()` wired to
`onStart()`/`onStop()` per the API's own required lifecycle pairing.
Unpin button calls `AppWidgetHost.deleteAppWidgetId()` and clears the
persisted id. A stale persisted id (the widget's own app since
uninstalled) is detected and cleared automatically on next load via
`AppWidgetManager.getAppWidgetInfo()` returning null.

**Real bug found and fixed via live testing on the emulator, worth
remembering**: the initial implementation trusted `ActivityResult
.resultCode` from the `ACTION_APPWIDGET_BIND` consent dialog to decide
whether the user approved - `RESULT_OK` meant proceed, anything else
meant delete the id and bail. On this Android 16 build,
**`AllowBindAppWidgetActivity` returns `RESULT_CANCELED` even when the
user taps "Create" and the bind genuinely succeeds** (confirmed by
instrumented logging: `AppWidgetManager.getAppWidgetInfo(widgetId)` comes
back fully populated immediately after, and the widget's own provider
receives real lifecycle broadcasts) - trusting resultCode silently deleted
every successfully-bound widget right after binding it, which is exactly
what made the first several live-test attempts fail with the framework's
own "Couldn't add widget." fallback text (a red herring at first glance -
it looked like a content-rendering failure, but the widget had already
been deleted by the app's own code by the time that view rendered). Fixed
by not gating on resultCode at all - checking `getAppWidgetInfo()` for
real post-bind state instead, which is what production launchers actually
rely on. This is the one piece of this session's widget work that's a
confirmed, generalizable app-level bug fix, not just an emulator
limitation.

**Verified structurally end-to-end on the emulator**: allocate -> pick
(tested against "Battery", "At a Glance", and "Analog") -> bind-consent
dialog appears and resolves correctly with the fix above -> configure
Activity runs for providers that declare one (Analog's real "Select a
Clock Face" screen, confirmed launching and returning correctly) ->
`finalizePinnedWidget()` persists the id and renders `AppWidgetHostView` ->
unpin correctly tears down and reverts to the placeholder -> **the pinned
id survives an app reinstall and relaunch** (real `SharedPreferences`
persistence, confirmed by killing and reinstalling the APK mid-session and
seeing the same widget id attempt to restore on next launch).

**NOT verified: real widget content actually painting.** Every widget
available in this AVD's picker (`Battery`, `At a Glance`,
`com.google.android.settings.intelligence`; `Analog`,
`com.google.android.deskclock`) is a Google-signed system widget, and
every one hits the same `AppWidgetHostView` failure once bound:
`Caused by: java.io.FileNotFoundException: New version of Google Play
services needed. It will update itself shortly.` - this AVD's GMS Core
build is a stub that can't resolve these providers' `RemoteViews`
resources without a real Play Store update, which needs a signed-in
Google account and real internet access this environment doesn't have.
Root-caused via instrumented logging, not guessed: the pick/bind/persist
pipeline above completes successfully every time (confirmed by
`AppWidgetManager.getAppWidgetInfo()` returning valid info and
`dumpsys appwidget` showing a real bound widget), and the failure is
squarely inside `AppWidgetHostView.getDefaultView()`'s own resource
inflation, several frames past anything this app's code touches. A real
third-party widget from an app installed the normal way (not a GMS system
widget) would very plausibly render fine - this is the same class of
"can't fully verify without different real-world conditions" gap this
project has flagged honestly elsewhere (tier (a)/(c) real-Tesla
reachability, live MediaSession content). Confirm against a real widget
(e.g. anything from a regular Play Store app, or a real device with
up-to-date Play services) before trusting content rendering, not just the
pin/persist mechanics.

**Testing note for whoever picks up accessibility-gated work next**: this
session hit a real Android 13+ "restricted settings" gate - a sideloaded
app's accessibility service can be entered into
`Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` via `adb shell settings
put` and still silently fail to actually bind (`dumpsys accessibility`
shows empty `Enabled services`) until the restriction is cleared with
`adb shell cmd appops set <pkg> ACCESS_RESTRICTED_SETTINGS allow` (or by
toggling it through the real Settings UI once by hand, the trusted path a
real user would take). Confirmed this is an environment/tooling quirk, not
an app bug - `VehplayerAccessibilityService` itself was never at fault.
Worth remembering before assuming a fresh emulator/AVD image is
mis-configured next time this flow needs re-testing from a clean install.

## Session 6: real dashboard integrations + a real connectivity bug fix

Kicked off from user feedback on a real device: the Now Playing card was
still the static placeholder, the search keyboard was the wrong tool for a
car (system IME), Phone/Messages tiles just launched other apps instead of
showing anything, and the whole local-IP connection flow was suspected of
being fragile on real hardware. All confirmed and fixed except the last
one's real-hardware verification.

### Navigate: custom keyboard + live suggestions (full-screen overlay)
First attempt built the keyboard *inside* `NavigateMapFragment`'s hero
card - wrong call, verified wrong on the emulator: the hero card is only
~40% of screen height (`activity_car_dashboard.xml`'s 0.6-width, not-full-
height `heroCard`), so a 5-row keyboard overflowed it entirely and hid the
search field it was supposed to serve. Fixed by moving search entirely out
of the fragment into `DestinationSearchOverlayView`, a full-screen overlay
owned by `CarDashboardActivity` (same pattern `MessagesOverlayView` and
`PhoneOverlayView` reuse below) - `NavigateMapFragment`'s "Where to?" bar
is now just a launcher pill.

- `CarKeyboardView.kt`: custom on-screen keyboard (44dp keys, Material
  minimum), letters + a `123` toggle for digits/symbols rather than an
  always-visible digits row (tried that first too, same space problem).
- `DestinationSearchOverlayView.kt`: debounced (300ms) live suggestions via
  Mapbox's Search Box API `/suggest` + `/retrieve` (session-token paired
  per Mapbox's billing model), falls back to one-shot `/forward` geocoding
  if the user types-and-submits before picking a suggestion.
- Verified live on the emulator: typed "part" → real "Party Store"
  suggestion appeared, keyboard never overlapped the search field.

### Now Playing: real MediaSessionManager data
`media/VehplayerNotificationListenerService.kt` is the one
`NotificationListenerService` component both this and Messages ride on -
`MediaSessionManager.getActiveSessions()` requires naming an *enabled
listener component* even though media playback state has nothing to do
with notification content, that's just the API shape. `NowPlayingFragment`
now shows real title/artist/art/play-state, wires transport controls
(play/pause/skip), and tints the play button from the album art's actual
dominant color via `palette-ktx` (dependency was already added session 4
for this, unused until now).

Three real states, each verified live by toggling the permission via adb
(`cmd notification allow_listener` / `disallow_listener`): permission not
granted → "Tap to enable Now Playing" (opens
`Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`); granted, nothing
playing → "Nothing playing"; a track playing → real content. **Not
verified**: an actual live MediaSession populating the content state - the
emulator has no way to get a real media app playing without a Google
account sign-in (YT Music) or a working Chrome first-run flow (hit a
first-run interstitial loop trying to play an mp3 directly, gave up per
the "don't rabbit-hole on browser automation" rule rather than keep
fighting it). The two idle states are real end-to-end verification of the
permission-gating logic; the populated-content code path uses the
documented `MediaController`/`MediaMetadata` APIs the standard way but
hasn't been watched actually firing on a real session.

### Messages: cross-app overview via notification metadata
No public API reads WhatsApp message content at all (deliberately locked
down by Meta), and RCS threads aren't in the SMS content provider - so
there is no generic "read all messages" API to build a real inbox from.
`VehplayerNotificationListenerService.recentMessages()` reads notification
metadata instead (sender, preview text, timestamp, the notification's own
`PendingIntent`) filtered to `CATEGORY_MESSAGE` or `MessagingStyle` extras
- this generically covers SMS/WhatsApp/Telegram/anything that posts a
normal messaging notification, without needing per-app integration. Tap a
message → fires its own `contentIntent`, opening the real conversation in
the real app. `MessagesOverlayView.kt` is the full-screen UI (same
overlay-at-Activity-level pattern as search). Verified live: posted a test
notification via `adb shell cmd notification post -S messaging ...`,
confirmed it appeared in the overlay and rendered via the real pipeline
(the visible text was garbled in that one test because of the *test
command's* shell-quoting, not an app bug - the plumbing itself is real).

**Note for whoever picks up "real widgets" next**: the user has a real,
currently-working WhatsApp home-screen widget (screenshot provided,
conversation list + new-chat button) - `Signal` genuinely does not have
one (checked, a 2014-era feature request still open), but WhatsApp and
Telegram do. A research pass this session confirmed `AppWidgetHost`
embedding (like Nova Launcher and some existing car-dashboard apps already
do) is real and buildable with no special permission, just
`ACTION_APPWIDGET_BIND` user consent per widget - but it's a poor fit for
*replacing* Messages/Phone (foreign widget styling clashes with the
warm-dark theme, phone-homescreen grid sizing doesn't fit car tiles).
Right fit for later: a generic "pin any widget" tile for things that will
never get bespoke integration (Home Assistant, the user's own Tesla
"alset" widget, weather).

### Phone: real call log + contacts, A-Z scrubber
`PhoneOverlayView.kt`, same full-screen-overlay pattern. Two real runtime
dangerous permissions (`READ_CALL_LOG`, `READ_CONTACTS` - not a
special-access settings toggle like the notification listener above, an
actual system permission dialog), requested via
`CarDashboardActivity.phonePermissionLauncher`
(`registerForActivityResult`, has to live on the Activity, a custom View
can't launch one itself).

**Real bug found and fixed via live testing, worth remembering**: the
first call log query used `"${CallLog.Calls.DATE} DESC LIMIT 30"` as the
sort order string - `CallLog`'s provider validates `sortOrder` against a
strict grammar on modern Android and throws `IllegalArgumentException` for
anything with a `LIMIT` clause tacked on, silently swallowed by the
existing `runCatching`, so the call list rendered empty with the correct
*permission* state but no data and no visible error. Fixed by removing
`LIMIT` from the SQL string and capping client-side in the cursor loop
instead, plus added `.onFailure { Log.w(...) }` so this class of bug logs
instead of silently vanishing next time. Verified live end to end: inserted
a real call-log row and a real contact via `adb shell content insert`,
confirmed both rendered correctly, confirmed the A-Z index strip renders
and is positioned correctly (only showed "J", correctly, since only one
contact existed).

### Navigate: recenter-on-me floating button
Small one: panning or searching away from your position previously had no
way back short of relaunching the fragment. `ic_locate.xml` + a circular
button, bottom-end of the map, flies the camera back to `lastKnownOrigin`.
Verified visually live (correct position, doesn't overlap the search bar
or route pill); the actual fly-to wasn't exercised since the emulator has
no live location fix by default.

### Connectivity: a real tier (a) bug fix + tier (c) implemented
Started because the user suspected the local-IP flow was fragile on real
hardware and recalled TeslAA/androidwheels.com solving this differently.
Two research passes (backgrounded, this session) plus the user's own
follow-up push (spotted a live WhatsApp widget contradicting an early
"WhatsApp mostly doesn't have widgets" assumption, then asked for a deep
read of teslamirror.com specifically) built up a real picture:

1. **Tesla's in-car Chromium really does refuse RFC1918 addresses**
   (`10/8`, `172.16/12`, `192.168/16`) outright - independently
   corroborated by a Tesla fleet-telemetry GitHub issue, multiple
   unconnected Tesla Motors Club forum threads spanning years, and
   `teslamirror.com`'s own marketing copy explaining why their app needs a
   VPN permission at all. Not a rumor.
2. **A cloud relay (the originally-recommended fix) was rejected**: it
   would violate this codebase's own explicit architectural principle -
   `cloud/src/index.ts`'s header comment literally states "Control plane
   only, never media (Foundation §6: 'Cloud never sees a video frame')".
   Piping video/audio bytes through a Cloudflare Worker, even without
   persisting them, breaks that promise. Caught before writing any Worker
   relay code, not after - worth remembering as a gate to check before
   proposing *any* future architecture change that touches the media path.
3. **Real bug fixed**: `MainActivity.awaitHttpServerAndShowUrl()` always
   called `localIpAddress()` (a plain IPv4 hotspot guess) to build the
   shown connection URL, completely ignoring the real tier (a) IPv6 GUA
   address `ReachabilityLadder.decide()` may have *already found* a few
   lines earlier in `onStartClicked()`. So even on a phone where tier (a)
   was genuinely available, the app would still show an RFC1918 IPv4 URL
   and hit the exact block being worked around. Fixed: the decided address
   now flows through (`reachableTier1Address` field,
   `formatHostForUrl()` brackets IPv6 literals for the URL authority).
   `HttpAssetServer.kt`'s `/go` redirect already reflects back whatever
   `Host` header the browser used rather than re-deriving its own address,
   so no change was needed there - it automatically benefits.
4. **Tier (c) implemented** (`VpnReachabilityService.kt`, previously an
   empty stub with a TODO deferring it until "S1 spike data exists"):
   `VpnService.Builder().addAddress("100.99.9.1", 32)`, no packet-
   forwarding loop needed - standard kernel IP routing delivers a packet
   arriving on any physical interface (the hotspot AP link) to the local
   socket stack whenever the destination address is configured on ANY
   local interface, including a VpnService tun address, regardless of
   which wire it physically arrived on; `HttpAssetServer`/`LocalMediaServer`
   already bind wildcard so the existing listening socket just picks it up.
   **The address range (`100.64.0.0/10`, RFC 6598 CGNAT space, NOT
   RFC1918) is evidence-backed, not guessed**: `teslamirror.com` - a real,
   currently-selling competitor app - documents doing exactly this,
   binding its own embedded server to `100.99.9.9` for this exact reason;
   independent TMC forum hobbyists solving the identical problem converged
   on the same non-RFC1918 workaround. `MainActivity` now actually starts
   this service and threads its address through the same
   `reachableTier1Address` path as tier (a).
   
   **Verified on the emulator, as far as the emulator can go**: VPN
   consent dialog → real system VPN key icon appeared in the status bar →
   `adb shell ip addr show tun0` confirmed `100.99.9.1/32` really assigned
   → the shown connection URL correctly read `http://100.99.9.1:8080/go`.
   **NOT verified**: whether a real *second* device (the car) on the
   phone's real WiFi hotspot can actually route to that address - the
   emulator is a single virtual device, it cannot simulate a second peer
   joining its hotspot. The kernel-routing reasoning above is sound and
   matches how `teslamirror.com` documents doing it, but this is
   *reasoned, evidence-backed, not yet real-hardware-confirmed* - the
   honest distinction this project has cared about all along. Confirm
   against a real Tesla before trusting this in the field.
5. **Samsung DeX**: researched and ruled out (see below) - no relevant API
   surface, not worth engineering effort for this product.

### Samsung DeX (researched, not built)
No DeX-specific API surface relevant to `MediaProjection`/`VirtualDisplay`/
`AccessibilityService` input injection - it's the same core Android APIs
either way. DeX's own display-output path (Miracast/wired) can't
substitute for the browser-based delivery this app already built (
different transport models entirely - a browser tab is not a Miracast
receiver). Not Samsung-exclusive as a category (Motorola "Ready For",
stock Android 16 Desktop Windowing exist too) but fragmented with no
shared API, so "supporting DeX" wouldn't even generalize. **Verdict: not
worth building anything for this.**

### Still genuinely open from this session
- **Accessibility/handsfree audit**: not started. Touch target sizes,
  contrast, glanceability, and whether search-while-typing should be
  restricted to parked-only are all still open product/UX questions, not
  just implementation ones.
- **Start/update screen (`MainActivity`'s plain layout) restyle**: not
  started, still the old plain look, not matching `CarDashboardActivity`'s
  warm-dark Space Grotesk visual language.
- **Real-hardware validation of tier (a)/(c)** (see above) - the single
  biggest remaining unknown, blocks trusting the whole connectivity fix.
- ~~**Generic AppWidgetHost "pin any widget" tile**~~ - built session 7, see
  that section. Pin/persist mechanics verified end to end; real widget
  content rendering still needs a real device or a real Play Store update
  to confirm (this AVD's GMS stub blocks it for every available widget).

### Market/business/legal restructuring (session 6, second half)
Triggered by the user uploading three documents mid-session; one
(`REVIEWENSYNC19juli.md`, about an unrelated Dutch "Vondst" waitlist
product) was correctly identified as an accidental upload and **not**
incorporated - flagged back to the user rather than silently applied to
the wrong project. The other two (`COMPETITIVE_REASSESSMENT.md`,
`MARKET_AND_PRICING.md`) are real, evidence-tagged vehplayer research,
copied into `docs/` and acted on:

- **`VEPLA_Foundation.md` retired, replaced by `VEHPLAYER_Foundation.md`.**
  Fixed the multi-session "phantom v1" problem (the old doc repeatedly
  said "unchanged, see v1 §N" for a v1 that never existed anywhere in this
  repo) by removing the phantom citations and stating each section's
  actual content directly - reconstructed from cross-references elsewhere
  in the corpus where recoverable (Autopsy Lesson 1, the Castla/GPL
  lesson), explicitly flagged as not-recoverable where it genuinely wasn't
  (most of the original Lessons 2-5), rather than inventing plausible-
  sounding history to fill the gap.
- **`GROWTH_SAAS.md` rewritten**: pricing moved from "subscription or
  one-time, price-test both" to a decided one-time €9.99 Pro tier (the
  original subscription framing directly contradicted the actual pricing
  decision once real competitor data was checked); "diversification is the
  moat" walked back to "protocol independence + purpose-built dashboard is
  the moat" once Lucid (added CarPlay, left the market) and GM (Google
  Built-in has no usable browser) turned out not to support the
  diversification story; Fleet/Dealer tier marked an explicit speculative
  appendix, not roadmap; added the TesAA/WebAA-outage launch-window
  sequencing (a real, time-limited, findable stranded user base) as GTM
  step zero.
- **`ARCHITECTURE.md` §2 updated**: Tesla suppresses `<video>` element
  playback in Drive (REPORTED, independent sources including a TeslaTap
  developer describing this project's own canvas-based architecture) -
  promoted WebCodecs-to-canvas from "primary, with a fallback" to "the
  only path that matters for a driving product", and documented why
  `mseFallback.ts`'s muxer is deprioritized rather than simply unfinished
  (that doc comment updated too). Also logged TeslaMirror's published
  per-MCU codec calibration (MCU2 → H.264 540p30, notably below our
  current 720p30 default) as a strong prior to verify at Gate 1, not
  ground truth.
- **New root files**: `CLAUDE.md` (session fast-path conventions),
  `brand.json` (name/tagline/domain/pricing/visual-identity single source
  of truth - domain is explicitly marked not-yet-chosen, don't treat any
  candidate as decided), `Makefile` (`make release` hard-codes the
  `aapt`/asset-name verification steps from two real incidents this
  session - see below - so they can't be silently skipped again).
- **`legal/` directory created**: `privacy-policy.md`,
  `terms-of-service.md`, `processing-register.md`, `trademark-note.md`.
  All explicitly drafts, `[MENS]`-flagged wherever a real fact (legal
  entity, address, jurisdiction) or a real lawyer's review is needed - not
  written to look publish-ready, written to be an accurate starting point
  for one.

**Two real incidents caught and fixed inside this same doc-integration
pass, worth remembering**:
1. Publishing build-11 initially uploaded the GitHub release asset as
   `vehplayer-debug-11.apk` instead of `vehplayer-debug.apk` - `gh release
   create file#label` only sets a display label, it does NOT rename the
   actual asset (unlike what the build-10 publish session assumed, which
   happened to work only because that source file was already named
   correctly). `UpdateChecker.kt` requires the exact filename
   `vehplayer-debug.apk`; this would have silently broken in-app updates
   for anyone who received a build-10 notification. Caught immediately
   after publishing by re-checking the release's own asset JSON, fixed by
   deleting and re-uploading with the correct filename. `make release` now
   verifies this automatically.
2. A privacy-policy/processing-register pass for an unrelated unfamiliar
   project (mentioned inside `REVIEWENSYNC19juli.md`, the accidentally-
   uploaded doc) documented a domain purchase as an already-completed fact
   when the domain had actually been registered by an unconnected third
   party - the working agreement that came out of that incident
   ("[MENS] facts stay open until confirmed, never folded into a facts
   table as settled") was applied proactively to vehplayer's own domain
   section in `brand.json`/`trademark-note.md` this session, before it
   could cause the same problem here.

### Real-phone testing round (session 6, third pass) - found what the emulator couldn't
The user tested the actual build on their own Samsung phone (not the
emulator) and sent real screenshots. Two real, concrete bugs surfaced that
static review and emulator testing both missed:

1. **Contacts A-Z scrubber was invisible on a real screen** (worked, but
   functionally couldn't be seen - 10sp `dash_text_muted` text against
   the near-black background). Fixed: bigger (13sp), bold, full-contrast
   `dash_text_primary`, and the strip now has its own `dash_surface`
   background so it reads as a real control instead of blending in.
2. **`NavAppPreference`'s installed-nav-app detection silently found
   nothing, even with Google Maps genuinely installed** - real Android 11+
   package-visibility restriction: `queryIntentActivities()` returns
   empty for anything outside the calling app's own package unless
   `AndroidManifest.xml` declares a `<queries>` block for the intent
   being probed. `adb shell cmd package resolve-activity` isn't subject
   to this (shell UID), which is exactly what made it look like a
   device-specific or timing issue before actually checking the manifest.
   Fixed with a `<queries>` declaration for `geo:` intents. Verified live
   on the emulator after the fix: picker correctly showed "Maps",
   selecting a destination correctly launched Google Maps with the right
   coordinates instead of drawing an in-app route.

Also surfaced, **not a bug, just needed explaining**: the Messages tile
showed "No recent messages" despite the user having 93 unread messages in
Google Messages. Correct behavior, not broken - `NotificationListenerService.
activeNotifications` only returns *currently-posted* system notifications,
not an app's own internal inbox/unread state. If those 93 messages'
notifications were already seen/cleared on-device (common after opening
the Messages app at some point), there is nothing left for a
notification-based reader to see - no generic API reads WhatsApp/SMS/RCS
inbox content directly (see the Messages section above). Added an
explanatory subtext to the empty state
("Shows new messages as they arrive, not your full inbox") rather than
leaving it looking like a silent failure. To test this feature live,
send yourself a fresh message so a real notification posts.

**New feature, also from this round**: `NavAppPreference.kt`/
`NavAppPickerView.kt` - Navigate can now hand a chosen destination to a
real installed nav app (Google Maps, Waze, whatever's actually detected
via the `<queries>`-enabled `geo:` probe, not a hardcoded allowlist)
instead of always rendering in-app, picked via a settings icon next to
the recenter button on the Navigate page. Real user finding worth noting
for later: **Waze does not appear to publish a real home-screen widget,
Google Maps does** (relevant if the AppWidgetHost "pin any widget" tile
from earlier this session's research ever gets built - Maps would be a
good candidate, Waze wouldn't).

**Accessibility/handsfree audit, done this pass**: reviewed touch target
sizes across every list row added this session (suggestions, contacts,
call log, messages, nav-app picker) - all comfortably exceed Material's
48dp minimum once real padding + multi-line text content is accounted
for, no changes needed beyond the A-Z scrubber fix above. Reviewed
`contentDescription` coverage - every *interactive* icon-only control
(recenter, nav-app settings, all overlay close buttons) already has one;
several purely-decorative icons (tile icons sitting next to their own
label text, the search-bar compass icon next to "Where to?", empty-state
icons next to their own explanatory text) don't, which is correct/expected
(TalkBack reads the adjacent visible text; a redundant description on a
decorative icon next to text it duplicates is noise, not an improvement).
**Left explicitly open, a product decision not an implementation one**:
whether destination search/typing should be restricted to parked-only -
flagged back in the original Navigate keyboard work this session, still
unresolved, needs a founder call not a Claude default.

**Also restyled this pass**: `MainActivity`'s setup screen (`activity_main.xml`,
replacing the plain programmatically-built `LinearLayout`/`Button` UI) to
match `CarDashboardActivity`'s warm-dark Space Grotesk look - this was the
last screen in the app that still looked like a dev shell. Verified live:
status text updates, both step buttons, and the update banner all fire
their real click handlers correctly in the new layout.

**Explicitly deferred to a fresh session, per the user**: the tier (a)/(c)
real-Tesla connectivity verification (`docs/ARCHITECTURE.md` §7,
`VpnReachabilityService.kt`) - the user wants that investigated with fresh
context rather than at the tail end of an already-very-long session.

## CarDashboardActivity (session 4, build-9) - Phase 1 of 3
Founder pushback that mattered: "casting my whole phone has no value to me,
I want the Android Auto/CarPlay *feeling*, not a raw mirror." Re-embedding
the actual Android Auto/CarPlay protocol is not viable (Tesla has zero
native support for either, that constraint is why this whole project casts
to a browser instead of using a head-unit protocol; TeslAA - a real
predecessor project - died when Google tightened one validation check it
controlled, which is the concrete case for staying protocol-independent,
see GROWTH_SAAS.md §5). But "cast our own car-optimized UI instead of the
raw phone screen" needed no protocol at all: mirror mode already casts
whatever's in the foreground, so if a purpose-built dashboard Activity is
what's in front when streaming starts, that's what the car sees. Zero
wire-protocol or capture-pipeline changes.

**Note found while investigating this (session 4), fixed session 6**:
`docs/VEPLA_Foundation.md` was internally versioned "v2" and repeatedly
said "unchanged, see v1 §N" for load-bearing sections - but no v1 document
ever existed anywhere in this repo. Session 6 replaced it entirely with
`docs/VEHPLAYER_Foundation.md`: the phantom "see v1" pointers are gone,
each section states its actual current content directly, and where the
original content genuinely wasn't recoverable from anywhere else in the
corpus (most of the original Autopsy's Lessons 2-5), that's flagged
explicitly in that document's §4 rather than invented. Session 6 also
retired the "VEPLA" name entirely per real trademark/market research
(`docs/COMPETITIVE_REASSESSMENT.md` §7.1) - `vehplayer` is now the actual
product name, not just a working codename.

Three-phase plan:
1. **Done (build-9)**: `CarDashboardActivity` - full-screen, immersive,
   warm-dark themed (deliberately not the cool blue-grey every AA/CarPlay
   clone uses), Space Grotesk display type (OFL, `assets/licenses/`). Hero
   now-playing card (static "Nothing playing" placeholder) weighted larger
   than the Navigate/Phone/Messages tile column - hierarchy is
   informational (what you glance at constantly vs. occasionally), not
   decorative. Tiles fire plain launcher intents; whatever they open is
   what mirror mode shows next, automatically, no new capture code.
   `MainActivity` now hands off to this Activity (with the resolved
   connection URL as an intent extra) once `CaptureService.httpServerPort`
   resolves, instead of showing the URL on its own plain status screen.
2. **Not started: real now-playing.** Needs `MediaSessionManager` +
   Notification Listener access (`android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`)
   to read whatever's actively playing (Spotify, YouTube Music, anything) -
   this is a sensitive permission, give it its own explicit ask/explanation
   step, same pattern as the accessibility and install-unknown-apps flows
   already in the app, don't bundle the request into an unrelated action.
   `androidx.palette:palette-ktx` is already a dependency (added this
   session) for tinting the now-playing card from the actual album art's
   dominant color once real art is available.
3. **Not started: nav-app picker.** Query installed apps that handle
   `Intent.ACTION_VIEW` + `geo:` (covers Waze, Maps, whatever's installed,
   not just those two hardcoded), let the user pick a default, store it
   (SharedPreferences is enough, no need for anything heavier), fire that
   specific app instead of the generic geo intent that currently lets the
   system prompt/pick.

Verified end to end on the emulator (not just the layout screenshotted in
isolation): real Start -> VPN -> screen-share flow, confirmed via `dumpsys`
that `CarDashboardActivity` is the actual foreground activity once
streaming starts, real detected IP shown, Phone tile opens the real dialer.

## HttpAssetServer lifecycle fix (session 4, build-8)
`HttpAssetServer` (the local `/go` endpoint - mints the pairing token,
serves the offline bundle fallback) used to be owned by `MainActivity` and
stopped in its `onDestroy()`. `CaptureService` (the actual capture/encode
pipeline) is a proper foreground service and survives the user
backgrounding or swiping the app away while walking to the car - that's
the whole point of a foreground service. But the HTTP server died the
moment the Activity did, while capture kept running underneath it: the
car's `/go` request got `ERR_CONNECTION_REFUSED` even though the hotspot and
the capture session were both fine. A user reported exactly this with a
confirmed-correct hotspot+Tesla connection, which is what pointed at the
real cause instead of a wrong assumption about their setup.

Fixed: `HttpAssetServer` now starts inside `CaptureService.onStartCommand()`
(same port-fallback retry logic that used to live in `MainActivity`) and
stops in `stopCaptureInternals()`, so its lifetime matches the actual
capture session, not whichever screen happens to be on top.
`CaptureService.httpServerPort` is public (`private set`) so `MainActivity`
can poll it briefly after calling `CaptureService.start()` to build the
"open this URL" message, since the port now resolves asynchronously inside
the service instead of synchronously in `onCreate()`.

Verified on the emulator: started streaming, confirmed `/go` responds,
swiped the vehplayer card out of Recents (confirmed via uiautomator the
card was actually gone, task removed), confirmed the process survives
(foreground service), confirmed `/go` still responds with a fresh valid
token afterward - it did not before this fix, same repro reproduced the
bug cleanly on the old code first.

**Known remaining gap, not yet fixed**: `MainActivity.localIpAddress()`
still picks "the first non-loopback IPv4 address" with no preference for
the actual hotspot/AP interface over e.g. a cellular data interface if
both happen to be up. Wasn't the cause of either bug found this session
(the ERR_CONNECTION_REFUSED user's IP address itself was correct, hotspot
really was up), but it's a latent correctness gap worth tightening -
prefer an interface whose address is in a private RFC1918 range and/or
whose name suggests AP/hotspot mode, before falling back to "first
non-loopback" as a last resort.

## Two real crash fixes (session 4) - read this before touching CaptureService/H264Encoder again
Both found from a user-provided screen recording of a real device crash-loop
("vehplayer stopt steeds"), then reproduced deterministically on the emulator
via a scripted uiautomator flow (enable accessibility -> Start -> VPN
consent -> Share entire screen), not by guessing:

1. **`CaptureService.onStartCommand`**: `startForeground()` used the
   manifest's full `mediaProjection|microphone` type unconditionally. On
   API 34+, the `microphone` type requires RECORD_AUDIO to be an actively
   *granted* runtime permission, not just manifest-declared - this app
   never requests it (Route B/`lowLatencyAudio` is hardcoded `false`, not
   wired to any UI control), so every single stream start crashed with a
   SecurityException before MediaProjection was even touched. Fixed:
   `startForeground()` now passes only `FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION`
   unless `lowLatencyAudio` is actually true. **If Route B ever gets wired
   to a real UI control, a RECORD_AUDIO runtime permission request needs to
   be added alongside it** - this fix doesn't add one, it just stops
   claiming the microphone type when it isn't used.
2. **`H264Encoder.start()`**: `MediaCodec.configure()` crashed with a bare
   `IllegalArgumentException` (no detail message) immediately after fix #1
   stopped masking it. Bisected key-by-key on the emulator:
   `KEY_PREPEND_HEADER_TO_SYNC_FRAMES` and `KEY_INTRA_REFRESH_PERIOD` both
   independently crash `configure()` on this device's encoder *even though*
   `MediaCodecInfo.CodecCapabilities` reports them supported - capability
   reporting isn't trustworthy enough to gate on alone. Fixed: `start()` now
   tries the full ARCHITECTURE.md §2 format first, catches
   `IllegalArgumentException`, and retries with a minimal fallback format
   (color format/bitrate/framerate/plain I-frame-interval only) rather than
   crash-looping or permanently disabling these features for every device.
   **This was only tested against one encoder** (the Android 16 emulator's
   software AVC encoder) - the fallback path is what makes this safe on
   other devices, not a claim that the two flagged keys are broken
   everywhere; a real MCU2/MCU3-paired phone might accept them fine.

## Cable-free update pipeline (session 3)
No Play Store, so no silent auto-update, Android always requires one install
tap for a sideloaded app. What this gets you instead: publish from anywhere
with `gh release create build-<N> <apk>#vehplayer-debug.apk --repo
mounssif/vehplayer` (tag must be `build-<versionCode>`, asset must be named
exactly `vehplayer-debug.apk`, `UpdateChecker.kt` parses both), and the app
finds and installs it over the network on next launch, no USB/phone<->laptop
transfer. Build with `./gradlew assembleDebug -PvehplayerVersionCode=N
-PvehplayerVersionName=...` first, bump N each time.

**Real incident this session, avoid repeating it**: build-5 and build-6 got
published with the `-P` flags forgotten on the *final* rebuild right before
packaging (an earlier versioned build had been used for emulator testing,
then a plain unversioned `./gradlew assembleDebug` ran again "just to be
sure" and THAT default-versionCode=1 artifact is what got copied and
published). Both releases silently shipped versionCode=1 under a `build-5`/
`build-6` tag, so `UpdateChecker` compared "6 available" against an
installed app that was *actually* also versionCode 1 forever - the update
button worked, the install genuinely succeeded, and the banner never went
away, because the newly-installed APK still self-reported as build 1.
**Always run `aapt dump badging <apk> | head -1` on the exact file about to
be uploaded and confirm the versionCode before `gh release create`** - that
would have caught it immediately, and now this is what build-7 onward
actually does.

`ApkInstaller.kt` downloads the APK in-app (DownloadManager, with live
percentage progress polled into the status text, this went through two
rounds of real-device feedback this session: first "doesn't tell you
anything while downloading", fixed with progress polling) and hands it to
the system installer directly via a FileProvider `content://` URI, no
browser hop. Needs `REQUEST_INSTALL_PACKAGES` plus a one-time per-app
"install unknown apps" grant (`ApkInstaller.installPermissionSettingsIntent`),
same shape as the accessibility permission flow already in the app.
`android/app/debug.keystore` is committed and pinned in `signingConfigs`
specifically so this works - a build signed with a different (e.g.
freshly-generated CI) keystore would fail to install as an update over the
existing app. Verified the complete loop on the emulator: banner -> tap ->
permission settings -> tap again -> live percentage -> "Download complete,
opening installer..." -> real system update dialog.

**Google Play Protect flags the app as "may be harmful" during that install
scan** (found running the loop above on the emulator's Play Store system
image, not a code bug). Not a hard block - "Install anyway" is still there
below the warning - but expect it on every future install/update, this is a
heuristic/reputation call, not a one-time thing that clears once accepted.
Root cause is almost certainly the permission combination:
`VehplayerAccessibilityService`'s input injection (dispatchGesture) plus
`REQUEST_INSTALL_PACKAGES` self-updating is close to the textbook shape of
a remote-access trojan to Play Protect's heuristics, independent of actual
intent. Both permissions are core to what this app does (input control is
the whole point of the accessibility service; self-update is what this
session was asked to build), so there isn't a code fix that makes this go
away without cutting a real feature. Do not attempt to suppress or evade
the Play Protect prompt, that's a user security decision to make on their
own device, not something to engineer around.

**Correction, session 4**: a user-reported "vehplayer stopt steeds"
crash-loop was initially assumed to be this Play Protect prompt (plausible
from a screenshot alone). A screen recording proved that wrong - the app
actually crashed for the two real reasons in "Two real crash fixes" above,
nothing to do with Play Protect at all. Lesson: don't diagnose a crash from
a single screenshot when a fuller repro (video, or better, logcat) is
available; the two are easy to conflate since both can show up around the
same install/update flow.

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

## Also still open from before (Foundation TODOs)
- Trademark check for "vehplayer" itself (Foundation §1) - the VEPLA-name
  collision that motivated this is resolved (VEPLA retired entirely,
  session 6), but "vehplayer" hasn't had a real trademark screening either.
- Short car-facing domain (Foundation §1, `COMPETITIVE_REASSESSMENT.md`
  §7.2): `veh.modev.be` is a placeholder (12 chars, two dots, personal
  domain in the product surface). User is looking for a short (ideally
  ~3-4 letter) domain - **not yet chosen or purchased**, do not treat any
  specific candidate as decided until confirmed.

## Scope-creep tripwires (Working Agreement, Foundation §12)
- Any pull toward a second car brand, Fleet tier, or paid marketing before
  Gate 4 is complete.
- Any dependency that reintroduces Android Auto/CarPlay protocol code
  (Foundation §4 Lesson 1).
- Any code copied or closely modeled from Castla (GPL-3.0), architecture
  ideas only.
