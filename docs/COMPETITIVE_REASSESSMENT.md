# vehplayer , Competitive Re-Assessment, Branding and Session Brief

> **Supersedes §4 and §6 of `MARKET_AND_PRICING.md`.** Everything else in that
> document (fleet sizing, the canvas-versus-video constraint, Play Store policy,
> the WebAA collapse) still stands and is not repeated here.
>
> **Why this exists.** The founder challenged the previous competitive read: "TeslaMirror,
> Tesla Display and Car Cast are not the problem, they just cast a screen and spam ads,
> and in three years of Tesla ownership I had never heard of them." That is a specific,
> testable claim. It was tested. **The founder is substantially right, and the previous
> analysis was too generous to these products.** The corrected version is below, with the
> numbers that settle it, plus the one part of the founder's reasoning that points at a
> real risk rather than away from one.
>
> Research date: 19 July 2026. Evidence tags as usual: MEASURED, REPORTED, ASSUMED.

---

## 1. The numbers that settle the argument

All REPORTED from AppBrain and live Play listings, July 2026.

| Product | Live since | Total installs | Last 30d | Rating | Price | State |
|---|---|---|---|---|---|---|
| **Tesla Display** | Dec 2022 | 40,000 | 750 | **2.17** (140 ratings) | Free, ad-supported | Alive, poorly rated |
| **TeslaMirror** | Dec 2020 | 15,000 | 220 | **2.91** (240 ratings) | $5.99 | Alive, actively updated |
| **TesAA / WebAA** | Oct 2021 | 10,000-20,000 | ~780 | 3.53 (622 ratings) | $4.99 | **Broken** (Play Services) |
| **Car Cast** | ~2023 | not published | n/a | n/a | Freemium + sub/lifetime | Alive, cross-platform |

Three things fall out of this table.

**First: the founder's read was correct.** Not one product in this category has ever earned
a passing grade. A 2.17 and a 2.91 are not "competent competitors", they are products that
most users try once and abandon. The previous document called Tesla Display "the highest
threat" because it is free and has touch control. On the numbers, a free product that
40,000 people installed and rated 2.17 is not a threat, it is a demonstration that free
and functional are different things.

**Second: this is a side project, not a company.** Tesla Display's developer (Kan Huang,
trading as Super Ratel, Beijing) also publishes MSR Bluetooth, EasyMSR USB, BTMSR and
MSR880, which are magnetic stripe card reader utilities. REPORTED. Tesla casting is one
item in an unrelated portfolio. The ad banner the founder screenshotted (Keytrade Bank,
inside a driving app) is consistent with that: this is monetised by ad network, not by
caring about the product.

**Third, and this is the uncomfortable one: the whole category has produced roughly 65,000
to 75,000 installs in five and a half years, across every player combined.** That is the
honest size of proven demand. It can be read two ways and both readings are defensible:

- *Optimistic*: nobody has ever shipped a good product here, so demand has never actually
  been tested. Every install in that table is a person who wanted this enough to find an
  obscure app, and then got something rated 2.2 stars. Latent demand is larger than
  revealed demand.
- *Pessimistic*: the market is simply small, and five years of multiple teams trying is
  evidence rather than coincidence.

The truth is probably that the optimistic reading is right about *quality* and the
pessimistic reading is right about *scale*. Plan for a good product in a small market, not
a good product in a big one. The pricing consequence is in §5.

---

## 2. "I never heard of them in three years"

This is the most important sentence in the founder's message, and it does not mean what it
looks like it means.

It is offered as evidence that these products are not competition. It is much stronger
evidence of something else: **discovery in this category is broken.** An engaged Tesla
owner, actively motivated enough to find and pay for TesAA, never encountered a free app
with 40,000 installs that solves an overlapping problem. That is not a story about Tesla
Display being irrelevant. That is a story about nobody in this category being findable.

Two consequences, and they pull in opposite directions:

- **Opportunity**: the channel is unowned. There is no incumbent brand, no default answer,
  no "just use X" in the forums. Whoever does community presence and search properly first
  takes the category, and none of the current players are even trying. Tesla Display's
  store listing is a wall of app names. TeslaMirror's is a setup manual.
