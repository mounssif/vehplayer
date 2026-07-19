# vehplayer - Foundation Document

> Purpose: single source of truth for this project. Every conversation,
> deliverable, and decision should be consistent with this document. Update
> it when decisions change. Sections marked (decided) are settled; sections
> marked OPEN are R&D gates, not preferences.
>
> **This replaces `VEPLA_Foundation.md` (retired).** That document was
> internally versioned "v2" and repeatedly deferred load-bearing sections
> to "unchanged, see v1 §N" - but no v1 document ever existed anywhere in
> this repo (found session 4, unresolved across sessions 4-5). Rather than
> keep citing a file that was never written, this document states the
> actual current decisions directly. Where a "v1" section's original
> wording genuinely isn't recoverable from anywhere else in the corpus,
> that's noted explicitly below instead of inventing plausible-sounding
> history to fill the gap.

## 1. Name & Origin

**vehplayer.** VE-hicle + PLA-yer. No "Tesla", no "Auto", no "Play" as a
standalone trademark-adjacent word.

- Naming rule (load-bearing for §13): "Android Auto" and "CarPlay" appear
  only in *descriptive comparison* copy ("an alternative to..."), never in
  the product name, app title, or store listing *title*. Relaxed
  deliberately for store long-description/SEO copy (`MARKET_AND_PRICING.md`
  §5.1) - nominative use to describe compatibility is standard practice and
  how competitors are actually found, since "android auto tesla" is the
  real search term. The rule that never bends: never imply endorsement,
  never use Apple's or Google's logos.
- **"VEPLA" is retired**, not just a working codename anymore. An unrelated
  Swedish B2B app (verksamhetsplatsen.se, construction/project-management
  software) already ships under that name. Different Nice class, probably
  not a hard legal blocker, but the repo carried two names for a while and
  that already caused real confusion (this phantom-v1 problem is a direct
  symptom). `vehplayer` is now the name everywhere: Gradle project,
  Android package `app.vehplayer.android`, this document, `GROWTH_SAAS.md`,
  the market docs.
- Domain: `veh.modev.be` for now (a personal/dev domain, reads as
  hobbyist - `COMPETITIVE_REASSESSMENT.md` §7.2). A short dedicated domain
  (4-8 characters, no hyphens) is planned before launch - **[MENS]**, not
  yet registered as of this writing, do not treat any specific candidate
  as decided until the founder confirms a purchase.
- Trademark/domain clearance: still TODO before anything ships. See
  `legal/` once that directory exists (`MARKET_AND_PRICING.md` §8, §9.4).

## 2. One-liners

Primary, revised per `COMPETITIVE_REASSESSMENT.md` §5.1 (replaces the
original "Your apps. Your car screen." once real competitive research
showed every mirroring competitor already claims something equivalent):

> **"A car dashboard for your Tesla. Not a phone on a big screen."**

Supporting lines:
- "Navigation, music and calls, laid out for a car screen. Your phone
  stays in your pocket."
- "Nothing leaves the car. No cloud, no relay, no account."
- **"Nothing Google ships can turn this off."** Earned, not aspirational:
  the Android-Auto-protocol camp (TesAA/WebAA) broke on a live Google Play
  Services update while this document was being written. vehplayer doesn't
  touch that protocol, so that failure mode doesn't exist for it - the
  strongest line in the whole positioning because a competitor can't copy
  it without rewriting their architecture.

Anti-claim rule holds: vehplayer delivers the *experience*, never claims
to *be* Android Auto or CarPlay.

## 3. Product Vision

**vehplayer is a car dashboard, and mirroring is plumbing** - the settled
category definition from `COMPETITIVE_REASSESSMENT.md` §3. Every
competitor found in the market research (Tesla Display, TeslaMirror, Car
Cast, TesAA/WebAA) is a *mirror utility*: they take the phone screen and
put it on the car screen, full stop. vehplayer's `CarDashboardActivity`
(session 4+) is a purpose-built, car-optimized interface that happens to
be *delivered* via mirroring because Android has no API to embed a foreign
app's UI the way Android Auto/CarPlay do - but the product is the
dashboard, not the delivery mechanism. This reframes the competitive
picture entirely: the mirroring feature is a commodity every competitor
fights over and should be free (`MARKET_AND_PRICING.md` §6.2); the
dashboard is the only thing worth charging for.

Core design principles, in effect throughout this project's architecture
even though a single canonical "Fast/Simple/Local/Independent/Honest" list
was never centralized in a recoverable v1 doc - restated here from how
they've actually been applied session over session:
- **Fast**: the stated latency budget is glass-to-glass ≤120ms perceived,
  touch round-trip ≤80ms (§7 below).
