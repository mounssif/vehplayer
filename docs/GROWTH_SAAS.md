# vehplayer - Growth & SaaS Strategy

> Companion to `VEHPLAYER_Foundation.md` §13. This is where the growth/business-model
> reasoning lives in full; Foundation keeps only the decisions that constrain
> architecture. Evidence status tags apply here too where a claim is empirical
> (MEASURED / REPORTED / ASSUMED).
>
> **Revised 19 July 2026** against real competitive/market research
> (`docs/COMPETITIVE_REASSESSMENT.md`, `docs/MARKET_AND_PRICING.md`) - several
> claims below (subscription framing, "diversification is the moat",
> near-zero marginal cost) turned out to be wrong once actual competitor
> install numbers, Mapbox pricing, and the real Play Store policy landscape
> were checked instead of assumed. Corrections are inline, marked.

## 1. What vehplayer actually is, commercially

vehplayer is not "an Android Auto clone." **It is a car dashboard, and
mirroring is plumbing** (`COMPETITIVE_REASSESSMENT.md` §3) - every
competitor in this category (Tesla Display, TeslaMirror, Car Cast,
TesAA/WebAA) mirrors the raw phone screen; none ships a purpose-built car
interface. That's the actual product and the only thing worth charging
for.

**Correction from the original framing**: this document previously
described vehplayer as "sold as reliability-as-a-service... a subscription
product, not a one-time utility." That's wrong given the real evidence -
see §4. The realistic ceiling for this category (TesAA's 10-20k lifetime
installs after 4.5 years, the best-known product in the space) is a
**self-funding niche product**, not a subscription SaaS business. Plan the
cost structure and the roadmap accordingly: near-zero-marginal-cost only
holds for the parts of the product that stay local (§4), and the
`GROWTH_SAAS.md`-as-a-company framing should stay aspirational, not
treated as the current plan.

## 2. Positioning

Primary one-liner (`VEHPLAYER_Foundation.md` §2):

> **"A car dashboard for your Tesla. Not a phone on a big screen."**

- Never in the app title or store listing title: "Android Auto",
  "CarPlay", "works with Android Auto" - the naming rule
  (`VEHPLAYER_Foundation.md` §1).
