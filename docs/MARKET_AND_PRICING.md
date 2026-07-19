# vehplayer , Market, Competition, Pricing and Go-to-Market

> Drop this in `docs/`. Load it alongside `VEPLA_Foundation.md`, `ARCHITECTURE.md`,
> `GROWTH_SAAS.md` and `NEXT_SESSION.md`.
>
> **Why this document exists.** A market sizing was sketched in a Google Search AI
> conversation. Roughly half of it is wrong, and the half that is wrong is the half
> that matters: it says the competition is dead and the market is empty. It is not.
> This document is the fact-checked replacement. Every claim carries a source and an
> evidence tag: **MEASURED** (verified in our own car/repo), **REPORTED** (a public
> source says so, linked), **ASSUMED** (our inference, flagged as such).
>
> Research date: 19 July 2026. This is desk research via public web sources, not a
> commissioned market study and not legal advice. Sections 8 and 9 in particular are
> a reason to hire a professional, not a substitute for one.

---

## 0. The one-paragraph version

The addressable car base is roughly **four times larger** than the Google AI estimated,
and skewed toward the *fast* infotainment chip rather than away from it. The competitive
field splits into two camps: **Android Auto protocol emulators, which are dying**
(TesAA/WebAA is broken right now by a Google Play Services update, confirmed live), and
**screen mirroring apps, which are alive and are our real competition** (Tesla Display
is free and already does touch control; TeslaMirror ships H.265 while we ship H.264).
So the market is neither the vacuum the Google AI described nor the crowd a flat
competitor list suggests. Two conclusions follow. **One: our protocol-independence bet
is now proven by someone else's outage, and should be said out loud in marketing.
Two: mirroring is a commodity and must be free; the purpose-built car dashboard is the
product and the only thing worth charging for.** The founder reached conclusion two by
instinct in session 4 (`CarDashboardActivity`). This document is the evidence for why
that was right, plus a launch window (§4.0) that is open now and will close.

---

## 1. Fact-check verdict table

| Claim from the Google Search AI | Verdict | Reality |
|---|---|---|
| "Ruim 5 tot 6 miljoen Tesla's wereldwijd" | **Too low** | ~9.7M cumulative deliveries through Q2 2026. REPORTED |
| "Tesla's vanaf 2022 gebruiken AMD Ryzen" | **Correct** | MCU3 rollout began China Nov/Dec 2021, North America late Dec 2021. REPORTED |
| "Als we Intel wegfilteren blijft 1,5-2M over" | **Backwards** | MCU3 is now the *majority*: ~7.4M of ~9.7M. The fast-chip population is the big one. ASSUMED (derived from delivery data) |
| "TesAA/Tesla Display zijn deprecated, bestaan niet meer" | **Half right, and the right half matters** | TesAA is still *listed and selling* at $4.99. But androidwheels.com, the endpoint the whole product depends on, currently serves: "Latest Google Play Service update breaks WebAA. We are looking for potential workarounds, but options are limited." It is sold but broken. MEASURED (founder screenshot) + REPORTED (site live) |
| "Nu die concurrentie is weggevallen" | **Partly true** | The *Android Auto protocol* camp has fallen away. The *screen mirroring* camp has not, and that is the camp we are in. See §4 |
| "Je bent de enige die een werkende oplossing biedt" | **False** | Tesla Display, TeslaMirror and Car Cast are unaffected by the WebAA break and are alive. Some are ahead of us on codecs (H.265). REPORTED |
| "€4,99 eenmalig als introductieprijs" | **Wrong shape** | A free competitor with touch control already exists. Charging for basic mirroring is dead on arrival. See §6 |
| "Android/iOS onder EV-rijders ~40/60" | **Plausible, unverified** | Directionally right for the US, too pessimistic globally (EU/China skew Android). ASSUMED |
| "Smartphone-projectie" as the umbrella term | **Correct** | Standard industry term, also "phone projection". REPORTED |
| Rivian blocks CarPlay | **Correct** | Still no CarPlay/AA as of 2026, has Google Cast (park only). REPORTED |
| Lucid added CarPlay | **Correct and important** | Gravity got CarPlay + AA via OTA March 2026, so Lucid is *leaving* our market. REPORTED |
| GM dropping CarPlay in new EVs | **Correct** | REPORTED |