- **Risk**: build quality does not solve discovery. vehplayer could be objectively the best
  product in this category and still reach nobody, exactly as Tesla Display reached 40,000
  people the founder never heard of. **Distribution is a harder problem here than
  engineering, and it currently has zero hours allocated against it.**

Do not file this under "we have no competition". File it under "the hard part is not the
part we are good at". The TesAA collapse (`MARKET_AND_PRICING.md` §4.0) is the single best
distribution opening this category has ever had, and it is temporary.

---

## 3. Category correction: vehplayer is not in the same product category

The founder's underlying point is right and should be written into the Foundation, because
it changes what the product is:

**Every product in that table is a mirror utility. They take the phone screen and put it on
the car screen. vehplayer is building a car dashboard, and the mirroring is plumbing.**

The founder's own screenshots make the difference legible without any analysis:

- TeslaMirror's marketing image shows a portrait phone letterboxed into a landscape car
  screen, with `http://3.3.3.3:3333/` in the address bar. MEASURED (founder screenshot).
  The product is literally "your phone, sideways, with an IP address above it".
- Tesla Display's settings screen offers "Disable SSL", "Try MSP IP", "Control on
  touchscreen", "Optimization goal". MEASURED (founder screenshot). This is a debug panel
  shipped as a settings page.
- vehplayer's dashboard shows a now-playing card and three destinations, with an in-app map
  and turn-by-turn. MEASURED (emulator screenshots).

So the competitive frame is not "who wins the mirroring feature". It is: **mirroring is the
commodity these products fight over, and vehplayer should stop fighting there.** That is
what the session-4 pivot already did by instinct.

**But they remain competitors in three specific ways, and pretending otherwise would be a
mistake:**

1. **Search results.** They occupy "tesla android auto", which is the query real users type.
2. **Price anchoring.** $4.99 and $5.99 set the ceiling regardless of relative quality.
3. **Category reputation.** After 2.17 and 2.91 stars, anyone who has tried this category
   arrives expecting junk. The first ten seconds of vehplayer's onboarding are carrying
   that baggage, whether or not it is fair.

---

## 4. What is worth taking from them

The founder asked what is useful in what they have built. This is the honest list, and some
of it is genuinely valuable because it is calibration data we do not have.

### 4.1 TeslaMirror, take this seriously

TeslaMirror is the one product here that deserves respect. It has shipped for five and a
half years, is on version 9.72, and publishes per-firmware release notes. REPORTED.

- **Per-MCU codec calibration, which we do not have.** Their published recommendations:
  MCU3 uses H.265 at 720p60 (they note 1080p needs more bandwidth than is comfortable),
  MCU3 tops out at 1080p60 H.264 or 1080p30 H.265, and MCU2 should run H.264 at 540p30.
  Since Tesla software 2025.38.11, H.265 works on MCU2 as well. REPORTED. **This is
  somebody else's MEASURED data covering exactly the calibration our `ARCHITECTURE.md` §2
  still marks ASSUMED.** Treat it as a strong prior to verify, not as truth, but it is far
  better than the current guesses. Note in particular that their MCU2 recommendation of
  540p30 is dramatically lower than our 720p30 default.
- **Wi-Fi gotcha**: they recommend a 5 GHz hotspot and state that Tesla vehicles do not
  support Wi-Fi 6 access points. REPORTED. If true this is a real support-ticket saver and
  belongs in our onboarding checks.
- **A short domain for the car.** They serve at `TSL6.com`. Eight characters, typed on a
  car touchscreen with no keyboard shortcuts. Ours is `veh.modev.be`, which is twelve and
  contains two dots. **This is a genuine UX feature disguised as a domain choice.** See §7.
- **The 144-hour refund window.** Buy the app, get six days, email for a refund if it does
  not work in your specific car. REPORTED. This is a smart answer to the category's core
  objection ("will it even work in mine"), and it is strictly better than a crippled free
  tier because it does not degrade the product for anyone.
