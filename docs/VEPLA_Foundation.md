# VEPLA - Foundation Document (v2)

> Purpose: single source of truth for this project. Every conversation, deliverable, and decision should be consistent with this document. Update it when decisions change. Sections marked (decided) are settled; sections marked OPEN are R&D gates, not preferences. v2 delta vs v1: promotes the three Gate-1 questions from "OPEN, test in the car" to "decided architecture, verified with numbers at Gate 1", adds §9b multi-brand abstraction, and adds §13 Growth & SaaS strategy (companion detail in `docs/GROWTH_SAAS.md`). Model: FREL Foundation / Lost&Sound Foundation conventions.

## 1. Name & Origin
Unchanged from v1. **VEPLA** = VE-hicle + PLA-y. No "Tesla", no "Auto", no "Play". Domain/trademark check still TODO before anything ships (vepla.com / vepla.app / vepla.be, Benelux + EUIPO).
- Naming rule (unchanged, load-bearing for §13): "Android Auto" and "CarPlay" appear only in *descriptive comparison* copy ("an alternative to..."), never in product name, app title, or store listing.
- ⚠ Scaffolding-session finding: an unrelated Swedish B2B app already ships as "VEPLA" (verksamhetsplatsen.se, construction/project-management software, live on Play Store + App Store). Different Nice class, probably not a hard blocker, but close enough that code-level identifiers now use the working codename **`vehplayer`** (Gradle project name, Android package `app.vehplayer.android`) until real trademark clearance happens. This is a source-level rename only, it does not change the brand name used anywhere in this document or in `GROWTH_SAAS.md`; do not treat it as the brand decision.

## 2. One-liners
Unchanged. Primary: **"Your apps. Your car screen."** Anti-claim rule holds: Vepla delivers the *experience*, never claims to *be* Android Auto or CarPlay.

## 3. Product Vision
Unchanged, see v1 §3. Core design principles (Fast, Simple, Local, Independent, Honest) stand as written.

## 4. The Autopsy
Unchanged, see v1 §4. Lessons 1-5 and the GPL boundary remain load-bearing constraints, not historical color.

## 5. Competitive Position
Unchanged, see v1 §5, refreshed quarterly as firmware/competitor landscape moves.

## 6. Technical Decisions , Settled (decided v1, unchanged)
No Android Auto/CarPlay protocol emulation. Native Kotlin app. Shared layer = web client + protocol. Video pipeline (MediaCodec -> WS -> WebCodecs -> canvas, MSE fallback). WebSocket transport. Data plane 100% local, control plane in the cloud. Car browser support target MCU2/MCU3+. iOS reality = Vepla Lite (view-only), Phase 2.