Two things the Google AI never raised, both of which are bigger deals than anything it
did raise:

1. **The Tesla browser suppresses `<video>` playback while in Drive.** This is the
   single most important technical constraint in the entire product and it is the
   reason our architecture is correct. See §3.
2. **Google Play's AccessibilityService policy plus the `REQUEST_INSTALL_PACKAGES`
   policy are in direct conflict with what the app currently does.** See §7. This is
   a launch blocker that needs a decision before the store listing, not after.

---

## 2. Market sizing, corrected

### 2.1 The car base

Tesla cumulative deliveries (REPORTED, Tesla IR + Electrek + Statista):

| Period | Deliveries | Infotainment |
|---|---|---|
| Through 2021 | ~2.3M | MCU1 / MCU2 (Intel Atom) |
| 2022 | 1.31M | MCU3 (AMD Ryzen) |
| 2023 | 1.81M | MCU3 |
| 2024 | 1.79M | MCU3 |
| 2025 | 1.64M | MCU3 |
| 2026 H1 | 0.84M | MCU3 |
| **Cumulative** | **~9.7M** | |

- **MCU3 (AMD Ryzen, our best-case tier): ~7.4M cars.** ASSUMED, derived from the
  Dec 2021 rollout date against per-year delivery totals.
- **MCU2 (Intel Atom, Model 3/Y 2017-2021 and S/X 2018-2021): ~2.2M cars.** Not
  automatically excluded. TesAA's own store listing says "All Tesla's supporting
  Netflix/YouTube/Disney+ should be able to use this app", and TeslaMirror ships
  H.265 for MCU2 on Tesla software 2025.38.11 and later. REPORTED
- **MCU1 (pre-2018 S/X): ~0.3M.** Out of scope, consistent with Foundation §6.

Correction worth internalising: the Google AI treated the Intel tier as the majority to
be filtered out. It is the minority. **Our primary target is the large, modern,
fast-chip population, and MCU2 is the upside case, not the base case.**

### 2.2 The honest funnel

Cars are not users. Users are not buyers. This is the funnel that should drive
planning, and each step down is where the Google AI's number went wrong by an order
of magnitude.

| Layer | Estimate | Basis |
|---|---|---|
| Teslas on the road | ~9.2M | ~9.7M delivered minus write-offs. ASSUMED |
| ... with an Android-primary driver | ~3.7-4.6M | 40-50% Android blend across US (iPhone-heavy) and EU/China (Android-heavy). ASSUMED, unverified |
| ... on MCU2/MCU3 (browser capable) | ~3.6-4.5M | ~97% of the fleet. ASSUMED |
| **TAM** | **~4M** | |
| ... who will ever *hear* about a niche sideloaded app | ~1-3% | Category benchmark, see below. ASSUMED |
| **Realistic reachable audience** | **~40k-120k** | |
| ... who install | fraction of that | |
| **Realistic year-one installs** | **2k-15k** | Benchmarked against TesAA, see below |

**The benchmark that should anchor all planning:** TesAA has been on the Play Store
for about 1,678 days (since roughly late 2021), is the single best-known product in
this exact category, is backed by AAWireless (a real hardware company with a real
audience), and has **10,000 to 20,000 total installs** with ~780 in a recent 30-day
window. REPORTED (AppBrain).

That is the ceiling to plan against. A €5 paid app in this category is a
**€10k-75k lifetime gross revenue** product, not a business that supports a team.
Plan the cost structure accordingly: this must stay a near-zero-marginal-cost,
low-maintenance product, which is exactly what Foundation §9's local-first data plane
already gives us. Do not build anything with a per-user server cost.