- **Lossless audio as a premium hook**: up to 96 kHz stereo 32-bit, positioned explicitly
  against Bluetooth quality. That is a sharper framing of our Route B than "low latency".

### 4.2 Tesla Display

- **"Forward another phone screen"**: cast from a second phone through the first. MEASURED
  (founder screenshot). Niche, but a genuinely clever family or passenger feature.
- **Their Play Store accessibility disclosure survived review.** Their listing carries the
  explicit language: the app uses `dispatchGesture` and `performGlobalAction`, the purpose
  is remote control from the Tesla touchscreen, and no data is collected through the
  AccessibilityService API. REPORTED. **This is important reassurance for
  `MARKET_AND_PRICING.md` §7.1**: two live apps in this exact category, doing this exact
  thing, are published on Play right now. The declaration is passable. Use their wording as
  a starting template, not as a copy.

### 4.3 Both of them, confirming our architecture

Both solve the RFC1918 block with a VpnService assigning an address outside the private
ranges, TeslaMirror explicitly at `100.99.9.9`, which is the CGNAT range. REPORTED. That is
tier (b) of our reachability ladder, in production, in two shipping products, for years.
Good confirmation that `VpnReachabilityService` is worth finishing, and that the founder's
peer-to-peer decision this morning was the right call rather than a limitation.

### 4.4 What their one-star reviews tell us to build

This is the reliability checklist, taken from real reviews. REPORTED.

| Complaint | What it means for us |
|---|---|
| "Doesn't work as advertised. Only works while parked. As soon as you put your Tesla in Drive the browser is disabled" | Drive-mode behaviour is the single most confusing thing in this category and it may vary by region and firmware. Our onboarding must state plainly what works in motion and what does not |
| "I can't even get the screen share to start even after watching the tutorial video" | Setup failure is the number one killer. Every failure state needs a specific, actionable message, never a spinner |
| "Makes the phone very hot to the touch and consumes a good amount of battery" | Thermal is a real product problem, not a footnote. Already flagged in Foundation §7, now confirmed by user reports |
| "Shows up for a few seconds then crashed. Tried a different phone, with Pixel 8, 1000 times better" | Encoder behaviour varies wildly by device. Our H264Encoder fallback path was the right instinct, and a device compatibility list is a real deliverable |
| "Can't get the voice for maps directions on the car speaker" | Audio routing confuses people. Route A versus Route B needs to be a plain-language choice, not a toggle labelled with our internal names |
| "Has ads posing as the app inside the app" | Free and ad-free is a differentiator we get for nothing |

---

## 5. Revised positioning and pricing

### 5.1 Positioning

Unchanged from `MARKET_AND_PRICING.md` §5 in substance, sharpened by §3 above:

> **"A car dashboard for your Tesla. Not a phone on a big screen."**

Two supporting lines now carry evidence behind them rather than aspiration:

- **"Nothing Google ships can turn this off."** Earned by the WebAA collapse.
- **"No ads. Ever."** Earned by the competitor that puts a bank advert in a driving app.

**Session 10 addition**: a third supporting line, aimed past the direct
mirroring competitors above and at the two incumbents this whole category
implicitly gets compared to - **"No fixed templates. If your phone can
render it, your car can show it."** This isn't aspirational copy; it's
backed by researched, cited restrictions in Android Auto's and CarPlay's
own developer guidelines (no custom layout, no arbitrary images/video, no
animation, one UI template category per app) that a browser-rendered
dashboard simply isn't subject to. Full research, concrete feature ideas
this unlocks, and the driver-distraction gating those features still need
live in `docs/DIFFERENTIATOR_FEATURES.md` - do not use this line in
marketing without also shipping the Park-only gates that document
specifies, or the honest positioning becomes the exact distracted-driving
pattern AA/CarPlay's restrictions exist to prevent.

### 5.2 Pricing, revised

The previous recommendation (free dashboard, Pro at €9.99 one-time) assumed a "competent
free competitor" that does not exist. With that premise corrected, the pricing changes:

