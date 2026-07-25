# vehplayer - The hardware pivot

> Written at the end of session 10, immediately after the reachability
> question was finally closed (`REACHABILITY_RETHINK.md`): there is no
> address a rootless phone can present that the car will accept. This
> document reworks the concept around that fact. Evidence tags per house
> rule: MEASURED (our own car/repo), REPORTED (public source, cited),
> ASSUMED (inference, flagged).
>
> **Gating caveat, read first.** The dongle direction rests on one untested
> assumption: that the car accepts CGNAT space (`100.64.0.0/10`), which is
> not RFC1918 and therefore not covered by the measured filter. That has
> never been put in front of the car, because the mechanism that was
> supposed to deliver it (VpnService) died before any packet arrived. Until
> that test runs, everything here is a plan conditional on it. Branding, a
> pitch deck and any spend on hardware should wait for it. The plan itself
> is worth writing now because the phone-only product is dead either way.

## 1. What changed

Ten sessions were spent trying to make a phone reachable from a Tesla's
browser. Every rung of the ladder is now measured dead
(`REACHABILITY_RETHINK.md` has the table). The conclusion is not "we have
not found the trick yet", it is "IPv4 from a phone is either RFC1918, which
is filtered, or not ours to use, and IPv6 is not routable from the car at
all."

That kills the product as designed. It does not kill the product.

Everything above the transport layer is built and works: capture, hardware
H.264 encoding, the wire protocol, input injection over accessibility, the
quality ladder, the WebCodecs client, and a real dashboard with
now-playing, phone, messages and navigation. What is missing is one hop:
something the car will actually talk to.

A small Linux board provides that hop, and it does so without asking
permission from Android's sandbox, the user's carrier, or Tesla's filter
design. It can hand out an address family we choose, bind port 443, run its
own DNS, and hold a real certificate. All four are things a rootless phone
cannot do, and each of them was independently a blocker at some point in
this project.

## 2. What the pivot unlocks (the part that is easy to miss)

The forced move is not purely a cost. Three things get better, and two of
them are significant.