### 2.3 Non-Tesla expansion, revisited

- **Rivian**: no CarPlay/AA, has Google Cast but park-only, browser reportedly
  planned. Fleet is small (low hundreds of thousands). Watch, do not build. REPORTED
- **Lucid**: added CarPlay + Android Auto by OTA in March 2026. **Leaving our market.**
  Remove from any future TAM slide. REPORTED
- **GM (Blazer EV and newer)**: dropped CarPlay/AA for Google Built-in. Google Built-in
  has its own app store and no usable general browser, so this is *not* an addressable
  market for a browser-based caster. Do not count it. ASSUMED
- Conclusion: the `CarProfile` abstraction in Foundation §9 is still correct as cheap
  insurance, but **the non-Tesla expansion story is weaker than Foundation §13 implies.**
  Tesla is not a stepping stone here, it is essentially the whole market. Adjust
  expectations, keep the abstraction (it costs nothing), drop the multi-brand growth
  narrative from anything investor-facing until a second profile is MEASURED.

---

## 3. The technical constraint that defines the product

**The Tesla browser blocks `<video>` element playback while the car is in Drive.**
REPORTED, multiple independent sources including a TeslaTap developer describing
exactly our architecture:

> "the video component of the browser is not available, to workaround I convert the
> video to canvas images and use websocket to send the stream to the car browser and
> it render in the canvas using animationFrame"

This is why `videoDecoder.ts` decoding to **canvas** rather than to a `<video>` element
is not an implementation detail, it is the entire product moat. Consequences that must
be written into `ARCHITECTURE.md`:

1. **The WebCodecs to canvas path is the only path that works in Drive.** Promote it
   from "primary, with MSE fallback" to "the product".
2. **`mseFallback.ts` is worth less than the current docs imply.** MSE renders through
   a `<video>` element, so on cars where it would be needed it is also most likely to
   be suppressed in Drive, which is the only state that matters for a nav product. The
   unimplemented fMP4 muxer should drop down the priority list, and the doc comment in
   that file should say why rather than implying it is simply unfinished. Verify in the
   car before deleting, but stop treating it as a required deliverable.
3. **Any "watch a movie" positioning is a park-only feature** and should never be the
   headline. Competitors lead with movies and video calls. We should lead with
   navigation and audio, which is what actually works in motion, and which is also the
   only honest safety posture.
4. Tesla update 2026.20 added Parental Controls toggles for Browser, Theater and Arcade,
   and Not a Tesla App expects "more granular app control, such as only restricting
   access to certain apps when the car is in motion, in future updates". REPORTED.
   **This is Failure Mode #2 from Foundation §10 arriving on schedule.** The remote
   config kill-switch design in `cloud/src/remoteConfig.ts` is the right hedge and
   should be finished rather than left at `REPLACE_ME`.

---

## 4. The real competitive landscape

The category splits into **two camps with different architectures and therefore
different fates.** This distinction is the single most important thing in this document,
and both the Google AI and the first draft of this analysis missed it.

### Camp A: Android Auto protocol emulation (dying, confirmed)

These emulate an Android Auto head unit on the phone and let Google's Gearhead connect
to it. Google Play Services validates head units. Google controls that validation.

| Product | Status |
|---|---|
| **TesAA / TeslAA / WebAA** (Borconi-Szedressy, now AAWireless) | **Broken as of now.** androidwheels.com serves: *"Latest Google Play Service update breaks WebAA. We are looking for potential workarounds, but options are limited."* Still listed at $4.99 with 10-20k installs. MEASURED + REPORTED |

**This is Foundation §4 Lesson 1 happening in real time, for the second time.** The
Autopsy already recorded that one Google Play Services change killed this product line
once. It has now happened again, to the same product, under a new name. The lesson was
not theoretical and it is not historical.

