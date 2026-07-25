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
- **It is materially lighter regulatory-wise.** Selling an assembled
  WiFi-emitting device in the EU pulls in CE marking, the Radio Equipment
  Directive, WEEE, RoHS and GPSR obligations. Publishing software that
  people run on hardware they already own pulls in essentially none of
  that. For a solo operation this difference is not a detail, it may be the
  difference between shipping and not. (Being researched; treat as ASSUMED
  until that report lands.)
- **The precedent is well established.** Home Assistant, Pi-hole and
  Frigate all give the software away and sell convenience, support and a
  box that just works. Open core plus paid convenience is a known-good
  model for exactly this shape of product.

The split that protects the business: **open-source the plumbing, keep the
product closed.** The box configuration is commodity networking; there is
no moat in it and pretending otherwise costs trust for nothing. The
dashboard, the app and the brand are where the value is, and none of that
has to be open.

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
