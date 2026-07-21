# vehplayer - Differentiator Features vs Android Auto / CarPlay

> Session 10 continuation. Triggered by two things at once: the founder asked
> Gemini for a second opinion on architecture/roadmap (Google/Android being
> "their thing"), and separately asked for research into features that let
> vehplayer "pronk" (show off) with something CarPlay/Android Auto
> structurally cannot do, plus a refreshed market/positioning pass. This doc
> covers both: a fact-check of Gemini's proposal, and the AA/CarPlay
> competitive-differentiation research. Evidence tags per house rule:
> MEASURED (verified on our own car/repo), REPORTED (a public source says
> so, cited), ASSUMED (our inference, flagged).

## 0. Why this doc exists, and what it is not

This is not a build spec. Per the founder's own instruction, nothing here
gets built until it has been "grondig onderzocht" (thoroughly researched)
first - this doc is that research pass, and a build-priority list comes
after, not inside, this document. It is a companion to
`COMPETITIVE_REASSESSMENT.md` (which covers direct Tesla-mirroring
competitors, TeslaMirror/Tesla Display) and `GROWTH_SAAS.md` §5 (the "moat"
section), not a replacement for either - the AA/CarPlay angle here is a
different competitive frame than "other Tesla mirroring apps."

## 1. Gemini second-opinion review: what held up, what didn't

The founder asked Gemini (a second AI, chosen because Google/Android is
"their thing") for architecture/roadmap ideas. Fact-checked against this
repo's own MEASURED findings and documented history:

- **Wrong, and backwards**: Gemini's proposal states Tesla's browser
  "supports no IPv6" and that this killed WebRTC. Both are incorrect per
  our own MEASURED research. `ARCHITECTURE.md` §7 (session 7, real Model 3):
  Tesla's browser refuses **RFC1918 IPv4**, and **IPv6 GUA is the one tier
  that actually works** on current Android. WebRTC did not die either: the
  session 8-9 WebRTC probe got a real **PASS** in the car (`NEXT_SESSION.md`,
  session 9). Gemini was reasoning from general knowledge, not this repo's
  measured history.
- **Wrong on the current pipeline**: the proposal describes "mediamtx
  compiled into the APK" as the shipping solution. `MEDIAMTX_HLS_RESEARCH.md`
  (this session) already found that bundling MediaMTX inside the APK is
  fragile/unsupported (Android's executable-permission restrictions on app
  storage; the MediaMTX maintainer's own guidance is against it). HLS is
  also not "the current solution" - it's a candidate for **passive media**,
  per this session's own in-car measurement (progressive `<video>` smooth,
  hls.js/MSE choppy). The live-mirror primary stays WebCodecs-to-canvas.