**What this means for vehplayer: our core architectural bet just got validated by
someone else's outage.** We do not touch the Android Auto protocol, so no Google Play
Services release can do this to us. That is not luck, it is Foundation §4 Lesson 1, and
it should now be stated in marketing copy as a concrete promise rather than an
engineering principle: *"Nothing Google ships can turn this off."*

### Camp B: screen mirroring over the browser (alive, unaffected, our actual competition)

These never talk to Gearhead. They capture the screen and stream it. The WebAA break
does not touch them, and it does not touch us either, because this is our camp.

| Product | Platform | Price | Position | Threat |
|---|---|---|---|---|
| **Tesla Display / TesDisplay** | Android + iOS | **Free** | Mirrors phone, plays video in D-mode, **can control the phone from the Tesla screen**, casts Waze/Maps | **Highest.** Free, and already does mirroring plus touch control, which is our free tier |
| **TeslaMirror** | Android | Paid | Actively maintained: v9.72 notes Android 17 support and **H.265 on MCU3 and on MCU2 running 2025.38.11+** | **High.** Technically ahead of us (we are H.264 only) |
| **Car Cast** | Android + iOS | Freemium: 1h/month free, then monthly sub or lifetime | Slick marketing, "no VPNs, proxies or IP addresses", cross-platform, Toxic Degu Ltd | **High.** Best-funded-looking, owns the UX narrative |
| **1001 TVs** | Android + iOS | Free tier | General screen mirroring with a Tesla landing page | Low, unfocused |
| **Tesor** | Android | Hobby/unreleased | Shizuku-based, single-app casting, lossless audio, auto-hotspot on Phone Key | Low reach, but the closest to our technical ambitions |

Also present: TeslaTV.net (live TV streaming, park-oriented), and hardware CarPlay
adapters for Rivian at around $400 which do not apply to Tesla.

### 4.0 The launch window this opens

Camp A's collapse strands a **paying, high-intent, findable** user base: people who spent
$4.99 specifically to get phone apps onto a Tesla screen, used it daily, and now have
nothing. They are not a hypothetical persona, they are an identifiable list of people
actively looking for a replacement right now.

Where they are, all public: the Tesla Motors Club TeslAA thread (which runs to many
pages and is where users report outages first), TesAA's Play Store reviews, the
`#TeslAA` channel in the AAWireless Slack workspace linked from TesAA's own store
listing, and r/TeslaMotors.

**This is the beta cohort in `GROWTH_SAAS.md` §6, except the recruiting problem just
solved itself.** It is also, conveniently, more than the 12 testers the Play Console
gate needs (§7.3).

Two constraints on exploiting this, both non-negotiable:

1. **Do not pitch into their outage threads as a vendor.** Answering "does anything still
   work?" with a link is legitimate and welcome. Posting an ad is how you get banned from
   the exact community you need. Foundation §3's honesty principle applies to marketing
   too.
2. **Do not promise what is not tested.** We have still never loaded the web client in a
   real car. Recruiting stranded users to a product that also fails to connect burns the
   one audience that matters. Real-car verification comes before any outreach, in that
   order, no exceptions.

This window will not stay open. Either AAWireless finds a workaround, or those users
drift to Tesla Display and Car Cast, which are free and working today.

### 4.1 What this changes

**The Google AI's core strategic claim was that the market is empty and therefore
willingness to pay is high. The opposite is true.** There is a competent free
competitor doing mirroring with touch control, and a polished freemium competitor on
both platforms. Anything we charge for must be something none of them has.

**What none of them has:** every single one of these products mirrors the *raw phone
screen*. Not one ships a purpose-built, car-optimised interface. Car Cast's own store
copy is a list of phone apps you can mirror. Tesla Display's is a list of video apps
that work. This is precisely the gap `CarDashboardActivity` fills, and it maps exactly
to the founder's session-4 pushback ("casting my whole phone has no value to me, I want
the Android Auto/CarPlay *feeling*").