- **Relaxed deliberately for the store long description and SEO
  copy** (`COMPETITIVE_REASSESSMENT.md` §5.1): nominative comparison use
  ("an alternative to Android Auto and CarPlay for cars with a browser
  instead of a head unit") is standard practice and is how competitors are
  actually found - "android auto tesla" is the real search term. Car
  Cast's own live Play listing opens with "TESLA DOESN'T HAVE ANDROID
  AUTO. CAR CAST DOES THE JOB." and remains published. The rule that never
  bends: no logo, no implied endorsement, never in the app name/icon/title.
- Two earned supporting lines, not aspirational copy:
  - **"Nothing Google ships can turn this off."** TesAA/WebAA broke on a
    live Google Play Services update *during this session's own market
    research* - the second time this exact product line has died this
    way. vehplayer doesn't touch that protocol, so that failure mode
    doesn't exist for it.
  - **"No ads. Ever."** Earned against Tesla Display, whose screenshots
    show a bank advertisement inside a driving app.

## 3. Market entry sequencing (who buys first)

**Revised**: there is a real, time-limited launch window open right now,
and it changes the sequencing (`COMPETITIVE_REASSESSMENT.md` §4.0).

0. **The TesAA/WebAA-stranded user base, first.** Camp A's collapse
   (Google Play Services broke it, again) stranded a paying, high-intent,
   *findable* user base: people who paid $4.99 specifically to get phone
   apps onto a Tesla screen, used it daily, and now have nothing. They are
   an identifiable list of people actively looking for a replacement,
   findable in the TMC TesAA thread, TesAA's own Play Store reviews, the
   `#TeslAA` AAWireless Slack channel, and r/TeslaMotors. Two hard
   constraints on this: **never pitch into their outage threads as a
   vendor** (answering "does anything still work?" honestly is welcome,
   posting an ad gets the account banned from the exact community that
   matters), and **never promise what isn't tested** - real-car
   verification comes before any outreach, no exceptions, since recruiting
   stranded users to a product that also fails to connect burns the one
   audience that matters. This window will not stay open; either AAWireless
   finds a workaround or those users drift to the free competitors.
1. **Tesla owners without factory CarPlay/AA generally** (the broader
   existing-demand market, proven by TesAA and Castla's forum traction).
   Gate 1-4 target once the immediate window above is exploited.
2. **Tesla owners who tried Castla and bounced off Shizuku.** A named,
   findable audience (Castla's own GitHub issues and TMC threads). Free
   tier removes their exact blocker.
3. **Second-hand EV buyers** who bought a car specifically because factory
   infotainment lacked CarPlay/AA and the used-car discount reflected
   that. vehplayer is a feature they can add back for the price of an app.
4. **Other browser-capable EV brands** - **weaker than previously
   stated**: Lucid added CarPlay/AA via OTA in March 2026 and is *leaving*
   this market, and GM's Google Built-in has no usable general browser and
   isn't addressable at all (`MARKET_AND_PRICING.md` §2.3). Rivian remains
   a real but small, watch-not-build prospect. The `CarProfile`
   abstraction (`VEHPLAYER_Foundation.md` §9) stays as cheap insurance;
   don't treat this as an active near-term growth lever.

## 4. Monetization architecture

**Revised in full** (`MARKET_AND_PRICING.md` §6, `COMPETITIVE_REASSESSMENT.md`
§5) - the original free/paid split assumed a "competent free competitor"
that turned out not to exist (Tesla Display is free but rated 2.17 stars;
there is nothing functional to under-price against), and the original
near-zero-marginal-cost claim didn't account for in-app navigation's real
cost.

| Tier | Price | Contents |
|---|---|---|
| **Free** (the product, not a demo) | $0 | The full `CarDashboardActivity` experience - dashboard, now-playing, phone, messages - plus mirror mode with touch control. No time limit, no watermark, no nag. Deliberately more generous than Car Cast's 1h/month, because the free tier's job is to beat the *free* competitor (Tesla Display), not to squeeze the paid one. |
| **Pro, one-time purchase, €9.99** (test €7.99/€12.99) | €9.99 | In-app navigation (see cost note below), screen-off/virtual display Power Mode (Shizuku), low-latency or lossless browser audio (Route B), auto-connect (BLE + auto-hotspot), split view, multi-car profiles. |
| Fleet/Dealer | - | **Explicitly speculative, not roadmap** (see below). Do not build against this before Gate 6, and do not present it as a near-term plan in anything investor-facing. |

**Why one-time, not subscription** (correction from the original
"subscription or one-time+updates, price-test both" framing): a
subscription creates a support obligation a solo-maintained product can't
staff, it's the exact objection that dominates competitor reviews ("it
will break and I will still be paying"), and it's a direct counter-position
to Car Cast ("Buy it once. No subscription."). Reserve subscription pricing
for a genuine ongoing-cost feature - none currently exists, because the
data plane is local.

**Correction to "near-zero marginal cost"**: this was stated as a
permanent architecture constraint. It holds for the mirroring/dashboard
core (data plane never touches our infrastructure) but **not** for in-app
navigation - Mapbox's Navigation SDK free tier is 100 MAU + 1,000 trips,
metered pricing above that runs roughly $0.30/MAU + $0.08/trip, and a
2,000-MAU app doing 25,000 trips/month lands near $2,470/month. That's why
navigation moved to Pro (§ table above) - paying users fund their own map
costs, free users get a "Navigate tile launches Waze/Google Maps" path
instead of in-app rendering, which is genuinely zero marginal cost and
needs no new code (the mirrored app just appears on the car screen through
the existing pipeline). Whichever path ships, this document and
`MARKET_AND_PRICING.md` §2.2 both need to keep saying "near-zero marginal
cost for the mirroring/dashboard core" rather than an unqualified blanket
claim.

**Consider adopting TeslaMirror's refund-window model** instead of (or
alongside) a free trial: "if it doesn't work in your car, email within
seven days for a refund." More persuasive than any free tier at answering
this category's real objection ("will it even work in mine"), costs
almost nothing at this volume, and doesn't degrade the product for anyone.

**Realistic revenue expectation, stated plainly** (`MARKET_AND_PRICING.md`
§6.3): at 5,000 installs and 5% conversion at €9.99, roughly €2,100 net of
Google's fee. At 15,000 installs and 8% conversion, roughly €10,200. This
is a real, useful, self-funding niche product - not a company. Treat any
"Fleet/Dealer tier" or "reliability-as-a-service" framing as an explicitly
speculative appendix, not as the plan, until a second real data point
(a second `CarProfile`, real recurring revenue) exists.

## 5. The moat: protocol independence + a purpose-built dashboard

**Retitled and revised** from "diversification, not a trick" - the
diversification argument is weaker than originally stated (see §3 item 4:
Lucid left the market, GM isn't addressable). The moat that actually holds:

- **Protocol-level**: no dependency on Android Auto/CarPlay protocols, so
  there is nothing for Google or Apple to revoke
  (`VEHPLAYER_Foundation.md` §4 Lesson 1). This is now *proven by someone
  else's outage*, not just an engineering principle - say so in marketing
  (§2).
- **Category-level** (new, replaces the diversification argument as the
  primary structural advantage): every real competitor mirrors the raw
  phone screen; none has built a purpose-built dashboard
  (`VEHPLAYER_Foundation.md` §3). This is a genuine product gap, not a
  feature gap, and it's harder to copy than a codec improvement.
- **Distribution-level**: dual channel (Play Store + first-party signed
  APK, `VEHPLAYER_Foundation.md` §8) so a single store policy change
  cannot kill distribution.
- **Reachability-level**: the fallback ladder (`VEHPLAYER_Foundation.md`
  §6b) means a single Tesla firmware change degrades a tier, not the
  product. Both real shipping competitors (Tesla Display, TeslaMirror) use
  the same VpnService/CGNAT-address approach in production for years -
  proven ground, not a guess (`COMPETITIVE_REASSESSMENT.md` §4.3).
- **Brand-level diversification (Gate 6+, now explicitly weaker)**: kept
  as cheap insurance via the `CarProfile` abstraction, not treated as an
  active near-term lever - see §3 item 4.
- **Template-level (new, session 10)**: Android Auto and CarPlay confine
  every third-party app to a small fixed set of host-rendered templates
  (no custom layout, no arbitrary images/video, no animation, capped
  theming) with a video/game/browser category ban baked into Google's own
  quality guidelines. vehplayer's in-car UI is a full web page, not a
  template consumer, so this is a structural gap neither incumbent can
  close without abandoning their own driver-distraction policy. See
  `docs/DIFFERENTIATOR_FEATURES.md` for the researched restriction list,
  concrete feature ideas this unlocks, and the Park-only gating those
  features still need under the same distraction-safety reasoning AA
  itself applies.

This is the actual answer to Failure Mode #1 (Tesla ships native AA/CarPlay
support): if that happens, Tesla-specific revenue erodes, but the product
does not, because it was never architecturally a "Tesla app," and it was
never *just* a mirroring tool either.

## 6. Go-to-market sequencing

1. **Gate 0 (before anything else): real car verification.** A `/go`
   round trip has still never been completed in an actual Tesla, across
   every session so far. Nothing below this line should be acted on until
   this passes - recruiting the stranded TesAA users (§3 item 0) to a
   product that also fails to connect burns the one audience that matters.
2. **Immediate, once verified: the TesAA-stranded launch window** (§3 item
   0) - this is not a Gate 4 activity, it's happening now and is time-limited.
3. **Gate 1-3 (build in stealth otherwise)**: no public marketing spend
   beyond the window above. The compatibility telemetry and latency
   harness are themselves content assets being built for later (the "does
   my car work" matrix).
4. **Gate 4 (private beta)**: recruit from TMC forums and the Tesla
   subreddit more broadly, same channels, same audience as §3 item 0-2.
   Direct outreach to visible Castla GitHub issue authors is a legitimate,
   low-cost recruiting channel. Pricing survey embedded in the beta.
5. **Gate 5 (company layer)**: publish the compatibility matrix as a
   public page (transparency-as-marketing). Comparison-SEO pages go live
   here, governed by §2's naming constraint.
6. **Gate 6 (launch)**: paid acquisition only once organic/community
   channels are measured. Second `CarProfile` research starts here, not
   before, and only if a second brand becomes genuinely addressable
   (unlike Lucid/GM, see §3 item 4).

## 7. Failure-mode watch specific to growth

- **Overexpansion before Gate 4**: chasing a second car brand, a fleet
  product, or paid ads before Tesla is daily-drivable is the single most
  likely way to kill this via unfocused scope. Treat any pressure to do
  this as a scope-creep tripwire (`VEHPLAYER_Foundation.md` §12,
  `NEXT_SESSION.md`).
- **Pricing anchored to TesAA's willingness-to-pay evidence** ($4.99 for a
  stale, 3.5-star, now-broken product), not to a free competitor's
  existence - a free competitor being *bad* (Tesla Display, 2.17 stars) is
  not the same as no free competitor existing, and pricing must account
  for both facts at once (§4).
- **Store risk (AccessibilityService policy, `VEHPLAYER_Foundation.md`
  §8)** is a growth risk, not just a technical one: any Gate 5 GTM plan
  must assume the first-party APK channel carries real distribution
  weight, and per §3 item 0, it's also the channel that reaches the
  TesAA-stranded users first since it needs no store review.
- **Discovery is broken across this entire category**
  (`COMPETITIVE_REASSESSMENT.md` §2): an engaged, paying TesAA customer
  never encountered Tesla Display's 40,000-install free app. Build quality
  alone does not solve distribution - it currently has zero hours
  allocated against it beyond §3/§6 above, and that's a real gap, not
  "we have no competition."

## 8. Open questions for Gate 4 (not decided, flagged honestly)

- Exact Pro price point: €9.99 is the working number
  (`MARKET_AND_PRICING.md` §6.2), €7.99/€12.99 are the test bounds, not
  decided.
- The navigation cost decision (§4): in-app Mapbox rendering as the Pro
  hook vs. a free "launches Waze/Google Maps" tile - **[MENS]**, blocks
  finalizing the free/Pro feature split.
- Whether "low-latency audio" is a strong enough standalone Pro hook, or
  needs TeslaMirror's sharper "lossless audio vs Bluetooth" framing
  (`COMPETITIVE_REASSESSMENT.md` §4.1) to land.
- Company vs. personal Play developer account (`MARKET_AND_PRICING.md`
  §7.3) - determines whether the 12-tester/14-day gate applies at all.
  **[MENS]**.
- Fleet/Dealer tier viability is genuinely unresearched and now explicitly
  downgraded to a speculative appendix (§4) - do not build against it
  before Gate 6.