## 6b. The Three Gate-1 Questions , now DECIDED as architecture (v2)
v1 treated reachability, touch, and audio as open R&D questions to be settled once, in the car, before writing code. That framing was backwards: Tesla firmware changes several times a year (see Failure Mode #2), so "pick the one true answer at Gate 1" produces an architecture that breaks on the next update. v2 decision: **the fallback ladder itself is the shipped architecture**, not a temporary measurement scaffold. Gate 1 still runs (§8, `ARCHITECTURE.md` §8) but its job changes from "choose a path" to "rank and calibrate paths the app already knows how to walk."

1. **Reachability (decided v2): ship all three tiers behind one automatic probe, always.**
   - On first connect, and silently on every reconnect, the phone tries in order: (a) IPv6 GUA on the hotspot interface, no VpnService; (b) CGNAT VpnService (100.64.0.0/10); (c) TeslAA-style public-range VpnService, the guaranteed floor.
   - The probe result (which tier worked, on which firmware version) is exactly the opt-in compatibility telemetry already specified in v1 §9/§6 (control plane). Gate 1's S1 spike calibrates the *order* and the *timeout budget* per tier with MEASURED numbers; it does not eliminate tiers.
   - Consequence for UX: at most one Android VPN consent dialog, shown only if (a) fails. If (a) succeeds, the user never sees a VPN prompt at all. Copy must handle both paths honestly (Foundation principle 5).
   - This is the direct architectural answer to Failure Mode #2 (Tesla blocks a tier): the app degrades to the next tier automatically and the compatibility matrix shows the breakage within hours, no app update required to keep most users working.

2. **Touch injection (decided v2, finalizes v1's fallback ladder as the shipped default, not a contingency).**
   - v1 default (free tier): **AccessibilityService `dispatchGesture` on the mirrored main display.** One settings toggle, phone screen stays on, no developer options, no Shizuku.
   - Power Mode (Pro, opt-in): Shizuku, unlocking virtual display (screen-off) + faster injection. This is a monetized upgrade, not a hidden requirement, which is the entire wedge against Castla (v1 §4 Lesson 5).
   - View-only remains the last-resort floor per the v1 §10 kill criterion, but is not the v1 target; it is a graceful-degradation state, not a shipped mode.
   - Gate-1 S3 spike still calibrates latency/fidelity numbers and confirms `dispatchGesture` works against a VirtualDisplay per Android version (needed once Power Mode ships), but does not change which mode is default.

3. **Audio route (decided v2, finalizes v1's "likely answer").**
   - Default for all users: **car Bluetooth A2DP.** Zero code path, works day one, acceptable latency for music.
   - Pro toggle: **browser AudioWorklet route** (AAC/PCM over the WS audio channel), target <80ms added latency, for users who want nav-prompt timing tightened.
   - Gate-1 S4 spike measures the actual delta with the harness mic; it calibrates the marketing claim ("X ms faster nav prompts"), it does not decide whether the toggle ships, because the toggle is trivial to build feature-flagged behind Route B, which was designed sender-agnostic from day one (§4 Wire Protocol).

**Net effect of v2:** Gate 1 stops being a go/no-kill gate for the reachability and touch questions specifically (§10's kill criteria for those two are downgraded from "kill the product" to "kill/reprice a specific tier or the Power Mode upsell"). The only kill criterion that remains hard is: if **no** reachability tier at all survives current firmware, or if `dispatchGesture` is impossible on **any** display mode. That is now a much narrower, much less likely failure than v1 framed it.

## 7. Performance Doctrine
Unchanged, see v1 §7 and `ARCHITECTURE.md`. Latency budget: glass-to-glass ≤ 120ms perceived, touch round-trip ≤ 80ms.

## 8. Legal, Licensing & Store Strategy
Unchanged, see v1 §8. GPL hygiene, trademark naming rule, and dual distribution (Play Store + first-party signed APK) all remain load-bearing given §13's growth plan leans on the first-party channel as insurance against Play Store AccessibilityService policy risk.

## 9. Technical Decisions , Multi-Brand Abstraction (new, v2)
The wire protocol and web client were already designed sender-agnostic (v1 §6, `ARCHITECTURE.md` §4) to accept iOS or a desktop harness. v2 extends the same discipline to the *car* side, because §13's growth thesis depends on Tesla not being a hard dependency.
- **Decided v2: introduce a `CarProfile` config object now** (browser engine + version floor, viewport reporting quirks, RFC1918 policy, WebCodecs/MSE support, known input event fidelity) instead of Tesla-specific conditionals scattered through the codebase. Tesla MCU2/MCU3 ship as the first two `CarProfile` entries; the compatibility telemetry (v1 §9) becomes, structurally, "which `CarProfile` did this session run under," which is the same data whether the car is a Tesla or not.
- This costs nothing at Gate 1-3 (only one profile exists) and removes the rewrite risk when Rivian/Polestar/Volvo EX browsers become viable targets (§13).
- Explicit non-goal for v1-v3: do not go chase a second car brand before Tesla is daily-drivable (Gate 3). This is a data-modeling decision now, not a roadmap acceleration.

## 10. Failure Modes & Kill Criteria
Unchanged list from v1 §10, with the reachability/touch kill criteria narrowed per §6b above. Re-read alongside `docs/GROWTH_SAAS.md` §5 (moat = diversification) for how §13's roadmap is itself a mitigation for Failure Mode #1 (Tesla ships native support).

## 11. Roadmap with Gates
Unchanged structure (Gate 0-6, see v1 §11). Gate 1's deliverable is now explicitly "MEASURED calibration of the §6b ladder," not "a single chosen path." Gate 5 (company layer) is expanded by `docs/GROWTH_SAAS.md`.

## 12. Working Agreements (Claude ↔ founder)
Unchanged from v1: Dutch conversation, English deliverables, no em-dashes, no time estimates, Gate-scope pushback, evidence tags (MEASURED/REPORTED/ASSUMED) on every browser claim, session-end doc refresh.

## 13. Growth & SaaS Strategy (new, v2)
Full detail lives in `docs/GROWTH_SAAS.md`. Summary of the decisions that belong in Foundation because they constrain product architecture, not just marketing:
- **Positioning stays "browser extension of your phone," never "Android Auto/CarPlay replacement" in any user-facing surface** (naming rule, §1). Comparison-SEO copy is the one permitted exception, phrased as alternative-to.
- **The moat is diversification, not a single trick.** Every brand added under the §9 `CarProfile` abstraction is one fewer single point of failure against Failure Mode #1/#2. This is why §9's data-modeling decision ships before it is "needed."
- **Monetization stays Freemium** (v1 §9 hypothesis), free tier is the funnel and must stay genuinely good (mirror + touch, no artificial nerfing), Pro is the Power Mode + convenience bundle (§6b item 2, low-latency audio, split view, auto-connect, multi-car profiles).
- **Distribution stays dual-channel** (Play Store + first-party signed APK) from Gate 5 onward, not as a contingency but as the standing answer to Failure Mode #3.
- Everything else (GTM sequencing, community seeding, fleet/dealer channel, pricing detail) is Gate 4+ execution detail and lives in `GROWTH_SAAS.md`, not here, to keep Foundation a stable reference.