- **Simple**: a two-minute setup flow (`MainActivity`'s numbered steps),
  one settings toggle for the free-tier touch path, no developer options
  or Shizuku required by default.
- **Local**: the data plane is 100% local by design (§6, §13) - screen and
  audio bytes never touch a server, which is also why a Cloudflare relay
  was rejected this session for the RFC1918 connectivity fix
  (`NEXT_SESSION.md`, session 6) even though it would have been the
  simpler build.
- **Independent**: no dependency on the Android Auto/CarPlay protocol
  (§4 Lesson 1) - this is the single load-bearing architectural bet the
  whole moat rests on.
- **Honest**: status messaging reflects real state rather than optimistic
  guessing - `VpnReachabilityService`'s doc comments explicitly distinguish
  "evidence-backed" from "real-hardware-confirmed" rather than claiming
  more certainty than exists, and `MainActivity` shows specific failure
  messages instead of an indefinite spinner (a direct response to the
  competitor one-star-review pattern "I can't even get the screen share to
  start", `COMPETITIVE_REASSESSMENT.md` §4.4).

## 4. The Autopsy

What's actually recoverable from the rest of this corpus (GROWTH_SAAS.md,
NEXT_SESSION.md's scope-creep tripwires), stated directly rather than
behind a "see v1" pointer:

- **Lesson 1 (protocol independence)**: TeslAA/WebAA died when Google
  tightened one Android-Auto-protocol validation check it controlled -
  confirmed happening a *second time*, to the same product line, live
  during this session's own market research
  (`MARKET_AND_PRICING.md` §4, Camp A). This is the concrete case for
  never depending on that protocol; it is not a hypothetical risk being
  hedged against, it is a lesson that has now repeated itself in real
  time. Scope-creep tripwire: any dependency that reintroduces
  Android-Auto/CarPlay protocol code is a hard no (`NEXT_SESSION.md`).
- **Castla's ceiling (GPL boundary)**: Castla's real adoption ceiling is
  its Shizuku requirement - a single UX point of failure, and the reason
  vehplayer's free-tier default is AccessibilityService `dispatchGesture`
  rather than Shizuku (§6b item 2). Separately, Castla is GPL-3.0
  licensed: architecture *ideas* are fair game to learn from, but no code
  is ever copied or closely modeled from it (`NEXT_SESSION.md` scope-creep
  tripwires) - this is a hard legal/licensing boundary, not a style
  preference.
- **What's genuinely not recoverable**: the original Foundation
  enumerated "Lessons 1-5" as load-bearing constraints, but only lesson 1
  and the Castla/GPL lesson above have left a clear trace anywhere else in
  this corpus. If the specific historical detail behind lessons 2-5
  matters for a future decision, that needs founder input to reconstruct
  properly rather than this document inventing plausible-sounding content
  to fill the gap - flagged here as an open item, not silently dropped.

## 5. Competitive Position

Superseded by `docs/COMPETITIVE_REASSESSMENT.md` (19 July 2026) and
`docs/MARKET_AND_PRICING.md` §4 - read those directly rather than a
summary here, since the category split (Camp A: dying Android-Auto-
protocol emulators; Camp B: alive screen-mirroring competitors) and the
per-competitor detail changes with the market and should stay live in
those documents, refreshed as the landscape moves, not duplicated and
allowed to drift here.

The one-paragraph version: mirroring is a commodity fought over by four to
five alive-or-dying competitors, none of which ship a purpose-built
dashboard (§3); the real addressable market is roughly 4M browser-capable
Teslas, with a realistic year-one install ceiling in the low thousands to
low tens of thousands (benchmarked against TesAA's 10-20k lifetime
installs after 4.5 years); the TesAA/WebAA outage happening live during
this research is a real, time-limited launch window for a stranded,
findable, paying-intent user base (`MARKET_AND_PRICING.md` §4.0).

## 6. Technical Decisions - Settled

No Android Auto/CarPlay protocol emulation. Native Kotlin app. Shared
layer = web client + protocol. Video pipeline: `MediaCodec` → WebSocket →
WebCodecs → **canvas** (not a `<video>` element) as the primary and only
path confirmed to survive Tesla's Drive-mode `<video>` suppression -
`ARCHITECTURE.md` §2 has the full detail and the reasoning for why
`mseFallback.ts`'s `<video>`-element-based muxer is deprioritized, not
simply unfinished (`MARKET_AND_PRICING.md` §3). WebSocket transport. Data
plane 100% local, control plane in the cloud (`cloud/src/index.ts`:
"Control plane only, never media"). Car browser support target
MCU2/MCU3+. iOS reality = view-only Lite, a later phase, not attempted in
the current build.

## 6b. The Three Gate-1 Questions - decided as architecture

Tesla firmware changes several times a year (Failure Mode #2), so "pick
the one true answer once" produces an architecture that breaks on the
next update. Decision: **the fallback ladder itself is the shipped
architecture**, not a temporary measurement scaffold. Gate 1's job is to
rank and calibrate paths the app already knows how to walk, not to choose
a single path.

1. **Reachability (decided): ship all three tiers behind one automatic
   probe, always.**
   - On first connect, and silently on every reconnect, the phone tries in
     order: (a) IPv6 GUA on the hotspot interface, no VpnService; (b)/(c)
     a `VpnService`-assigned `100.64.0.0/10` CGNAT address (implemented
     session 6, `VpnReachabilityService.kt`, evidence-backed against a
     real shipping competitor's identical technique - see
     `NEXT_SESSION.md` session 6 for the full derivation).
   - A cloud relay was considered and explicitly rejected (session 6): it
     would violate §6's "control plane only, never media" principle even
     without persisting anything. This is the standing answer any future
     connectivity proposal needs to clear before it's built.
   - Consequence for UX: at most one Android VPN consent dialog, shown
     only if tier (a) fails. Copy handles both paths honestly.
   - **Not yet real-hardware-confirmed** (session 6): the address
     assignment and URL-building logic is verified as far as the emulator
     allows; whether a real second device (the car) on the phone's real
     hotspot can route to the assigned address needs a real Tesla.
2. **Touch injection (decided, the shipped default, not a contingency).**
   - Free tier default: **AccessibilityService `dispatchGesture`** on the
     mirrored main display. One settings toggle, phone screen stays on, no
     developer options, no Shizuku.
   - Power Mode (Pro, opt-in): Shizuku, unlocking virtual display
     (screen-off) + faster injection. A monetized upgrade, not a hidden
     requirement - the wedge against Castla (§4).
   - View-only remains the last-resort floor, a graceful-degradation
     state, not a shipped mode.
3. **Audio route (decided).**
   - Default for all users: **car Bluetooth A2DP.** Zero code path, works
     day one, acceptable latency for music.
   - Pro toggle: **browser AudioWorklet route** (AAC/PCM over the WS audio
     channel), target <80ms added latency. TeslaMirror's "lossless audio,
     up to 96kHz/32-bit, positioned against Bluetooth quality" is a
     sharper framing to borrow for this than "low latency" alone
     (`COMPETITIVE_REASSESSMENT.md` §4.1).

**Net effect**: the reachability and touch kill criteria are narrowed from
"kill the product" to "kill/reprice a specific tier or the Power Mode
upsell." The only hard kill criterion left: if **no** reachability tier
survives current firmware, or `dispatchGesture` is impossible on **any**
display mode.

## 7. Performance Doctrine

Latency budget: glass-to-glass ≤120ms perceived, touch round-trip ≤80ms.
Full detail in `ARCHITECTURE.md`.

## 8. Legal, Licensing & Store Strategy

GPL hygiene (§4), the trademark naming rule (§1), and dual distribution
(Play Store + first-party signed APK, §13) all remain load-bearing.
`MARKET_AND_PRICING.md` §7 found two live Play Store policy conflicts that
need resolving before any store listing:
- **AccessibilityService disclosure**: Play requires a prominent in-app
  disclosure with affirmative consent, separate from the privacy policy,
  enforcement tightened 28 January 2026. Never declare
  `isAccessibilityTool=true` - vehplayer is not an assistive product, and
  a false declaration risks account termination.
- **`REQUEST_INSTALL_PACKAGES`/self-update pipeline**: prohibited under
  Play policy for updating the app itself. The session-3 update pipeline
  (`UpdateChecker.kt`/`ApkInstaller.kt`) is genuinely good engineering for
  sideload distribution but cannot ship in a Play Store build - needs a
  `playRelease`/`sideloadRelease` build-flavour split.

See `legal/` (once created, `MARKET_AND_PRICING.md` §9.4) for the privacy
policy, terms, processing register, and trademark note this requires.

## 9. Technical Decisions - Multi-Brand Abstraction

The wire protocol and web client are designed sender-agnostic (§6,
`ARCHITECTURE.md` §4) to accept iOS or a desktop harness. The same
discipline extends to the *car* side, because §13's growth thesis assumed
Tesla wasn't a hard dependency - though `MARKET_AND_PRICING.md` §2.3 found
that assumption weaker than originally stated (Lucid added CarPlay and
left the addressable market; GM's Google Built-in has no usable general
browser and isn't addressable either). Tesla is closer to *the* market
than *a* market for now.

- **Decided: a `CarProfile` config object** (browser engine + version
  floor, viewport reporting quirks, RFC1918 policy, WebCodecs/MSE support,
  known input event fidelity) instead of Tesla-specific conditionals
  scattered through the codebase. Tesla MCU2/MCU3 are the first two
  entries.
- Costs nothing while only one profile exists, keeps the door open if a
  second real car profile becomes viable later. Not a near-term growth
  lever - drop any "diversification is imminent" framing from anything
  investor-facing until a second profile is MEASURED, not just modeled.
- Explicit non-goal for now: do not chase a second car brand before Tesla
  is daily-drivable end to end (a real `/go` round trip in an actual car,
  still not done as of this writing).

## 10. Failure Modes & Kill Criteria

- **#1 (Tesla ships native AA/CarPlay support)**: mitigated structurally
  by protocol independence (§4 Lesson 1) - Tesla-specific revenue erodes,
  the company doesn't, because the product was never architecturally a
  "Tesla app" (`GROWTH_SAAS.md` §5).
- **#2 (a Tesla firmware update degrades a reachability/touch/audio
  tier)**: mitigated by the fallback ladder itself being the shipped
  architecture (§6b) - a single firmware change degrades a tier, not the
  product. Live evidence this is a real, recurring risk, not a
  hypothetical: Tesla 2026.20 added Parental Controls toggles for
  Browser/Theater/Arcade, and third-party trackers expect more granular
  in-motion app restriction in future updates
  (`MARKET_AND_PRICING.md` §3 item 4) - exactly this failure mode,
  arriving on schedule. The remote-config kill-switch design
  (`cloud/src/remoteConfig.ts`) is the right hedge for this and should be
  finished rather than left at `REPLACE_ME`.
- **#3 (Play Store rejects the AccessibilityService declaration)**:
  mitigated by dual-channel distribution (§8, §13) - the first-party
  signed APK is insurance, not a fallback.
- Reachability/touch kill criteria narrowed per §6b: only total failure of
  all tiers, or `dispatchGesture` failing on every display mode, kills the
  product outright.

## 11. Roadmap with Gates

Gate 0-6 structure. Gate 1's deliverable is MEASURED calibration of the
§6b ladder (still pending real-car verification, the single most
important open item across every session's notes). Gate 5 (company layer)
is expanded by `docs/GROWTH_SAAS.md`.

## 12. Working Agreements (Claude ↔ founder)

Dutch conversation, English deliverables, no em-dashes, no time estimates,
Gate-scope pushback, evidence tags (MEASURED/REPORTED/ASSUMED) on every
browser/market claim, session-end doc refresh (this document being
exactly that, session 6). New agreement from session 6's market research:
**`[MENS]` facts - purchases, registrations, payments, signatures - are
documented as fact only after the founder confirms them**; until then they
stay noted as an intention with an open status, not folded into a facts
table as if already true (a lesson learned the hard way on an unrelated
project's domain-purchase mixup, worth applying here proactively).

## 13. Growth & SaaS Strategy

Full detail lives in `docs/GROWTH_SAAS.md`, revised per `COMPETITIVE_REASSESSMENT.md`/
`MARKET_AND_PRICING.md` findings (§4, §6 of those documents supersede the
prior GROWTH_SAAS.md §3/§4 diversification framing - see that file's own
revision note). Summary of the decisions that constrain product
architecture, not just marketing:

- **Positioning stays "a car dashboard for your Tesla," never "Android
  Auto/CarPlay replacement" in any user-facing surface** (§1 naming rule).
  Comparison-SEO copy in the store long description is the one permitted
  exception.
- **The moat is protocol independence plus a purpose-built dashboard, not
  diversification across car brands** - revised down from the original
  "diversification is the moat" framing once Lucid left the addressable
  market and GM's Google Built-in turned out not to be addressable at all
  (§9). The `CarProfile` abstraction stays as cheap insurance, not as an
  active growth lever.
- **Monetization**: free tier is the full dashboard experience plus mirror
  + touch control, genuinely good, no artificial nerfing - revised from an
  earlier "free basic mirroring only" framing once a free, functional-enough
  competitor (Tesla Display) turned out to already exist
  (`MARKET_AND_PRICING.md` §6.2). Pro is one-time (not subscription,
  `MARKET_AND_PRICING.md` §6.2), €9.99, bundling Power Mode + in-app
  navigation + low-latency audio + split view + auto-connect + multi-car
  profiles. In-app navigation moved to Pro specifically because Mapbox's
  Navigation SDK has a real per-MAU/per-trip cost or the near-zero-
  marginal-cost claim doesn't hold (`MARKET_AND_PRICING.md` §6, session 6
  correction).
- **Distribution stays dual-channel** (Play Store + first-party signed
  APK) from Gate 5 onward - the standing answer to Failure Mode #3, and
  per session 6's market research, the sideload channel is also how the
  TesAA/WebAA-stranded user base gets reached first, since it needs no
  store review.
- Everything else (GTM sequencing, community seeding, fleet/dealer
  channel, pricing detail) is Gate 4+ execution detail and lives in
  `GROWTH_SAAS.md`, kept there rather than duplicated here.