- **Rejected, again, for the same reason as session 6**: the proposed cloud
  relay/tunnel (§3A of the proposal: phone opens a tunnel to a cloud
  service over 443, car connects to `https://auto.onsdomein.com`, SaaS
  tiers around it) is the exact idea `VEHPLAYER_Foundation.md` (line ~190)
  and `NEXT_SESSION.md` (session 6) already considered and explicitly
  rejected: it violates "cloud is control plane only, never media"
  (`cloud/src/index.ts`'s own header comment: "Cloud never sees a video
  frame"). Its stated justification ("Tesla blocks local IP connections")
  is also factually wrong per the IPv6 point above - the local reachability
  problem is already solved without a cloud relay, so the justification for
  building one evaporates. **Do not build this.** Any future architecture
  proposal that routes media through cloud infrastructure needs to clear
  this bar explicitly, per the standing rule.
- **Already built**: the proposal's "AppWidgetHost for lighter widgets"
  suggestion already exists (`PinnedWidgetHost.kt`, session 6/8) - generic
  native widget hosting for things that won't get bespoke integration
  (Home Assistant, weather, etc.), exactly as described.
- **Real idea, needs its own feasibility research before any code**: the
  proposal to launch Waze/Google Maps onto a `MediaProjection` virtual
  display via `ActivityOptions.setLaunchDisplayId()`. Researched this
  session (§5 below): **verdict is do not build.** Blocked by
  `signature|privileged` Android permissions no store app can hold, only
  clearable via Shizuku with real but unverified rendering risk, and both
  Waze's and Google Maps Platform's own Terms of Use prohibit this exact
  kind of unauthorized integration. Full writeup below.
- **Genuinely useful, and convergent with research already underway**: the
  USB dongle idea (§3B of the proposal - a cheap board that broadcasts its
  own Wi-Fi hotspot, phone and car both join it, stream stays local).
  This overlaps directly with `MEDIAMTX_HLS_RESEARCH.md`'s Pi Zero 2 W /
  Radxa Zero 3W hardware research from earlier this session. One
  correction needed: a dongle broadcasting its own hotspot does not
  automatically dodge Tesla's RFC1918 restriction just by being a separate
  device - if it hands out ordinary private (192.168.x.x) addresses, it's
  the identical problem on different hardware. The dongle would still need
  to solve reachability properly (its own IPv6 GUA/ULA scheme, or a
  deliberately chosen non-RFC1918 addressing plan), with the same rigor as
  `ARCHITECTURE.md` §7's tier ladder, not assumed away. The genuine win: a
  dongle removes the current design's dependency on the user's specific
  carrier supporting IPv6 tethering delegation (the real session-7 blocker
  was a test SIM with zero IPv6 GUA on cellular that night) - a
  self-contained dongle could guarantee its own addressing regardless of
  carrier. Worth a dedicated reachability-focused follow-up, not assumed
  solved by "it's a separate network."

## 2. What Android Auto structurally cannot do (2026)

REPORTED, primary sources: `developer.android.com/design/ui/cars/guides/
templates/overview`, `developer.android.com/docs/quality-guidelines/
car-app-quality`, `developer.android.com/design/ui/cars/guides/
foundations/customize-app`, Android Developers Blog (May 2025, May 2026).

Third-party apps compose screens from a fixed template set only:
`ListTemplate`, `GridTemplate`, `SectionedItemTemplate`, `PaneTemplate`,
`MessageTemplate`, `LongMessageTemplate`, `SearchTemplate`,
`SignInTemplate`, `TabTemplate`, `NavigationTemplate` (nav apps only),
`PlaceListMapTemplate`/`MapWithContentTemplate` (nav/POI/weather only),
`MediaPlaybackTemplate` (media apps only, added 2025/2026). The May 2026
"unifying platforms" post added a few more components (Spotlight Section,
Condensed Items, Chips) but the system is still template-only, no custom
layout escape hatch.

Explicit MUST-NOT rules straight from Google's own quality guidelines:
- **[SA-1]**: no animated graphics/video on screen (narrow parked-canvas
  exception).
- **[IU-1]**: no arbitrary images, only a single static background image,
  drawer icons, and driving-decision/lane-guidance graphics.
- **[DD-2]**: a video app's UI must not be visible while driving at all,
  audio must pause and can't resume while driving.
- **[PE-1]**: on Android Automotive OS, apps get their own custom UI only
  for setup/settings/sign-in, and only while parked - the structural core
  of the whole system.
- **[NF-2]**: nav apps draw only the map itself; the search bar/action
  strip/buttons are host-rendered, not app-rendered.
- **[AC-1]**: task completion capped at five screens.
- **[PC-1]/[AN-1]**: video, games, and general browsers are named
  categories that aren't permitted app types on the platform at all.
- **[TH-1]**/`customize-app`: theming capped at light/dark plus "4 standard
  colors, or up to 2 custom accents." No custom fonts, no CSS, no custom
  shapes.
- Weather apps specifically: "MUST NOT use weather animations on map
  surfaces when user is driving," capped at 5 map annotations and 3 legend
  colors while driving.

**Bottom line**: no custom layouts, no arbitrary images/video, no games, no
browsers, no freeform content ever while driving; a truly custom UI exists
only in the parked, Automotive-OS setup/settings state.

## 3. What Apple CarPlay structurally cannot do (2026)

REPORTED (secondary confidence on template specifics - Apple's JS-rendered
docs and the CarPlay Developer Guide PDF weren't directly fetchable this
session, so template detail below comes from developer forum threads and
secondary write-ups, not a directly-read primary spec; verify before this
goes in front of legal or paid marketing).

Third-party CarPlay apps get one Apple-granted entitlement category
(Audio, Communication, Navigation, Parking, EV Charging, Fueling, Quick
Food Ordering, Driving Task, plus a 2026 "voice-based conversational app"
category) - a Driving Task entitlement blocks also holding Communication
on the same app, and apps get a Maps entitlement or Audio, not both.
Templates: `CPListTemplate`, `CPGridTemplate`, `CPMapTemplate`,
`CPNowPlayingTemplate`, `CPInformationTemplate`, `CPPointOfInterestTemplate`,
`CPSearchTemplate`, `CPVoiceControlTemplate`, `CPTabBarTemplate`,
`CPAlertTemplate`, `CPActionSheetTemplate`; navigation stack depth is
capped at two templates (root plus one push) for most categories.

**Real developer complaints (primary, dated Apple Developer Forum threads)**:
thread 726487 quotes a developer hitting an undocumented `CPGridTemplate`
layout that doesn't match Apple's own documentation ("now it can fit up to
8 images in a row" when the doc promises a fixed two-row balance); thread
797334 hits an undocumented 9-button cap; thread 798894 finds inconsistent
list item-count limits (12 in one report, 24 items/50 sections in another)
that aren't documented anywhere Apple publishes. These are developers
hitting the wall directly, the sturdiest citations found this session.

2026 updates (REPORTED, secondary sources): CarPlay Ultra (deeper
OS/cluster integration, still a slow Aston-Martin-first rollout) and iOS
26.4 opening CarPlay to third-party AI chatbots and apps like WhatsApp/
Google Meet - neither relaxes the template system itself; CarPlay Ultra
reportedly still excludes YouTube/Netflix-style apps.

**Bottom line**: no custom UI outside enumerated templates, one entitlement
category per app, undocumented and inconsistent hard item/column limits
even Apple's own forum can't clarify, no general browser, no video/game
category at all.

Honest gap: developer-side frustration evidence is solid (forum threads
above); end-user-side "I wish my dashboard did X" evidence is thin - one
9to5Google piece wishing for AA split-screen multi-app support was the
best found. Reddit could not be searched from this sandbox (fetch blocked);
if user-facing marketing needs that texture, it needs a follow-up pass from
an environment that can reach reddit.com.

## 4. Concrete features vehplayer can build that AA/CarPlay structurally cannot

Framed against this session's own MEASURED finding: plain `<video>` **does**
play in Drive on the Tesla browser, and WebCodecs-to-canvas plus arbitrary
CSS/JS is the live-mirror path - vehplayer is not Park-gated the way
AA/CarPlay explicitly are for anything visual ([DD-2], [PE-1]). Each item
names the exact rule it violates. Effort is ASSUMED, not measured.

1. **Fully custom-themed, animated dashboard chrome** (fonts, motion, brand
   skinning). Violates [TH-1] (4-color cap) and [SA-1] (no animation).
   Effort: small - CSS/web-font work on the existing `CarDashboardActivity`
   render surface. **This is also the direct answer to the founder's own
   UI feedback** (see §7 below): "not modern, feels landscape-retrofit" is
   exactly what unrestricted theming/motion fixes, once designed properly.
2. **Rich, inline-media messaging** (images/GIFs inline in-thread, not just
   TTS'd text). Violates [IU-1] and AA's text-plus-actions-only
   `MessageTemplate`. Effort: medium - `MessagesOverlayView`/
   `VehplayerNotificationListenerService` already exist as a base. Needs
   the Park/Drive gating discussed in §6.
3. **Audio-reactive Now Playing visualizer** (canvas/WebGL, album-art
   particles, lyrics scroll). Violates [SA-1]. Effort: small-medium, pure
   front-end against metadata `NowPlayingFragment` already surfaces.
4. **Simultaneous multi-pane dashboard** (map + messages + now-playing,
   user-arranged, one screen). Violates AA's `MapWithContentTemplate`
   (fixed combinations only) and CarPlay's two-template stack cap plus
   one-entitlement-per-app rule - no single AA/CarPlay app can even show
   its own map next to a rival app's messaging pane. Matches a real
   tech-press wish (9to5Google, §3). Effort: medium-large, real
   information-architecture work (arrangeable widget layout, state sync),
   not just styling. **Also a direct answer to the "use more of the big
   screen" feedback** (§7).
5. **Rich destination-search cards** (photo/rating/hours, Places-style).
   Violates [IU-1] and AA/CarPlay's text-only search/list templates.
   Effort: medium, front-end against a places API on top of the existing
   destination-search overlay.
6. **True mirrored access to any phone app, integrated or not.** The
   single biggest structural gap: AA/CarPlay only surface apps that ship
   an explicit Car App Library/CarPlay entitlement; an app with zero car
   integration cannot appear at all, regardless of its own UI quality.
   vehplayer's mirror architecture has no such gate. Effort: already
   built (`CaptureService`/`H264Encoder`) - the deliverable here is
   positioning/marketing language, not new code, stating this explicitly
   as an AA/CarPlay-impossible capability rather than just "mirroring."
7. **Live animated weather radar** (real precipitation-loop motion, storm
   tracking). Violates AA's own named weather-animation ban outright, one
   of the most explicit restrictions found in this research. Effort:
   medium, radar-tile API plus canvas animation.
8. **Arbitrary third-party web embeds as dashboard widgets** (smart-home
   control, package tracking, EV-network maps, stock/crypto tickers -
   anything iframe-able). Neither platform has a concept of embedding an
   arbitrary third party's web widget; both gate apps into one fixed
   category/entitlement. Effort: medium for a basic widget-slot framework,
   scales with per-widget curation/sandboxing.

## 5. Waze/Google Maps on a virtual display: feasibility verdict

**Do not build.** Researched this session, full chain:

- Launching a **third-party app's** Activity onto a **trusted** virtual
  display requires `ADD_TRUSTED_DISPLAY` (for the display) and
  `ACTIVITY_EMBEDDING` (for the cross-app launch), both AOSP
  `signature`/`signature|privileged` permissions - a store-distributed APK
  cannot acquire these by declaring them, full stop, regardless of code.
  REPORTED, AOSP manifest source.
- Real shipping tools that do this (AG Displays, SimpleVirtualDisplay) all
  route around the wall via **Shizuku** (shell-UID privilege), the same
  mechanism vehplayer already depends on for Power Mode input injection.
  Technically plausible only in a Shizuku-required build.
- Whether Waze/Google Maps even render correctly once forced onto a
  secondary display is unverified; scrcpy's own docs (a tool using the
  identical mechanism) warn of blank screens and inconsistent behavior
  across Android versions for much simpler apps (VLC, file managers).
- Both Waze's Terms of Use and Google Maps Platform's Terms of Service
  separately prohibit this exact kind of unauthorized integration/embedding
  without a partnership; Waze's own 2025 instrument-cluster feature ships
  through an official, vehicle-brand-gated partnership (BMW, Ford,
  Volvo/Polestar), not an open integration path, reinforcing that Google/
  Waze treat "nav content on a second screen" as partnership-gated, not a
  gray area.
- Making this work would also force every user wanting live nav-in-
  dashboard to install and pair Shizuku, a real regression against
  `GROWTH_SAAS.md`'s "free tier is the product, no nag, no friction"
  principle.

**Recommendation instead**: deep-link/intent-launch Waze or Google Maps to
start turn-by-turn on the phone's own screen with voice guidance (fully
sanctioned, no special permission needed), or keep building vehplayer's
own in-app navigation (already the Pro-gated roadmap item, `GROWTH_SAAS.md`
§4).

## 6. Ethical/legal gating: what must stay Park-only

- **Games and general web browsing**: AA's own guidelines name these as
  categories that must not exist in the driving-safe template set
  ([AN-1]/[PC-1]), tracking NHTSA's Visual-Manual Driver Distraction
  Guidelines (Federal Register 2013-09883 for OEM systems, 2016-29051 for
  portable/aftermarket devices - the more directly applicable category
  here), which cap any single visual-manual task at 2 seconds per glance,
  12 seconds cumulative. A game or general browser cannot fit that budget.
  Any such feature needs a hard, vehicle-speed/gear-checked Park-only gate
  before shipping, not a UI-level assumption.
- **Long-form/entertainment video (movies, YouTube, social feeds) while in
  Drive.** This session's MEASURED finding that `<video>` plays in Drive is
  a capability, not a green light. AA explicitly forbids a video app's UI
  being visible while driving ([DD-2]) for exactly this reason. Gate any
  long-form video strictly to Park (vehicle-state-checked), and keep
  marketing copy from reading as "watch movies while driving."
- **Rich inline messaging (§4.2) and rich search cards (§4.5)** are
  structurally allowed by the Tesla browser but land in the same NHTSA
  glance-time target zone. Recommend a simplified, large-text, TTS-first
  mode whenever the car is in Drive regardless of what the browser
  technically permits, matching the spirit of AA's own messaging-template
  restraint rather than "we ignore why AA/CarPlay restrict this."
- **Regulatory context**: Tesla is under active NHTSA scrutiny on
  distraction-adjacent issues (FSD investigations opened October 2025,
  REPORTED). Different feature, same signal: be conservative and explicit
  about Park-gating rather than treating the Tesla browser's permissiveness
  as tacit regulatory approval.

## 7. UI feedback (founder, this session): not modern, underuses the screen

Founder's own read, reviewing the current dashboard: it "doesn't feel
modern," reads as landscape-retrofit rather than designed for a genuinely
large screen, and a Tesla display is big enough to do meaningfully more
than what's currently shown.

Confirmed by inspecting the current layout
(`android/app/src/main/res/layout/activity_car_dashboard.xml`): a hero card
(60% width) plus a fixed 3-tile column (34% width) inside 28dp padding -
a conventional two-column, phone-app-shaped layout rather than one
designed edge-to-edge for a 15-17" display. Not something to reflow blindly
right now; this needs a real design pass (with actual screenshots, ideally
a design-focused session) rather than an ad hoc fix.

**This is the same project as §4.1 and §4.4 above, not a separate one.**
Unrestricted theming/animation (4.1) and an arrangeable multi-pane layout
that actually uses the screen's real estate (4.4) are the concrete
technical answer to "feels dated, underuses the screen." Recommend
sequencing a design pass and those two features together rather than
treating "modernize the UI" as its own undefined task.

## 8. Recommendation / what's actually next

Nothing in this document is authorized to build yet - it's the research
pass the founder asked for. Suggested priority order for whoever picks this
up next (unranked by effort, ranked by leverage):

1. A proper design pass on the dashboard (§7), scoped together with §4.1
   (theming/animation) and §4.4 (multi-pane layout) - highest visible
   impact, and it's the one the founder already flagged unprompted.
2. Positioning/marketing language using §4.6 (mirror-anything) and the
   AA/CarPlay template-restriction research (§2/§3) explicitly - this is
   copy work, not engineering, and can happen in parallel with anything
   else.
3. Any of §4.2/4.3/4.5/4.7/4.8 as discrete, independently shippable
   features once the design pass has a real visual system to build them
   into, each with its §6 gating applied where relevant.
4. The dongle reachability idea (§1) as its own focused spike, reconciled
   with `MEDIAMTX_HLS_RESEARCH.md`'s hardware research, if/when hardware
   becomes a priority.
5. Do not revisit: cloud relay (§1), Waze/Maps virtual-display mirroring
   (§5) - both have a clear, evidenced "no" from this session.
