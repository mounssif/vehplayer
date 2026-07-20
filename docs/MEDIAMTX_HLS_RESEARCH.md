# MediaMTX + HLS pipeline research (session 10)

> Companion research memo for the session-10 investigation in
> `NEXT_SESSION.md` and the CONTESTED block in `ARCHITECTURE.md` §2. This
> is a design-question writeup, not a settled decision. It exists to answer
> the founder's session-10 questions: can MediaMTX support cheap devices,
> how do we get an HLS stream to the car safely, and self-host vs sell our
> own hardware. Evidence tags follow the house rule: MEASURED (verified on
> our own repo/car), REPORTED (a public source says so, cited), ASSUMED
> (our inference, flagged). No em-dashes, per CLAUDE.md.

## 0. The one fact that gates everything below

Two independent findings collide with the existing architecture, and both
have to be said before any MediaMTX/HLS plan is costed:

- **The Tesla browser is Chromium 140. Native HLS in Chromium only landed
  in 142** (desktop, rolled out ~Dec 2025). REPORTED
  ([tech-ish](https://tech-ish.com/2025/12/08/google-chrome-microsoft-edge-chromium-native-hls-playback-desktop/),
  [windowsreport](https://windowsreport.com/chrome-on-desktop-to-finally-support-native-hls-playback/)).
  So on the actual car you cannot rely on a plain `<video src="...m3u8">`;
  you must use **hls.js**, which feeds segments into a `<video>` element via
  **Media Source Extensions (MSE)**. This is exactly what row B of
  `video-test.html` predicts (it will read SKIP on the car), and why row C
  (hls.js/MSE) is the row that actually decides this.
- **HLS is fundamentally a `<video>`/MSE format.** The project already
  believes (REPORTED, `ARCHITECTURE.md` §2) that Tesla suppresses `<video>`
  in Drive, and chose WebCodecs-to-canvas specifically to dodge that.
  hls.js renders through a real `<video>` element, so **if that suppression
  is real, an HLS path is Park-only** (ASSUMED, strong). If session 10's
  counter-observation holds (tiktok/youtube, both MSE `<video>`, playing in
  Drive), then the suppression claim is false and HLS becomes viable in
  Drive too.

**Net: the entire viability of the MediaMTX/HLS direction in Drive is
gated on `video-test.html`'s row-C result in the actual car.** Do not build
any of the below until that probe runs. If HLS is being considered
*because* it looks simpler than the WebCodecs pipeline, note that in Drive
it buys nothing that WebCodecs does not already give, and adds multi-second
latency (see §2). Its honest niche is **parked long-form/passenger media**,
not the live low-latency mirror.

## 1. Is MediaMTX even supportable? Yes, cleanly

MediaMTX (`github.com/bluenviron/mediamtx`, formerly rtsp-simple-server,
latest v1.19.2 at time of writing) is a single dependency-free Go binary.
REPORTED ([README](https://github.com/bluenviron/mediamtx)): "does not
require any dependency or interpreter, it's a single executable."

**Official prebuilt arch matrix** (REPORTED,
[releases](https://github.com/bluenviron/mediamtx/releases) +
[install docs](https://mediamtx.org/docs/kickoff/install)), asset pattern
`mediamtx_v<version>_<os>_<arch>.tar.gz`:

- `linux_amd64`, `linux_arm64`, `linux_armv7`, `linux_armv6`
- `darwin_arm64` (and historically `darwin_amd64`, treat as ASSUMED until
  confirmed on the releases page)
- `windows_amd64` (.zip)
- official Docker image `bluenviron/mediamtx`

Because it is statically linked Go with no runtime, portability to cheap
SBCs is excellent: pick the matching arch, drop the binary + `mediamtx.yml`,
run.

- Pi Zero 2 W (armv8/A53): `linux_arm64` (64-bit OS) or `linux_armv7`
  (32-bit OS). ASSUMED from arch.
- Pi 3/4/5, Orange Pi, Radxa (arm64): `linux_arm64`.
- Original Pi Zero / Zero W (armv6): `linux_armv6`.

**Licensing: MIT** (REPORTED, README). Safe to bundle, redistribute, and
ship commercially inside a proprietary app or on a companion device, with
the MIT notice retained in third-party attributions. This is a clean
contrast to the project's Castla concern (Castla is GPL-3.0, strong
copyleft, never copied or closely modeled per CLAUDE.md). MediaMTX carries
no such restriction. Caveat: a MediaMTX build that links ffmpeg/libav for
transcoding pulls in LGPL/GPL considerations, but the plain remux binary
does not.

**Running directly on Android is not officially supported** (REPORTED,
[discussion #1757](https://github.com/bluenviron/mediamtx/discussions/1757)).
The maintainer's guidance: do not attempt JNI/shared-library integration;
if you must, compile without cgo, embed the binary as a static asset, and
launch it as a subprocess via `ProcessBuilder`. Real-world caveat (ASSUMED,
this is the usual failure mode): modern Android enforces W^X /
no-execute-from-writable-app-storage, which is the common reason "just exec
the bundled binary" fails. Feasible but fragile and untested; treat as a
research spike, not a shippable path.

## 2. Protocols and latency

Ingest (publish): MoQ, SRT, WebRTC/WHIP, RTSP, RTMP, HLS, MPEG-TS, RTP.
Serve (read): MoQ, SRT, WebRTC/WHEP, RTSP, RTMP, HLS (standard and
Low-Latency). "Streams are automatically converted from a protocol to
another." REPORTED (README). So the phone-to-car shape is native: ingest
RTMP/RTSP/WHIP from the phone, serve HLS/LL-HLS (or WebRTC/WHEP) to the car.

HLS variant is a config switch (`hlsVariant`: `mpegts` / `fmp4` /
`lowLatency`), with LL-HLS tunables `hlsSegmentDuration: 1s`,
`hlsPartDuration: 200ms`. REPORTED
([mediamtx.yml](https://raw.githubusercontent.com/bluenviron/mediamtx/main/mediamtx.yml)).

Rough end-to-end latency (protocol-general, not MediaMTX-measured):

- WebRTC/WHEP: ~100-500 ms. REPORTED
  ([Mux](https://www.mux.com/articles/low-latency-live-streaming-developers-guide-ll-hls-webrtc-cmaf)).
- LL-HLS: ~2-4 s. REPORTED
  ([Cloudinary](https://cloudinary.com/guides/live-streaming-video/low-latency-hls-ll-hls-cmaf-and-webrtc-which-is-best)).
- Standard HLS: ~10-40 s. REPORTED (same sources).

Consequence: HLS (even LL-HLS) is far worse than the current
WebCodecs/WebRTC ~100 ms class and unacceptable for touch-interactive
mirroring; fine for passive parked video. If low latency ever matters
through MediaMTX, its WebRTC/WHEP output is the technically superior path it
already provides (subject to the same Drive `<video>` question, since WHEP
also renders to a `<video>` element in the browser).

## 3. Resource footprint on cheap hardware

Key distinction: **remux (repackage, no re-encode) is cheap; transcode is
expensive.** If the phone already emits H.264/AAC (it does, via
`H264Encoder`), MediaMTX only repackages into HLS segments, no decode-encode.
ASSUMED (strong, standard media-router behavior; README self-describes "a
focus on efficiency").

- No clean MEASURED pure-remux 1080p benchmark was found. A frequently
  cited "~90% CPU on an original Pi Zero W" figure
  ([discussion #3311](https://github.com/bluenviron/mediamtx/discussions/3311))
  is a camera capture+encode workload, not a remux, and must not be read as
  the repackaging cost.
- The Pi Zero 2 W has a quad-core A53 (vs the original Zero W's single
  core), so far more headroom; its 512 MB RAM is the real constraint, and
  community guidance prefers a 32-bit OS to avoid OOM. REPORTED.
- Assessment (ASSUMED, flagged): a single 1080p H.264 stream that is only
  remuxed to HLS should sit in the low tens of percent of one core and tens
  of MB RAM, comfortably within a Pi Zero 2 W or a Pi 3. **Benchmark the
  actual remux path on the target board before committing** (the kind of
  MEASURED gap this project cares about).

## 4. Getting the stream to the car safely

Constraints: data plane stays local (no cloud media, ever, CLAUDE.md); the
car refuses RFC1918 IPv4; IPv6 global-scope on the hotspot interface is the
only live reachability path (MEASURED, session 7); the current shell page is
`https://veh.modev.be`.

### 4a. The mixed-content wall

An HTTPS page cannot fetch `http://` or `ws://` subresources; Chromium
blocks it before the request completes. REPORTED
([onlineplayer.app](https://onlineplayer.app/en/blog/m3u8-hls-streaming-guide),
MDN secure-contexts). You cannot install a root CA or flip
`chrome://flags` on the Tesla browser, so self-signed-on-device and the
usual localhost-trust tricks are dead ends here.

### 4b. Three real options

- **(a) Serve the whole HLS page as plain HTTP from the local device over
  an IPv6 global literal** (`http://[2001:...]/index.html`). Same-scheme
  HTTP subresources, so no mixed-content block. MSE does **not** require a
  secure context, so HLS plays. Cost: no secure context means WebCodecs,
  getUserMedia, service workers, and Web Crypto `subtle` are unavailable on
  that page, so it is a separate, simpler page from the WebCodecs live
  mirror, not a shared one. Zero cert ops. **Simplest first cut, and the
  recommended pick if HLS lives on its own page.** ASSUMED.
- **(b) Real TLS cert the local device presents**, to keep everything
  HTTPS: own wildcard (e.g. `*.dev.modev.be` via Let's Encrypt DNS-01), an
  authoritative DNS that hands out an AAAA pointing at the device's current
  global IPv6, cert renewed on-device. Data still flows phone-to-car
  locally; only DNS + issuance touch the internet, so it respects
  "data plane local" (DNS is control-plane). Certs pin to a name not an IP,
  so the AAAA can change without reissue as long as DNS updates. Cleanest
  "keep the unified `veh.modev.be` shell" answer, at the cost of a small
  on-device DNS updater + cert renewal. If `veh.modev.be` (origin) runs
  hls.js against `https://car-xxx.dev.modev.be` segments, the device must
  send permissive **CORS** (MediaMTX HLS does by default, REPORTED, verify).
- Avoid `nip.io`/`sslip.io` (flakier, shared-domain rate limits) and avoid
  self-signed (no CA install path on the car).

### 4c. Authentication on a shared hotspot

Other passengers/devices on the phone hotspot could hit the m3u8. Reuse the
existing short-lived pairing tokens. MediaMTX supports three auth backends
(REPORTED,
[auth docs](https://mediamtx.org/docs/features/authentication)):

- **Internal** users + per-path permissions; HLS clients pass creds as
  query params or HTTP Basic, and MediaMTX enforces on **every segment
  request**, not just the playlist. A per-session random path
  (`/s/<token>/index.m3u8`) scoped to a single user gives per-session
  isolation.
- **External HTTP auth**: MediaMTX POSTs each attempt to a URL we host,
  which validates the vehplayer pairing token and returns 200/401. **Best
  fit**: reuses the existing token system and the callback carries no media,
  so it honors "cloud is control-plane only."
- **External JWT** (`?jwt=...` in the query for HLS). MediaMTX itself warns
  query-param JWT leaks in logs/referrers and intends to disable it by
  default; keep TTLs short if used.

**The single biggest gotcha: MediaMTX ships wide open.** MEASURED (fetched
the default
[mediamtx.yml](https://raw.githubusercontent.com/bluenviron/mediamtx/main/mediamtx.yml)):
the default config has an `any` user (empty password) with publish/read/
playback on **all** paths, so out of the box anyone on the LAN can read and
publish any stream with no password. That config must never ship. API,
metrics, playback, and pprof endpoints are disabled by default (good); if
the API is enabled for remote config, bind it to localhost only, never the
hotspot interface. Change default board-OS creds (Pi `pi/raspberry`, Radxa
defaults), key-only SSH or off, firewall so SSH/API are not reachable from
other hotspot clients, and plan for auto-update so a sold box does not rot
on stale CVEs.

## 5. Self-host vs sell-our-own-hardware

### 5a. Self-host / run-it-yourself (free tier, tinkerers)

- **On the phone** (bundled ARM binary as subprocess, or Termux/proot for
  advanced users): zero extra hardware/power, but competes with capture +
  encode for CPU/battery and Android may background-kill it. ASSUMED.
- **Spare Pi / home server**: trivial for a technical user (binary +
  `mediamtx.yml`), but a home box is not in the car, so it only serves the
  parked-at-home case. The in-car case still needs something powered in the
  car.
- Pros: $0 hardware, no inventory/support/returns burden. Cons: setup
  friction, N environments to support, IPv6/cert config falls on the user.
  Good for the free tier, weak as a mass-market story.

### 5b. Sell a cheap dedicated box (SBC + MediaMTX + printed case)

Since remux is near-trivial, "cheapest that works" is really "cheapest
board that boots Linux with WiFi." Candidate boards, 2026 pricing (REPORTED
where cited, prices move with the late-2025 RAM-driven Pi increases and
stock):

| Board | CPU / RAM | Retail (2026) | Notes |
|---|---|---|---|
| **Pi Zero 2 W** | 4x A53 / 512MB | ~$15 MSRP, ~$18 street | Mature, tiny, WiFi. Ample for remux. Supply tight. |
| **Radxa Zero 3W 1GB** | 4x A55 RK3566 / 1GB | ~$15, up to ~$55 (8GB) | USB 3.0, WiFi 6, more RAM at same floor. Distributor-only, longer shipping, eMMC option. |
| **Orange Pi Zero 2W** | 4x A53 H618 | ~$20 | Cheap, more RAM options, software less polished. |
| **Pi 3A+** | 4x A53 / 512MB | ~$25, EOL ~Jan 2026 | Being discontinued, do not design around it. |
| **Pi 4 (2GB)** | 4x A72 / 2GB | ~$45 | Overkill and pricey for pure remux. |

Sources:
[Pi Zero 2 W](https://www.raspberrypi.com/news/new-raspberry-pi-zero-2-w-2/),
[Radxa Zero 3W](https://radxa.com/products/zeros/zero3w/),
[Pi 3A+](https://www.raspberrypi.com/products/raspberry-pi-3-model-a-plus/),
[Pi price-rise post](https://www.raspberrypi.com/news/supply-chain-shortages-and-our-first-ever-price-increase/),
[pcbsync pricing](https://pcbsync.com/raspberry-pi-cost/),
[cnx-software Zero 2 W vs Radxa](https://www.cnx-software.com/2021/11/01/raspberry-pi-zero-2-w-vs-radxa-zero-features-and-benchmarks-comparison/).

**Cheapest reliable pick:** Pi Zero 2 W (software maturity/support) or
Radxa Zero 3W 1GB (more RAM + WiFi 6 + eMMC option, better in a hot car) at
the same ~$15 floor if you can tolerate distributor sourcing. Both remux
1080p HLS with headroom.

Bill of materials, per unit (Pi Zero 2 W build; most non-board lines are
ASSUMED bulk pricing):

| Item | Part | Unit cost (USD) |
|---|---|---|
| SBC | Pi Zero 2 W | $18 |
| Storage | 16-32GB microSD (or soldered eMMC on Radxa) | $6 |
| Power | 12V-to-USB 5V/2.5A car adapter + short cable | $6 |
| Case | 3D-printed enclosure (filament + amortized print) | $2-4 |
| Misc | antenna/pigtail, packaging | $3 |
| **BOM subtotal** | | **~$35-37** |

Plausible retail: hobby margin ~$59-69; thin "we want everyone to have one"
margin ~$45-49. Flashing/QA/support/returns realistically add $10-15/unit
of hidden cost, so below ~$49 erodes fast. ASSUMED.

Power/thermal: 5V/2.5A off a 12V-to-USB car adapter is fine (Zero 2 W idles
~0.5-1W, peaks ~2-3W under remux). Prefer car 12V over powering off the
phone, since the phone is already hotspot + capture + encode. A sealed
printed case in a sun-baked car can cook a microSD; vent the case and
prefer eMMC. ASSUMED.

**Architectural guardrail:** whichever model, MediaMTX must be LAN-local
plumbing on the hotspot (phone or companion box), never a cloud VPS relay.
Routing media through a remote server would violate "cloud is control plane
only, never media" (the same bar that killed the session-6 cloud WebSocket
relay proposal).

## 6. Makefile / build provisioning sketch (only if the probe passes)

Vendor MediaMTX by download-and-checksum per arch, and ship our own locked
`mediamtx.yml` (not the wide-open upstream default). This is not yet added
to the repo Makefile on purpose, it belongs behind the row-C probe result.

```makefile
# --- MediaMTX provisioning (draft, not wired into the main Makefile yet) ---
MEDIAMTX_VERSION ?= 1.19.2
MEDIAMTX_BASE    := https://github.com/bluenviron/mediamtx/releases/download/v$(MEDIAMTX_VERSION)
VENDOR_DIR       := vendor/mediamtx
CONFIG_SRC       := config/mediamtx.yml           # OUR hardened config, in version control
MTX_TARGET       ?= linux-arm64                    # linux-{amd64,arm64,armv7,armv6}, darwin-arm64, windows-amd64
MTX_ARCH         := $(subst -,_,$(MTX_TARGET))
MTX_EXT          := $(if $(findstring windows,$(MTX_TARGET)),zip,tar.gz)
MTX_ASSET        := mediamtx_v$(MEDIAMTX_VERSION)_$(MTX_ARCH).$(MTX_EXT)

mediamtx-fetch:
	mkdir -p $(VENDOR_DIR)/$(MTX_TARGET)
	curl -fsSL -o $(VENDOR_DIR)/$(MTX_ASSET) $(MEDIAMTX_BASE)/$(MTX_ASSET)
	grep "$(MTX_ASSET)" checksums.txt | (cd $(VENDOR_DIR) && sha256sum -c -)  # pin, like the APK release lesson
	case "$(MTX_EXT)" in \
	  tar.gz) tar -xzf $(VENDOR_DIR)/$(MTX_ASSET) -C $(VENDOR_DIR)/$(MTX_TARGET) ;; \
	  zip)    unzip -o $(VENDOR_DIR)/$(MTX_ASSET) -d $(VENDOR_DIR)/$(MTX_TARGET) ;; \
	esac
	cp $(CONFIG_SRC) $(VENDOR_DIR)/$(MTX_TARGET)/mediamtx.yml

mediamtx-all:
	for t in linux-amd64 linux-arm64 linux-armv7 linux-armv6; do $(MAKE) mediamtx-fetch MTX_TARGET=$$t; done
```

Notes: pin sha256 checksums (mirrors the existing "verify the exact file
before release" lesson); keep our `mediamtx.yml` auditable in the repo with
authenticated users and a single locked publish path; for a companion
device the fetched arm64 binary + config is the whole payload (install via a
tiny systemd unit); the in-APK path, if ever attempted, needs the
executable-permission caveat from §1.

## 7. Open items / honest unknowns

- **MSE-`<video>`-in-Drive on Chromium 140** is untested. This is the
  linchpin (see §0). Run `video-test.html` row C parked vs Drive in the car.
- No MEASURED pure-remux 1080p footprint on target hardware; benchmark
  before committing to a board.
- Exact 2026 street prices move with the RAM-driven Pi increases and stock;
  reconfirm the BOM at purchase time.
- Whether Let's Encrypt rate limits bite a per-device AAAA cert fleet at
  scale (needs a real issuance test) if option 4b is chosen.
- Confirm the `darwin_amd64` asset and exact v1.19.2 filenames on the
  releases page before hard-coding them in the Makefile.

## 8. Recommendation in one paragraph

Do not build the pipeline yet. First run `video-test.html` in the car
(row C, parked vs Drive) to settle whether MSE `<video>` survives Drive. If
it does not, HLS is a parked-media-only feature and should be scoped as
such. If it does, add MediaMTX as **LAN-local plumbing** (phone subprocess
or a ~$49 Pi Zero 2 W / Radxa Zero 3W companion box), serve HLS on a
**plain-HTTP IPv6-literal page** (or an own-wildcard-cert device if the
unified `veh.modev.be` shell must stay HTTPS), authenticate every read via
MediaMTX **external HTTP auth reusing the existing pairing token** on a
per-session path, and never ship MediaMTX's wide-open default config. Keep
the WebCodecs-to-canvas path as the live low-latency mirror regardless;
HLS is at best a second, higher-latency tier for passive/parked video.