**Do not give away the dashboard to beat a 2.17-star app.** There is nothing to beat.

Revised model:

- **Free**: mirror mode with touch control, plus the dashboard in a limited form (for
  example, now-playing and phone, but not navigation). Enough to prove it works in *their*
  specific car, which is this category's real objection.
- **Pro, one-time, €9.99**: the full dashboard including in-app navigation, plus screen-off
  mode, low-latency or lossless audio, auto-connect, split view, multi-car profiles.
- **Adopt TeslaMirror's refund window rather than a time-limited trial.** A published
  "if it does not work in your car, email us within seven days for a refund" is more
  persuasive than any free tier, costs almost nothing at these volumes, and directly
  answers the objection every review in this category is really about.

This also resolves the cost problem in §6, which the previous pricing model did not
account for at all.

**Unchanged**: one-time rather than subscription, and "buy it once, no subscription" as a
counter to Car Cast. Nothing found since changes that reasoning.

---

## 6. New finding: in-app navigation is not free

The dashboard screenshots show a Mapbox map with real turn-by-turn routing (a 101.2 km
route to Tuinstraat, 77 minutes). MEASURED. That is not a free component, and the previous
document's "near-zero marginal cost" claim did not account for it.

Mapbox free tiers, REPORTED: 25,000 monthly active users for mobile Maps SDK, but only
**100 MAU plus 1,000 trips** for the Navigation SDK under metered pricing, and 50,000 web
map loads. Above that, Navigation SDK metered runs roughly $0.30 per MAU plus about $0.08
per trip, with Directions billed separately per 1,000 requests.

Concretely: an app with 2,000 monthly active users doing 25,000 trips lands near $570 in
MAU charges plus roughly $1,900 in trip charges per month. That is a real business, not a
rounding error, and it arrives exactly when the product starts working.

**Decision required before launch [MENS], three viable paths:**

1. **Navigation is the Pro feature** (recommended, and the reason §5.2 changed). Paying
   users fund their own map costs, free users get the launcher path below.
2. **The Navigate tile launches Waze or Google Maps** rather than rendering in-app. Zero
   marginal cost, and it is what the founder described as the intent for the other tiles
   anyway. The mirrored app then appears on the car screen through the existing pipeline,
   which needs no new code at all.
3. **Self-hosted routing** (Valhalla, OSRM, GraphHopper) with OSM tiles. Removes the
   per-trip cost, adds real operational burden. Hard to justify at this scale.

Whichever is chosen, **`GROWTH_SAAS.md` and `MARKET_AND_PRICING.md` §2.2 both claim
near-zero marginal cost and must be corrected**, because with option 1 or 3 it is no longer
strictly true.

---

## 7. Branding, currently absent

The founder is right that there is no branding anywhere. Everything below is a Claude
decision with a stated revision trigger, per the house rules.

### 7.1 Name

**Lock `vehplayer` and retire `VEPLA` completely.** Reasoning in `MARKET_AND_PRICING.md`
§8: VEPLA collides with an unrelated Swedish B2B app, and the repo currently carries two
names, which has already caused confusion (the phantom "v1 Foundation" problem). Rename
`docs/VEPLA_Foundation.md` to `docs/VEHPLAYER_Foundation.md` in the same sweep.

Revision trigger: a trademark screening that finds a conflict.

### 7.2 The car domain is a product decision, not an admin one

`veh.modev.be` is twelve characters with two dots, typed on a car touchscreen, by someone
sitting in a parked car who is already unsure this will work. TeslaMirror uses `TSL6.com`.
Tesla Display uses a raw IP. TesAA used `androidwheels.com`.

**Recommendation [MENS]: register a short dedicated domain before launch.** Four to eight
characters, no hyphens, ideally no subdomain. This is worth real money and almost no effort,
and it is the first thing a new user touches. `veh.modev.be` also carries a personal
domain in the product surface, which reads as hobbyist.

### 7.3 Visual identity, already half-decided by the build

The dashboard has made real choices already and they are good ones. Formalise rather than
redesign:

- **Warm dark with amber and gold accents.** Deliberately not the cool blue-grey that every
  CarPlay and Android Auto clone uses. This is genuinely distinctive in the category and
  should be treated as the brand, not as a theme.
- **Space Grotesk** for display type, already OFL-licensed and vendored.
- Formalise both into `brand.json` at repo root (per `MARKET_AND_PRICING.md` §9.2) so the
  Android app, the web client and the future site all read one source.

### 7.4 What is missing and should be built this session

- **App icon.** None exists. Needs to work at launcher size and in a Play listing.
- **Wordmark** for the store listing, the site and the car-side connect screen.
- **The dashboard shows `http://10.0.2.15:8080/go` on the product surface.** MEASURED
  (screenshot). A raw local IP is developer output, not product. It should either be hidden
  once paired or replaced with the short domain and a pairing code.
- **The hero now-playing card occupies most of the screen and its empty state says "Nothing
  playing".** That is a large amount of the most valuable real estate spent on a null state.
  Consider collapsing it when idle and promoting Navigate, since navigation is the thing
  people actually glance at while moving.
- **Store assets**: feature graphic, screenshots, short and long description. The long
  description is where the nominative "alternative to Android Auto" copy lives, per
  `MARKET_AND_PRICING.md` §5.1.

---

## 8. Session brief

### 8.1 Confirmed correct, do not revisit

The peer-to-peer decision made this morning was right. A WebSocket relay through a
Cloudflare Worker would have put video and audio bytes through cloud compute, which breaks
Foundation §6 regardless of whether anything is stored, and it would have destroyed the one
claim no competitor in §1 can make. Tier (a) GUA IPv6 hardened and prioritised, with tier
(c) `VpnReachabilityService` as the floor, is the correct ladder. Both shipping competitors
use the tier (b)/(c) approach in production, so it is proven ground.

The concrete bug found alongside it (`awaitHttpServerAndShowUrl()` using `localIpAddress()`
IPv4 and ignoring the GUA IPv6 that `ReachabilityLadder.decide()` already resolved) is
exactly the kind of thing that makes tier (a) look broken when it is not. Good catch.

### 8.2 Priority order

0. **Real car verification.** Still never done. Everything else is speculative until a
   `/go` round trip completes in the actual Model 3. [MENS] for the car.
1. **Accessibility disclosure screen.** Blocks the Play listing. Two competitors have
   passed review with published wording (§4.2), so this is a known-solvable task.
2. **Build flavours** splitting `playRelease` from `sideloadRelease`, so the self-update
   pipeline does not sink the store submission.
3. **Navigation cost decision** (§6). This blocks pricing, which blocks the store listing.
4. **Branding pass** (§7): `brand.json`, app icon, wordmark, remove the raw IP from the
   dashboard, short domain [MENS].
5. **Recalibrate the encoder against TeslaMirror's published numbers** (§4.1), especially
   the MCU2 recommendation of 540p30, which is far below our current default.
6. `targetSdk` 36 before the 31 August deadline.
7. Accessibility and handsfree audit of the car UI, already on the Claude Code list.

### 8.3 Documents to correct

- `MARKET_AND_PRICING.md` §4 and §6: superseded by this document.
- `MARKET_AND_PRICING.md` §2.2 and `GROWTH_SAAS.md`: remove the near-zero marginal cost
  claim, or qualify it, per §6.
- `VEPLA_Foundation.md`: rename, retire the VEPLA name, add the category definition from §3
  ("we are a dashboard, mirroring is plumbing") as a settled decision.

---

## 9. Sources

AppBrain install and rating data for Tesla Display (`io.github.blackpill.tesladisplay`) and
TeslaMirror (`com.hustmobile.teslamirror`); live Google Play listings for both, plus
Hustmobile's developer page; apkcombo and apkpure listing mirrors for full store copy;
tesladisplay.com; carcastapp.com; Mapbox published pricing and Navigation SDK Android
pricing docs, plus Woosmap and checkthat.ai pricing breakdowns; founder-supplied
screenshots of both competitor apps and of the current vehplayer emulator build (MEASURED).