**Second thing none of them has, credibly:** a local-only data plane as a *stated*
privacy position. Car Cast advertises "no hotspot required, use Premium Connectivity"
and serves its player from `play.carcastapp.com`, which strongly suggests traffic
leaves the car. ASSUMED, worth verifying. If true, "your screen never leaves your car"
is a real, defensible differentiator that costs us nothing because it is already how
the architecture works (Foundation §6).

### 4.2 Where we are behind

Be honest about this in planning:

- **Codec**: TeslaMirror ships H.265. We are H.264 only. H.265 at the same quality is
  meaningfully fewer bits over a phone hotspot. This is a real gap on the one axis
  (latency and smoothness) where Foundation §7 claims we compete.
- **Platform**: two competitors are on iOS. We are Android only, and Foundation §6's
  "iOS reality" section correctly says a full iOS version is not possible. Our
  addressable market is structurally about half of theirs.
- **Maturity**: TeslaMirror is on version 9.x with per-firmware release notes. We have
  not yet loaded our web client in an actual car (`NEXT_SESSION.md`).

---

## 5. Positioning

Recommended one-liner, replacing the Foundation §2 primary for store and site use:

> **"A car dashboard for your Tesla. Not a phone on a big screen."**

Rationale: it states the differentiator in the same breath as the category, it is
provably true of us and provably false of every competitor listed above, and it
contains no trademark of Apple's or Google's.

Supporting lines:
- "Navigation, music and calls, laid out for a car screen. Your phone stays in your pocket."
- "Nothing leaves the car. No cloud, no relay, no account."
- **"Nothing Google ships can turn this off."** Earned, not claimed: the competing
  Android Auto based product broke on a Google Play Services update while this was
  being written (§4, Camp A). We do not use that protocol, so that failure mode does
  not exist for us. This is the strongest line we have and it is the only one a
  competitor cannot copy without rewriting their architecture.

### 5.1 On the Foundation §1 naming rule

Foundation §1 forbids "Android Auto" and "CarPlay" outside descriptive comparison copy.
**Keep that rule for the app title and the brand. Relax it, deliberately and in
writing, for the store long description and SEO pages.** Evidence: Car Cast's live Play
listing opens a section with "TESLA DOESN'T HAVE ANDROID AUTO. CAR CAST DOES THE JOB."
and remains published. Nominative use to describe compatibility is standard practice
and is how every competitor is discovered, because "android auto tesla" is the search
term real users type.

The rule that must not bend: never imply endorsement, never use the marks in the app
name, icon, or listing *title*, never use Apple's or Google's logos. This is the
distinction between describing a market and impersonating a brand.

---

## 6. Pricing plan

### 6.1 What the evidence supports

- A free competitor with touch control exists. **Charging for basic mirroring is dead
  on arrival.**
- TesAA sustains $4.99 one-time for a stale, 3.5-star product, which sets a floor for
  what this audience tolerates. REPORTED
- Car Cast runs subscription plus lifetime, which validates recurring pricing in the
  category but also creates the opening for a "no subscription" counter-position.
- Tesla owners are not price sensitive in absolute terms. They are sensitive to paying
  for something that breaks at the next firmware update, which is the recurring
  complaint in TesAA's reviews. REPORTED

### 6.2 Recommendation

**Free tier (the product, not a demo):**
- The full `CarDashboardActivity` experience: dashboard, navigation launch, now playing,
  phone, messages.
- Mirror mode with touch control.
- No time limits, no watermark, no nag. Deliberately more generous than Car Cast's
  one hour per month, because our free tier's job is to beat the *free* competitor,
  not to squeeze the paid one.

**vehplayer Pro, one-time purchase, €9.99 (test €7.99 and €12.99):**
- Screen-off / virtual display mode (phone screen stays dark, Power Mode).
- Low-latency browser audio (Route B).
- Auto-connect: BLE car detection plus automatic hotspot.
- Split view: navigation and media side by side.
- Multi-car profiles.