**iPhone stops being impossible.** The old design needed an Android sender
because it needed `MediaProjection` and a local server on the phone. An
iPhone hotspot hands out `172.20.10.1`, which is RFC1918, so iOS was
blocked by exactly the same filter. With the box providing the network,
both platforms are equally served, and the wire protocol was already
designed sender-agnostic for this (`ARCHITECTURE.md` §4: "The iOS sender
and the desktop test-harness sender speak the same protocol from day one").
The addressable market stops being "Tesla owners who use Android" and
becomes "Tesla owners". ASSUMED impact: large, since the old constraint cut
the market roughly in half.

**The phone-heat complaint can be designed out.** The single most repeated
real-user complaint about this whole product category is thermal and
battery drain ("Makes the phone very hot to the touch and consumes a good
amount of battery", `COMPETITIVE_REASSESSMENT.md` §4.4). That complaint is
a direct consequence of making the phone capture, encode and serve. Section
3 below is about how much of that work moves off the phone.

**Certificates and secure context stop being a fight.** With a box that can
bind 443 and hold a real certificate, the client is served over real HTTPS.
That restores secure context, which means `wss://`, WebCodecs and Web Crypto
all work on the page, and it retires the mixed-content workaround from
session 9 that currently forces the client to be served from the phone's
plain-http origin.

## 3. The architecture decision this opens: how much moves to the box

This is the most consequential design choice in the pivot, and it should be
made deliberately rather than by drift.

**Option A, the box is just plumbing.** It broadcasts an AP, hands out an
acceptable address, terminates TLS, and reverse-proxies to the phone. The
phone still captures, encodes and serves. Smallest change to what exists.
But the phone still runs hot, so the category's top complaint survives, and
the box is doing almost nothing for its cost.

**Option B, the box serves the dashboard.** The dashboard is a web app.
There is no reason it must be rendered on the phone and mirrored. The box
can serve it directly to the car, and the phone becomes a data source
feeding it now-playing metadata, messages, contacts and navigation state
over a light local link. That is kilobytes, not megabits.

Option B is strongly preferred, for reasons that compound:

- It is the honest expression of the project's own thesis. `CLAUDE.md` and
  `VEHPLAYER_Foundation.md` §3 both say the dashboard is the product and
  mirroring is plumbing. Option B builds exactly that. Option A keeps
  building a mirroring tool with a dashboard bolted on.
- The phone stops encoding video for the common case, so the thermal and
  battery complaint largely disappears for everything except full mirroring.
- The free tier becomes nearly free to run, since the core experience is a
  local web app plus a metadata trickle.
- Mirroring survives as the Pro feature it always should have been: the
  thing you turn on to put an arbitrary app on the screen, at the cost of
  phone battery, rather than the mechanism the whole product depends on.

The cost of Option B is that the dashboard has to be rebuilt as a web app
served by the box, rather than a native Android Activity that gets
mirrored. That is real work, but it is work against a UI the founder has
already said needs a redesign anyway (`DIFFERENTIATOR_FEATURES.md` §7: the
current layout reads as a landscape-retrofit and underuses the screen). The
two projects are the same project.

## 4. Business model, stated honestly

The founder's sketch is hardware plus a one-time app purchase, presented on
a SaaS-style landing page. Two notes.

**This is not SaaS, and calling it that will misprice it.** SaaS implies
recurring revenue for recurring service. Hardware sold once plus software
sold once is a product business with a website. That is a perfectly good
business, and the existing "buy it once, no subscription" position is a
real differentiator against Car Cast (`GROWTH_SAAS.md` §4). Do not adopt
SaaS language for a non-recurring model; it invites the comparison you win
by avoiding.

**There is exactly one line item with genuine recurring cost, and it should
carry the only recurring charge if any exists.** In-app navigation uses a
paid routing/tiles API and is not free at scale
(`COMPETITIVE_REASSESSMENT.md` §6). Everything else in the product costs
nothing per user per month once shipped. So the defensible shape is:

| Line | Type | Rationale |
|---|---|---|
| Hardware | one-time, with margin | real BOM, real fulfilment |
| App / dashboard | one-time | no recurring cost to serve |
| In-app navigation | optional recurring, or usage-capped one-time | the only real ongoing cost |
| Self-build (own Pi) | free software, no hardware sale | see §5 |

Pricing cannot be set until the hardware comparables research lands and the
CGNAT test says what the box actually has to be.

## 5. Open source, and why it should ship first

Open-sourcing the box setup (scripts, config, a flashable image) is the
right call, and not only for goodwill.

- **It is a trust requirement, not a nice-to-have.** The core promise is
  "nothing leaves the car". A closed box sitting on the user's network
  asking to be trusted with their phone's data is exactly the thing a
  privacy-conscious buyer will not accept on faith. Auditable setup is the
  proof.
- **It de-risks the launch completely.** Ship the scripts before any
  inventory exists. Tinkerers with a Pi already on the shelf validate the
  whole architecture across firmware versions, carriers and car variants,
  at zero cost and zero inventory risk. Only commit money to hardware once
  that has proven the thing works and that people want it.
- **It is materially lighter regulatory-wise, and the research came back
  worse than expected.** See §5a below. This is no longer a nice-to-have
  argument, it is close to decisive for a solo operation.
- **The precedent is well established.** Home Assistant, Pi-hole and
  Frigate all give the software away and sell convenience, support and a
  box that just works. Open core plus paid convenience is a known-good
  model for exactly this shape of product.

The split that protects the business: **open-source the plumbing, keep the
product closed.** The box configuration is commodity networking; there is
no moat in it and pretending otherwise costs trust for nothing. The
dashboard, the app and the brand are where the value is, and none of that
has to be open.

## 5a. Market evidence (session 10 research round)

Research caveat that applies to everything below: the sandbox proxy blocked
direct fetches of most primary sources, so nearly all of this is from
search-result summaries rather than pages read end to end. Treat exact
figures as indicative. Where a claim is load-bearing, re-check it.

### The headline: this exact product already exists, in the EU, at EUR 289

**REPORTED**: "Tesla Android" by Michal Gapinski is a Raspberry-Pi-class box
that broadcasts its own WiFi access point, which the Tesla browser connects
to, serving a UI over WebSocket with touch input sent back. Second
generation launched November 2025. It sells for **EUR 289** (128 GB SD) and
**EUR 309** (256 GB NVMe), with an EUR 89 upgrade kit for first-generation
owners. The software is **GPL-3.0** at
`github.com/tesla-android/android-raspberry-pi` (157 stars, 16 forks,
actively maintained through May 2026). Its stated business model is
"long-term software updates funded by hardware sales".
([teslaandroid.com](https://teslaandroid.com/products/tesla-android-2nd-generation),
[teslanorth](https://teslanorth.com/2025/11/25/tesla-android-carplay-project-debuts-2nd-gen-hardware/))

An older open-source instance of the same architecture exists at
`github.com/marcraft2/tesla-carplay`, which got mainstream coverage from
Raspberry Pi and Jalopnik.

Four consequences, and they are not all comfortable:

1. **The architecture is validated.** A shipping product proves the Tesla
   browser will accept and render a box-hosted local address. Whatever
   §6's CGNAT test returns, *something* works, and a public GPL repository
   documents what. Finding out which address family they use is now the
   single highest-value open question in this project, and it is answerable
   without a car. That investigation is running.
2. **There is a direct, funded, EU-based incumbent** running the exact
   business model this document proposes. Differentiation needs an explicit
   answer rather than an assumption that the space is empty.
3. **EUR 289 is the honest price anchor, not EUR 50.** Generic wireless
   CarPlay dongles (CarlinKit, Ottocast, AAWireless, Motorola MA1) sell at
   EUR 30 to 90, but those only convert wired CarPlay to wireless and
   **require a car that already has a CarPlay head unit, which a Tesla does
   not have**. The Tesla-specific tier, which must supply its own compute,
   WiFi and UI, runs USD 53 to EUR 309.
4. **GPL-3.0 means the Castla rule applies here too**: architecture ideas
   and factual observations about Tesla's browser are fair game, code is
   never copied or closely modelled.

### The category is real, and so is its failure mode

**REPORTED**: AAWireless (Groningen, Netherlands) has sold over 500,000
adapters, having raised over USD 7 million from 70,000+ Indiegogo backers.
CarlinKit self-reports over 10,000 units of daily output. So "people buy a
small box for their car" is well established.

**REPORTED**: what those buyers complain about is almost never features. It
is dropped connections requiring an unplug-replug, 2 to 3 second input lag,
slow boot, and phone battery drain. AAWireless publicly acknowledged being
overwhelmed by support tickets. **ASSUMED, and this is the most
transferable warning for a solo operation: support load, not manufacturing,
is what breaks small hardware sellers.**

**REPORTED, and a direct design constraint**: a Tesla owner reports a
browser-based CarlinKit unit "severely chokes internet bandwidth from
Tesla's MCU", leaving native Tesla apps unusable until unplugged. If the
car associates to our box's AP, it loses its normal internet path. Whether
the box backhauls internet to the car is a product decision that will show
up in reviews either way.

### Price anchors for the software side

**REPORTED**: Tesla Premium Connectivity is USD 9.99/month or USD 99/year.
When Tesla raised it roughly 40% in Australia and New Zealand in late 2025,
it produced measurable churn and press backlash, with owners reporting they
cancelled and "did not miss anything". **ASSUMED: USD 9.99/month is a
ceiling to price at or below, not a floor to price above.**

**REPORTED**: the loudest complaint in that episode was not the price level
but **features being removed from a previously-free tier**. That is strong
external validation of the existing "free tier is the product, not a demo,
no nag" house rule. Reputational damage in this category comes from taking
things away.

**REPORTED**: the browser, video and music streaming remain available over
WiFi with *Standard* Connectivity, so a box supplying its own WiFi keeps
the browser usable **without** the owner paying Tesla a subscription. That
removes a qualifier that would otherwise have shrunk the addressable base.

### Two platform risks worth watching closely

**Tesla is shipping native CarPlay.** REPORTED via Bloomberg's Mark Gurman
(Nov 2025, restated Feb 2026): in testing, windowed inside the Tesla UI
rather than full screen, standard CarPlay not Ultra, delayed by conflicts
between iOS Maps and Tesla's own navigation under Autopilot. Absent from
the 2026 Spring Update. ASSUMED: this removes most of the demand for the
"I want CarPlay in my Tesla" category, which is what Tesla Android,
CarlinKit T2C and SpaceBox all sell. It does **not** remove demand for a
differentiated dashboard, and there is no reporting of an Android Auto
equivalent. **This argues hard for the existing dashboard positioning and
against any messaging that leans on being a CarPlay substitute.**

**Tesla shipped a switch that turns the browser off.** REPORTED: update
2026.20 (rolling out from late May 2026) added parental controls that block
the Web Browser, Theater and Arcade per driver profile. Tesla's stated
rationale explicitly names the target: blocking the browser "prevents
drivers from using web-based workarounds to play third-party games or
stream unauthorized content while behind the wheel". ASSUMED: the browser
is intact and this is opt-in, but Tesla has now built both the UI surface
and the public rationale for switching it off. Extending that from an
opt-in parental control to a default or a driving-state restriction is now
a materially lower-effort change than it was. **Track every release from
2026.20 onward.** This is the clearest platform-risk signal this project
has seen.

### Fleet and market shape

**REPORTED** (secondhand, no primary source was readable): European Tesla
registrations fell about 27% in 2025 to 238,656 across EU+EFTA+UK, then
recovered to 170,351 in H1 2026. **ASSUMED**: the European parc is roughly
1.5 to 1.7 million, though nobody publishes that figure directly.

Three facts that actually shape the product:

- **Norway is the beachhead, by a wide margin.** Roughly 199,000 Teslas
  against about 2.91 million passenger cars, so **roughly 6.8% of every car
  in the country**. Germany has a nearly identical absolute count on a
  49-million-car fleet, i.e. 0.4%, a factor of 17 lower density. In 2025
  nearly one in five new cars sold in Norway was a Tesla. For a niche
  hardware product that depends on word of mouth inside a concentrated
  community, that difference is decisive.
- **Model 3 plus Model Y is 97 to 99% of the European fleet.**
  Compatibility testing is bounded to two cars, which matters enormously
  for a solo operation shipping hardware.
- **Roughly a fifth of the global fleet is permanently on Intel Atom
  (MCU2)**, because no MCU2-to-MCU3 upgrade path exists. That is the
  capability floor, and it will only shrink through scrappage.

## 5b. Regulatory: the reason open source has to ship first

This came back harder than expected, and it is close to decisive for a
solo operation.

**Selling an assembled WiFi device in the EU makes you the manufacturer of
radio equipment**, with no useful escape hatch:

- **REPORTED**: when Raspberry Pi asked whether the development-board
  exemption applied to them, the UK authority said that given the volumes
  and the likely-user demographic it did **not**, and even uncased developer
  units required CE marking before EU sale
  ([raspberrypi.com](https://www.raspberrypi.com/news/an-update-on-ce-compliance/)).
  The "it is just a dev board for hobbyists" argument is not available.
- **REPORTED**: integrating a pre-certified radio module does **not** exempt
  the host from radiated spurious emissions testing, because the enclosure,
  boards and nearby components change the emissions
  ([TÜV SÜD](https://www.tuvsud.com/en-gb/resource-centre/blogs/uk/testing-and-certification-blog/regulatory-compliance-for-radio-enabled-iot-devices-using-pre-certified-radio-modules)).
  Raspberry Pi's own wording is carefully hedged: a "pre-certified,
  RED-compliant core" that "significantly reduces" the integrator's work,
  not eliminates it.
- **REPORTED**: since 1 August 2025, RED articles 3.3(d)(e)(f) apply, with
  EN 18031 as the harmonised cybersecurity standard. **EN 18031 applies to
  complete internet-connected radio equipment, not individual modules**, so
  no module vendor can carry that obligation for you. It covers *your*
  firmware, update mechanism and credential handling. If the standard's
  restricted clauses bite, a notified body becomes mandatory rather than
  optional.
- **ASSUMED planning band, from lab-marketing figures that are US-skewed,
  commercially motivated and mutually inconsistent by an order of
  magnitude**: roughly **EUR 15,000 to 45,000 all-in** for a self-declared
  small WiFi product covering host EMC, radiated spurious emissions,
  safety, RF exposure, EN 18031 and retest contingency. Add roughly
  EUR 5,000 to 15,000 if a notified body is triggered. Published "EUR 3,000
  to 20,000" totals appear to predate EN 18031 and exclude retest
  contingency. No first-hand founder account of actual spend was found
  anywhere, so these are starting points for real quotes, not a budget.
- **WEEE is separately painful and per-country.** Registration is national,
  triggered by shipping a single unit into a member state, with **no
  de-minimis exemption verifiable anywhere in the EU-27**. Germany alone is
  roughly **EUR 300/year** in unavoidable fees for one brand and one device
  type (EUR 9.50 registration, ~EUR 130/year quarterly fee, plus an
  insolvency-proof guarantee at ~EUR 120-150/year), before any consultancy
  or authorised-representative fee. Article 3(1)(f)(iv) makes you the
  producer in the destination country, and Article 17 requires a paid local
  authorised representative in each member state where you are not
  established. IMPEL runs a dedicated cross-border "Article 17 Free-riders
  Project", so enforcement is organised, not theoretical.
- **Watch item**: the December 2025 Environmental Omnibus proposes
  suspending the mandatory authorised-representative requirement for
  EU-established producers until 2035. That would remove the most expensive
  per-country line, but not the registrations themselves. It is a proposal,
  actively opposed by producer-responsibility organisations, and **must not
  be planned around**.

**Correction to an earlier framing in this document**: a "kit of parts"
is **not** a reliable middle ground. The idea that individually certified
components shipped as a kit carry no new obligation rests only on forum
opinion. If you specify, bundle, brand and ship parts that a consumer
assembles into one working device, a market surveillance authority is
likely to treat you as having placed a product on the market. A kit looks
like a loophole and behaves like one.

So the ladder has two rungs, not three:

| Route | Regulatory load |
|---|---|
| Software only, user supplies their own Pi | Near zero today. No RED, no RoHS, no WEEE, no CE mark, because there is no product placed on the market. Residual: the Cyber Resilience Act, which does reach standalone software, with obligations phasing in from roughly 2026-2027 |
| Assembled branded device | Full stack: RED conformity assessment, technical file, DoC, CE mark, RoHS, EN 18031, per-country WEEE, GPSR responsible person, CRA |

**ASSUMED, and this is the strategic conclusion**: ship software-only
first. It carries near-zero regulatory load, it serves the open-source
commitment directly, it proves demand and produces real support-load data
before any inventory exists, and it lets the paid app carry revenue on its
own. Take on assembled-hardware obligations only once unit demand is
demonstrated and can fund a compliance budget. A middle path worth pricing
later is contracting an existing EU electronics fulfiller to be the legal
manufacturer while vehplayer supplies software and brand, converting a
fixed compliance project into a per-unit cost.

**Cheapest high-value next step, requiring no car and no money**: Tesla
Android is a comparable EU micro-seller shipping this exact product class.
Their published imprint, declaration of conformity and WEEE registration
would show precisely what a one-person EU operation actually files. That is
a far better answer than any consultancy estimate.

## 6. What is gated on the CGNAT test

Do not spend money or design a brand around this until the following is
MEASURED in the car:

1. **`100.64.0.0/10` acceptance.** Bring up any AP serving that range
   (laptop, spare router, travel router), join the car, open
   `http://100.64.0.1:<port>/`. Loads = the dongle direction is real and
   cheap. Refuses = Tesla blocks anything not publicly routable, the box
   needs a genuinely public address, and the cost, complexity and
   feasibility all change enough to reopen whether this is the right
   product at all.
2. **Non-standard port acceptance**, still formally open since Cloudflare
   redirected the http test. Less critical now, because a box can bind 443.
3. **Whether the car's browser will join a second AP at all** while the
   phone hotspot is also in range, and how painful that switch is in the
   car's UI. This is a UX question with real abandonment risk and nobody
   has looked at it.

## 7. Sequence

Ordered by what unblocks what, not by what is most fun.

1. **Run the CGNAT test.** Everything below is conditional on it. It costs
   an afternoon and no money.
2. **If it passes, prove the whole chain once** on borrowed hardware: box
   serving a page, car loading it, phone feeding it. That is the first
   complete round trip this project has ever had.
3. **Decide Option A vs Option B** (§3) with the founder, explicitly. The
   recommendation is B.
4. **Publish the open-source setup repo.** Scripts plus a flashable image,
   no hardware sale yet. This is the real launch, and it is free.
5. **Rebuild the dashboard as the box-served web app**, folded together
   with the redesign already identified in `DIFFERENTIATOR_FEATURES.md` §7.
6. **Only then**: branding pass, pricing, pitch materials, and a hardware
   SKU if the open-source phase shows real demand.

## 8. Provisional product pitch (one paragraph, to be rewritten after §6)

> Your Tesla has a big screen and a web browser, and almost nothing worth
> putting on it. vehplayer is a small box you plug in once. It gives the
> car a dashboard built for a car: what is playing, who is calling, what
> messages came in, and where you are going, laid out to be read at a
> glance instead of squinted at. Your phone stays in your pocket and stays
> cool. Nothing leaves the car, there is no account, no subscription and no
> ads. The setup is open source, so if you already own a Raspberry Pi you
> can build it yourself for free, and if you would rather not, we will sell
> you one that already works.

Deliberately does not mention Tesla by name in the hero line, per the
existing naming rule in `brand.json`. Deliberately leads with the screen
and the dashboard rather than with mirroring, per the category definition.
