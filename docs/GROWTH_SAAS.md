# VEPLA - Growth & SaaS Strategy (v1)

> Companion to `VEPLA_Foundation.md` §13. This is where the growth/business-model reasoning lives in full; Foundation keeps only the decisions that constrain architecture. Evidence status tags apply here too where a claim is empirical (MEASURED / REPORTED / ASSUMED).

## 1. What Vepla actually is, commercially
Vepla is not "an Android Auto clone." It is **a control plane for a local streaming session**, sold as reliability-as-a-service (Foundation §9's own framing, taken seriously as the whole business, not a tagline). The app itself, once installed, works without Anthropic-style dependency on us. What people pay for is: it keeps working after the next Tesla firmware update, it gets faster over time via web-client OTA, and it eventually works on more than one car brand. That is a subscription product, not a one-time utility.

## 2. Positioning (the naming-rule constraint, taken to its conclusion)
- Never in-product, never in the app title, never in store metadata: "Android Auto", "CarPlay", "works with Android Auto". This is not caution for its own sake, it is the exact mistake TeslAA's ecosystem risk mirrors on the trademark side (Foundation §1).
- Where it *is* allowed: comparison landing pages and paid search copy, explicitly framed as alternative-to ("an alternative to Android Auto and CarPlay for cars with a browser instead of a head unit"). This is standard nominative fair use and is how every dash-cam, adapter, and CarPlay-dongle competitor already advertises; it is low legal risk as long as no logo, no implied endorsement, no store-listing keyword-stuffing.
- The honest one-line pitch that does the SEO and word-of-mouth work without touching a trademark: **"Your phone, on your car's screen. No box, no dongle, no Shizuku."** The "no Shizuku" clause is aimed directly at the Castla/enthusiast audience who already knows what that word means and why it is a dealbreaker for a partner or parent's car.

## 3. Market entry sequencing (who buys first)
1. **Tesla owners without factory CarPlay/AA** (the whole existing market, proven by TeslAA and Castla's forum traction). This is the entire Gate 1-4 target. Do not dilute focus before Gate 4.
2. **Tesla owners who tried Castla and bounced off Shizuku.** This is a named, findable audience (Castla's own GitHub issues and TMC threads are the recruiting ground for the private beta, Gate 4). Free tier removes their exact blocker.
3. **Second-hand EV buyers** who bought a car specifically because factory infotainment lacked CarPlay/AA and the used-car discount reflected that (a real, documented pattern in the Tesla resale market). Vepla is a feature they can add back for the price of an app instead of a hardware adapter that most Teslas cannot physically use anyway (no CarPlay hardware path exists on Tesla at all, dongles target other brands).
4. **Other browser-capable EV brands** (Rivian, Polestar, Volvo EX90/EX30, Lucid) once the `CarProfile` abstraction (Foundation §9) has a second real entry with MEASURED data. This is explicitly Gate 6+, not earlier; chasing it sooner is scope creep against Gate 1-3 (Working Agreement, Foundation §12).

## 4. Monetization architecture
| Tier | Price shape (test at Gate 4) | Contents |
|---|---|---|
| Free | $0 | Mirror mode, AccessibilityService touch, BT audio. The wow moment must be free (Foundation §9); this is the acquisition funnel, not a stripped demo. |
| Pro | Subscription or one-time+updates (price-test both) | Power Mode (Shizuku, virtual display, screen-off), low-latency browser audio, split view, auto-connect (BLE detection + auto-hotspot), multi-car profiles. |
| Fleet/Dealer (Gate 6+, exploratory) | Seat-based or per-vehicle | Centralized entitlement management for rental fleets or dealer demo cars, co-branded onboarding flow, no data-plane change (still fully local per vehicle), just control-plane multi-tenancy. |

Unit economics stay near-zero marginal cost by design (Foundation §9): the data plane never touches our infrastructure, so the SaaS layer scales like a licensing API, not like a video-relay business. This is the single biggest structural cost advantage over any product that has to touch the video stream server-side, and it should stay a permanent architecture constraint, not just a v1 cost-saving.

## 5. The moat: diversification, not a trick
Every prior product in the Autopsy (Foundation §4) died from a single point of failure: TeslAA/WebAA died when Google tightened one validation check; Castla's real adoption ceiling is the Shizuku requirement, a single UX point of failure. Vepla's moat is structural, not clever:
- **Protocol-level**: no dependency on Android Auto/CarPlay protocols, so there is nothing for Google or Apple to revoke (Foundation §4 Lesson 1).
- **Distribution-level**: dual channel (Play Store + first-party signed APK, Foundation §8) so a single store policy change cannot kill distribution.
- **Reachability-level**: the fallback ladder (Foundation §6b) means a single Tesla firmware change degrades a tier, not the product.
- **Brand-level (Gate 6+)**: once a second `CarProfile` ships, Tesla-specific breakage stops being existential at all, it becomes a support ticket category.
This is the actual answer to Failure Mode #1 (Tesla ships native AA/CarPlay support): if that happens, Tesla-specific revenue erodes, but the company does not, because the product was never architecturally a "Tesla app."

## 6. Go-to-market sequencing
1. **Gate 1-3 (build in stealth)**: no public marketing spend. The compatibility telemetry and latency harness are themselves content assets being built for later (the "does my car work" matrix, Foundation §9).
2. **Gate 4 (private beta)**: recruit from TMC forums and the Tesla subreddit, the same channels that proved demand for TeslAA and Castla. Direct outreach to visible Castla GitHub issue authors is a legitimate, low-cost recruiting channel (public complaints about Shizuku are, in effect, a pre-qualified lead list). Pricing survey embedded in the beta, per Foundation §9.
3. **Gate 5 (company layer)**: publish the compatibility matrix as a public page (transparency-as-marketing, mirrors the FREL/Lost&Sound pattern of turning an internal validation artifact into a public trust asset). Comparison-SEO pages go live here, governed by §2's naming constraint.
4. **Gate 6 (launch)**: paid acquisition only once organic/community channels are measured (avoid burning spend before knowing CAC against a beta-validated conversion rate). Second `CarProfile` (Rivian/Polestar) research starts here, not before.

## 7. Failure-mode watch specific to growth (extends Foundation §10)
- **Overexpansion before Gate 4**: chasing a second car brand, a fleet product, or paid ads before Tesla is daily-drivable is the single most likely way to kill this the FREL way (bus-factor 1, unfocused scope). Treat any pressure to do this as a scope-creep tripwire per Foundation §12.
- **Pricing anchored to TeslAA's willingness-to-pay evidence, not to Castla's free status.** TeslAA proved people pay for a *worse* version of this; do not underprice out of comparison to a free hobby project with a dealbreaker UX flaw.
- **Store risk (AccessibilityService policy)** is a growth risk, not just a technical one: any Gate 5 GTM plan must assume the first-party APK channel carries real distribution weight, not just serve as a fallback nobody uses.

## 8. Open questions for Gate 4 (not decided, flagged honestly)
- Exact Pro price point (subscription vs one-time+updates) needs the beta pricing survey, per Foundation §9. No number is asserted here.
- Whether "low-latency audio" is a strong enough standalone Pro hook or needs bundling with Power Mode to justify a subscription at all; Gate 1 S4 numbers will inform this but the bundling decision is a Gate 4 pricing-survey question, not an architecture one.
- Fleet/Dealer tier viability is genuinely unresearched; flagged as exploratory only, do not build against it before Gate 6.