**Why one-time rather than subscription:** it is a direct counter to Car Cast, it suits
a solo-maintained product where a subscription creates a support obligation we cannot
staff, and it removes the "it will break and I will still be paying" objection that
dominates competitor reviews. Positioning line: **"Buy it once. No subscription."**

**Why not €2.99:** it does not change conversion meaningfully at this volume, it signals
throwaway software in a category where users have been burned, and at a realistic
2k-15k install ceiling the difference between €3 and €10 is the difference between
pocket money and a product that funds its own development.

**Deliberately not recommended:**
- Time-limited free trials. User-hostile in a car, where the failure mode is the
  product dying mid-drive.
- Subscription as the only option. Reserve it for a genuine ongoing-cost feature, which
  currently does not exist because the data plane is local.
- Ads. Foundation §8's privacy posture and a driving context both rule this out.

### 6.3 Revenue expectation, stated plainly

At 5,000 installs and a 5% free-to-paid conversion at €9.99, minus Google's 15% fee on
the first $1M, this returns roughly **€2,100**. At 15,000 installs and 8% conversion,
roughly **€10,200**. REPORTED (fee), ASSUMED (conversion).

This is a real, useful, self-funding niche product. It is not a company, and the
`GROWTH_SAAS.md` framing of "reliability-as-a-service" with a Fleet/Dealer tier is
aspiration rather than plan. Recommend rewriting `GROWTH_SAAS.md` §4's Fleet tier as an
explicitly speculative appendix so that no future session treats it as roadmap.

---

## 7. Distribution and the Play Store policy minefield

This section is the most actionable in the document. **Two of the app's current
behaviours conflict with live Google Play policy.** REPORTED, all from Play Console Help.

### 7.1 AccessibilityService

- Play permits the API broadly, **but** any app not declaring `isAccessibilityTool=true`
  must complete an accessibility declaration in Play Console **and** implement a
  prominent in-app disclosure with affirmative consent, separate from the privacy policy
  and separate from any other disclosure.
- Policy enforcement tightened with a **28 January 2026** date.
- We must **not** declare `isAccessibilityTool=true`. We are not an assistive product,
  and a false declaration is grounds for app suspension and developer account
  termination.
- **Action**: build the disclosure screen now. Requirements: state what data the service
  accesses, state how it is used, require an explicit tap to accept, and do not bundle
  it with the other permission asks. The existing accessibility onboarding step in
  `MainActivity` is the right place and is currently non-compliant.

### 7.2 `REQUEST_INSTALL_PACKAGES` versus the self-update pipeline

Play policy states that using this permission "to update your app, change its
functionality or bundle other APKs for silent or unauthorized installation (except
enterprise management)" is prohibited.

**The session-3 update pipeline (`UpdateChecker.kt` plus `ApkInstaller.kt`) is exactly
this.** It is fine for sideloaded distribution and it is a genuinely good piece of
engineering, but it **cannot ship in a Play Store build**.

**Action**: gate it behind a build flavour.
- `sideloadRelease`: keeps `UpdateChecker` + `ApkInstaller` + `REQUEST_INSTALL_PACKAGES`.
- `playRelease`: strips all three, relies on Play for updates.
This also resolves the Play Protect warning documented in `NEXT_SESSION.md`, which is
almost certainly triggered by the accessibility-plus-self-install combination.

### 7.3 The account gate

- Personal developer accounts created after 13 November 2023 must run a closed test with
  **at least 12 testers opted in for 14 continuous days** before production access, and
  Google also evaluates whether the app visibly improved during the window.
- **Organization accounts registered to a legal entity are exempt entirely.**
- **Recommendation [MENS]**: register the Play developer account as the company, not as
  a person. This removes the tester gate completely. Given FREL and Vondst exist, an
  entity may already be available.
- If a personal account is used anyway, the 12 testers are recruitable from the same
  TMC and r/TeslaMotors communities that are the beta cohort in `GROWTH_SAAS.md` §6, so
  plan those as one activity rather than two.

### 7.4 Other live deadlines

- New apps must target **API 36 (Android 16) by 31 August 2026**. Current `compileSdk`
  is 35. **Action: raise `targetSdk` to 36 and retest.**
- Developer verification begins 30 September 2026 in Brazil, Indonesia, Singapore and
  Thailand. Not blocking for an EU launch, worth tracking.
- Free "Limited Distribution" accounts launch globally in August 2026. Possibly relevant
  as a low-commitment first channel. Verify details before relying on it.

### 7.5 Channel recommendation

Ship **both**, and stop treating the first-party APK as a fallback:

1. **Play Store (`playRelease`)** for reach, trust and payment handling. Accept the
   policy constraints above.
2. **First-party signed APK (`sideloadRelease`)** from the site, for the power-user
   build with self-update. This is also the insurance policy if the accessibility
   declaration is ever rejected, which Foundation §10 Failure Mode #3 already
   anticipates.

---

## 8. Brand and trademark

- **"vehplayer" appears clear.** No conflicting trademark surfaced in the categories that
  matter. REPORTED, though this is a web search and not a register search.
- **The VEPLA collision remains real.** An unrelated Swedish B2B app ships under that
  name. The working codename switch was the right call and should now be made permanent:
  **retire "VEPLA" entirely** and rename `docs/VEPLA_Foundation.md` to
  `VEHPLAYER_Foundation.md`. Carrying two names in one repo is pure confusion cost, and
  the repo already shows the symptom (see the "v1 does not exist" note in
  `NEXT_SESSION.md`).
- **Descriptiveness risk.** "vehplayer" reads as "vehicle player", which is close to
  descriptive. Descriptive marks are harder to register and harder to defend. This is
  acceptable for a niche product and is a reason to file the **logo/stylised mark**
  alongside the word mark if anything is ever filed.
- **Nice classes** if filing: Class 9 (downloadable software) and Class 42 (SaaS), the
  standard pairing for an app with a web component. REPORTED
- **[MENS] actions**: confirm domain holdings, and before spending on any registration,
  get a real screening from a merkenbureau. Follow the Vondst precedent: the
  `legal/merkenonderzoek.md` there is exactly the right artefact and it caught a real
  conflict before money was spent.

---

## 9. What to build in this session

The repo is currently strong on engineering and thin on the company layer. Vondst's
structure is the model. Concretely, create:

### 9.1 `CLAUDE.md` at repo root
Missing entirely. This is the highest-value single file to add, because every future
session currently re-derives the conventions from four long docs. Model it on Vondst's:
commands, architecture, house rules. Must include the existing rules (no em-dashes, no
time estimates, evidence tags, GPL hygiene, the Lesson-1 tripwire) plus the new ones
from this document (canvas not video, free tier is not a demo, no `isAccessibilityTool`).

### 9.2 `brand.json` at repo root
Single source of truth for name, tagline, status, domain. Both the Android app and the
web client currently hardcode strings. Vondst's `brand.json` plus a rename sweep is the
proven pattern, and it is what makes a future rename cheap instead of a multi-file hunt.

### 9.3 `Makefile` at repo root
There is no single entry point. A new session has to read three READMEs to learn how to
build. Target set:

```
make setup       # android SDK check, npm install in webclient/ and cloud/, python venv for validate/
make android     # ./gradlew assembleDebug with version flags wired
make webclient   # npm run build in webclient/
make deploy-web  # wrangler deploy from webclient/
make harness     # the fake sender
make latency     # the glass-to-glass harness selftest
make release     # version bump, aapt verify, gh release create
```

`make release` must include the `aapt dump badging | head -1` versionCode check that
`NEXT_SESSION.md` records as a real incident. Encoding that lesson in a Makefile target
is how it stops recurring.

### 9.4 `legal/` directory
Following Vondst: `merkenonderzoek.md` (start from §8 here), `privacybeleid.md`,
`gebruiksvoorwaarden.md`, `verwerkingsregister.md`. **A privacy policy is a hard
requirement for a Play Store listing**, and for this app it is also a genuine selling
point, because the honest version says almost nothing is collected. Write it before the
listing, not during the submission.

Include a **safety and liability note**. Every competitor carries a disclaimer about
in-motion use. Ours should be honest rather than boilerplate, and it should match the
product decision in §3 (navigation and audio in motion, video in park).

### 9.5 `docs/` corrections
- Rename the Foundation, drop the VEPLA name, and **fix the phantom v1 problem**: either
  reconstruct the cited sections or remove the citations. Do not leave a document
  citing a file that has never existed.
- Update `ARCHITECTURE.md` §2 with the canvas-versus-video finding from §3 here.
- Rewrite `GROWTH_SAAS.md` §3 and §4 against the real competitor set in §4 here. The
  current "diversification is the moat" argument is weakened by Lucid leaving the market
  and GM not being addressable.

### 9.6 Product work, in priority order

Reordered against §4.0: the stranded Camp A users are the launch window, and the only
thing standing between us and them is that the product has never been proven in a car.

0. **Load the web client in the actual Model 3 and complete one real `/go` round trip.**
   Everything below is speculative until this passes. It is also the cheapest task on
   the list and has been deferred across four sessions. `[MENS]` for the car, Claude for
   the debugging.
1. **Accessibility disclosure screen** (§7.1). Blocks the store listing.
2. **Build flavours** to separate Play and sideload builds (§7.2). Blocks the store
   listing, and the `sideloadRelease` flavour is what serves the stranded users first,
   since it needs no store review at all.
3. **`targetSdk` 36** (§7.4). Fixed deadline, do not let it surprise us.
4. **Dashboard phase 2 and 3**: real now-playing via `MediaSessionManager`, and the nav
   app picker. Already scoped in `NEXT_SESSION.md`. This is the actual product
   differentiator, so it outranks everything except the blockers above.
5. **H.265 investigation** (§4.2). We are behind a live competitor here.
6. Deprioritise the `mseFallback.ts` muxer per §3.

---

## 10. Open questions for the founder [MENS]

1. **Company or personal Play account?** Determines whether the 12-tester gate applies.
2. **Is the Play Store even the primary channel?** The accessibility declaration is a
   real rejection risk. A sideload-first launch to the TMC community is a legitimate
   alternative that keeps the self-update pipeline intact.
3. **Free tier generosity**: confirm the recommendation that the dashboard is fully free.
   It is the single biggest strategic call in this document and it contradicts the
   Google AI's advice.
4. **Name**: confirm retiring "VEPLA" so the rename sweep can happen in one pass.
5. **Does the browser actually stay usable in Drive on current firmware?** The most
   important unknown in the entire product, still untested in the real car, and Tesla's
   2026.20 parental controls suggest the surface is actively moving.

---

## 11. Sources

Market and fleet: Tesla Q1/Q2 2026 IR releases via SEC, Electrek, CNBC, Statista,
Backlinko, Tridens. MCU3 rollout: Teslarati, Electrek, Tom's Hardware, Notebookcheck
(Dec 2021). Competitors: Google Play and App Store listings for Car Cast, TeslaMirror,
Tesla Display, TesDisplay, TesAA; AppBrain install data; carcastapp.com; tesladisplay.com;
Tesla Motors Club threads. Browser video constraint: TeslaTap desired-features thread,
Tesla Motors Club, JOWUA (Jan 2026). Parental controls: Not a Tesla App (May 2026).
Play policy: Play Console Help on AccessibilityService use, permissions and APIs that
access sensitive information, app testing requirements for new personal developer
accounts, and the 15 July 2026 policy announcement. Rivian/Lucid: autoevolution,
webpronews, InsideEVs.
