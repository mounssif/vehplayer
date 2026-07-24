# vehplayer - Reachability rethink (session 10, Opus 5 pass)

> Written after the founder asked for a fresh strategic look: what has been
> overlooked, what options remain, and what still gates a public release.
> This is an analysis document, not a build spec. Evidence tags per house
> rule: MEASURED (verified on our own car/repo), REPORTED (public source,
> cited), ASSUMED (inference, flagged).

## 1. The blind spot: ten sessions changed one variable and held three fixed

Every in-car connection attempt in this project's recorded history, pulled
from `NEXT_SESSION.md`:

| Attempt | Address | Scheme | Host form | Port |
|---|---|---|---|---|
| Session 6-9 | `10.118.219.223` (RFC1918) | `http://` | bare IP literal | 8081 |
| Session 7 | `100.99.9.1` (CGNAT via VPN) | `http://` | bare IP literal | 8080/8081 |
| Session 10 | `2a02:a020:...:5ff1` (IPv6 GUA) | `http://` | bare IP literal | 8080 |

**Only the address ever changed. The scheme was always plain `http://`, the
host was always a bare IP literal, and the port was always non-standard.**
Confirmed against the code: `HttpAssetServer` is NanoHTTPD with no TLS,
`DEFAULT_HTTP_PORT = 8080` with 8081/8082/8083 fallbacks, WS on 8787. There
is no TLS path and no DNS responder anywhere in `android/` (MEASURED, this
repo).

Meanwhile, the one URL shape that is **proven to work in the car, many
times over** is the exact inverse: `https://veh.modev.be/...` - real
hostname, real publicly-trusted certificate, standard port 443. That is how
`probe-webrtc.html`, `diag.html` and `video-test.html` all loaded in the
car, including at highway speed (MEASURED, session 10). tiktok.com and
youtube.com likewise.

So the configuration known to work has never been pointed at the phone, and
the configuration pointed at the phone has never been one the car is known
to accept. That is the blind spot.

## 2. What the new error code actually tells us

Session 9's RFC1918 failures were `ERR_CONNECTION_REFUSED`. Tonight's IPv6
failure was `ERR_ACCESS_DENIED`. **Different code means a different rule
fired**, which is a real signal, not noise.

Two corroborating details from the same night's screenshots:
- The connect-info overlay read `reached from network: HTTP 0x / STUN 0x` -
  the phone's own request counters stayed at **zero**. No packet arrived.
- The failure was immediate, not a timeout.

Together: **the car's browser refused this request before emitting a
packet.** It is a client-side policy denial, not a network-path failure.
That is good news, because a policy denial is a decision about some
*property of the URL*, and there are only four candidate properties, three
of which have never been varied (see the table above).

Note this also means the IPv6 address itself is **not proven guilty**. It
is one of four suspects, and the only one we happened to change.

## 3. The test that resolves this, with zero code changes

Session 9 cracked the previous blocker class with a laptop-vs-car A/B on an
identical URL. The same method applies, and none of it needs a new build.
Run in the car, in one sitting, reading the result off the screen:

1. **Laptop control over IPv6.** From a laptop on the hotspot, open the
   *same* `http://[GUA]:8080/diag` that the car refused. This was never
   done - session 9's laptop control was over RFC1918 only. If the laptop
   also fails, the phone is not reachable on that address at all and this
   was never a Tesla-policy question. **Do this first; it is the cheapest
   and it invalidates everything below if it fails.**
2. **Scheme.** Not directly testable without TLS on the phone, so infer it
   from 3 and 4 first.
3. **Port.** Try a known-good public site on a non-standard port from the
   car (any public `http://host:8080/` test endpoint). Loads = port is
   innocent.
4. **Bare IP literal.** Try a public site by raw IP from the car
   (`http://<public-ip>/`). Loads = IP-literal form is innocent.
5. **Capture the car's real Chromium version** while there:
   `veh.modev.be/video-test` prints it in its env line. The "Chromium 140"
   figure in our docs is stale and was mis-corroborated once already.

Four cheap observations narrow a 4-suspect problem to 1. Everything in §4
depends on which suspect survives.

## 4. The architecture this points at: real hostname, real certificate, local address

If the denial is scheme, host-form, or port related (i.e. anything except
"all IPv6 is blocked"), then the fix is to stop asking the car to do the
one thing it has never been willing to do, and instead hand it the exact
shape of URL it already accepts every day:

```
Car opens:   https://<device-id>.<our-domain>:8443/go
DNS (public) resolves that name  ->  the phone's current IPv6 GUA
TLS          validates against a real, publicly-trusted certificate
Transport    car -> phone, directly over the hotspot link
Media        never leaves the local link
Cloud        answers a DNS query and nothing else
```

Why this is materially different from everything tried so far:

- **Not a bare IP literal.** It is an ordinary hostname.
- **Real HTTPS with a publicly-trusted certificate.** This sidesteps the
  known dead end from `MEDIAMTX_HLS_RESEARCH.md` §4: self-signed
  certificates are unusable because there is no CA-install path on the
  Tesla browser. A real certificate needs no install.
- **It is a secure context**, which fixes a second, separate problem the
  project already hit: session 9's `DOMException: The operation is
  insecure` forced the client to be served from the phone's plain-http
  origin because an https page cannot open `ws://`. With real TLS on the
  phone, `wss://` works, and WebCodecs, service workers and Web Crypto all
  become available on that page.
- **The data plane stays local.** The cloud answers a DNS query. It never
  sees a frame. This is squarely inside the standing rule ("cloud is
  control plane only, never media") and is *not* the cloud relay rejected
  in session 6, which would have carried media.

**Honest gates on this design, none of them hand-waved:**

- **Port 443 is unbindable.** Android forbids apps binding ports below
  1024 without root, so this must be `:8443`. Chromium's blocked-port list
  does not include 8443 (REPORTED), but whether *Tesla's* filter cares
  about non-standard ports is exactly test 3 above. If Tesla requires 443,
  no rootless Android app can satisfy it, and the dongle (§6) becomes the
  only route.
- **Certificate custody.** Shipping a wildcard private key inside the APK
  is genuinely unsafe: it is extractable, and whoever extracts it can
  impersonate that domain. Two acceptable variants: use a **dedicated
  throwaway domain** whose compromise costs nothing and never touches
  `modev.be`, or provision **short-lived per-device certificates** through
  the control plane. The second is cleaner and more work. Do not ship a
  long-lived wildcard key in a public APK.
- **Dynamic address.** The phone's GUA changes on re-association, so the
  AAAA record needs a dynamic-DNS style update from the phone. That is a
  tiny control-plane call, consistent with the architecture.
- **Let's Encrypt rate limits** on a per-device certificate fleet, already
  flagged as an open question in `MEDIAMTX_HLS_RESEARCH.md` §7.
- **A phone-local DNS responder is the more elegant variant** (the phone
  serves DNS to its own hotspot clients, so even DNS stays local) and is
  already floated in `ARCHITECTURE.md` §7. But Android's hotspot DHCP
  hands out its own DNS server and an app cannot override that option
  without root (ASSUMED, needs verification). Public DNS is the rootless
  path; local DNS is a dongle capability.

## 5. What this does not fix

If test 1 fails, or if the denial turns out to be address-family based
(Tesla rejecting any non-Tesla destination that is not a well-known public
service), then no rootless phone-side scheme reaches the car, and the
honest options narrow to §6 and §7. Say so plainly rather than iterating on
variants of the same idea for another five sessions.

Also worth stating: WebRTC does not rescue this. Session 9 MEASURED that
Tesla's block covers **UDP as well as TCP** (laptop UDP to the phone
passed; car UDP to the same address failed), so ICE host candidates on a
blocked address are just as dead as HTTP. WebRTC only helps if the address
is reachable, in which case plain HTTP would have worked too. The one thing
WebRTC could still add is that cloud **signalling** is control-plane-legal
while a cloud **media** relay is not, so if a reachable local address is
ever found, WebRTC remains the better transport. It is not a way around an
unreachable address.

## 6. The dongle, reframed: it is not an upsell, it is the escape hatch

`DIFFERENTIATOR_FEATURES.md` §1 treats the dongle as a hardware upsell
option. That undersells it. Everything Android's sandbox forbids, a small
Linux board does natively **with root**:

- **Bind port 443.** No privileged-port restriction.
- **Run its own DNS for its own hotspot clients.** Full control of the
  DHCP options it hands the car, so no public DNS dependency at all.
- **Hold a certificate in hardware you control**, not in a public APK.
- **Guarantee its own addressing** regardless of the user's carrier, which
  removes the dependency that actually broke session 7 (a SIM with no IPv6
  on cellular that night).
- **Run MediaMTX natively**, which `MEDIAMTX_HLS_RESEARCH.md` found is
  unsupported and fragile when bundled into an APK.

The correction from `DIFFERENTIATOR_FEATURES.md` §1 still stands: a dongle
handing out ordinary 192.168.x addresses solves nothing, because that is
the same RFC1918 problem on different hardware. The dongle only helps if it
is designed around the addressing answer, not assumed to sidestep it. But
if §3's tests show the phone can never satisfy Tesla's rules from inside
Android's sandbox, **the dongle stops being optional and becomes the
product**, and the phone app becomes its companion rather than its host.

## 7. The strategic hedge that has been missed entirely

`VEHPLAYER_Foundation.md` §3 and `CLAUDE.md` both state the position
plainly: **the dashboard is the product, mirroring is plumbing.** Yet the
entire project is currently blocked on a plumbing problem that only
*mirroring* strictly requires.

Look at what the dashboard actually needs to move: now-playing metadata,
message text, contacts, navigation state. That is kilobytes of JSON, not a
video stream. The high-bandwidth local path is a hard requirement for the
mirror and for essentially nothing else.

So there is a version of this product that ships without the blocker being
solved at all: the car loads the dashboard from the CDN (proven to work),
and the phone syncs dashboard state through the control plane. Mirroring
becomes the feature that unlocks later, when the transport question is
answered.

**This is a real strategic option and also a real principle change, so it
must not be slipped in quietly.** The current promise is "Nothing leaves
the car. No cloud, no relay, no account" (`VEHPLAYER_Foundation.md`,
`MARKET_AND_PRICING.md` §295). Routing message content through a relay
contradicts that as written, even though it never touches "cloud never sees
a video frame". The defensible version is end-to-end encryption with keys
exchanged at pairing, so the relay carries ciphertext it cannot read, and
the marketing claim is rewritten honestly to say exactly that rather than
implying no relay exists. That is a Signal-shaped promise, not the current
one. It is a founder decision, not an engineering one, and it should be
made deliberately.

The reason to take it seriously: it converts a hard external dependency
(what Tesla's browser permits) into a soft one, and it means a public
release stops being hostage to a filter we do not control and cannot
appeal.

## 8. On "how long until something public"

House rule: plan by gate and sequence, never by weeks or months
(`CLAUDE.md`). So, honestly:

**There is exactly one unknown standing between this project and a public
release, and it is not a quantity of work.** It is whether any URL the
phone can serve, from inside Android's sandbox, is one the car's browser
will accept. Until §3's four observations are made, any estimate would be
invented. After them, the sequence forks cleanly:

- **If a URL shape works**: the remaining path is short and known - TLS on
  the local server, dynamic DNS through the control plane, then the first
  `/go` round trip, which is the gate this project has never passed. The
  dashboard, capture, encoder, input injection, quality ladder and web
  client are all already built and emulator-verified. This is the
  best-case branch and it is genuinely close.
- **If no phone-served URL works**: the phone-only product is not
  shippable as designed, and the decision is between the dongle (§6) as
  the primary product and the dashboard-first hedge (§7). Both are real
  products; neither is a small pivot; picking one is a founder call.

What should not happen is another session of building features on top of an
unverified transport. The four observations in §3 cost one drive and no
code, and they determine which of two quite different companies this is.

## 8b. The self-hosted tunnel idea: 80 percent right, and the wrong 20 percent is fatal

The founder proposed (session 10, brainstorm): give every device an opaque
per-device subdomain like `JD82BV103.vehp.nl`, pair it by scanning a QR in
the car, persist the session so it is not re-done every trip, and have
`*.vehp.nl` tunnel that name through to the phone's exposed port, so "we
never see or process anything." Possibly WireGuard underneath.

**Keep almost all of this. It is better than what §4 sketched.** Three
parts are genuine improvements and should be adopted verbatim:

- **An opaque per-device label** (`JD82BV103`) is not just a name, it is an
  unguessable capability. It solves in one stroke what §4 left vague, and
  it means the hostname itself carries authorization.
- **QR bootstrap** answers the question §4 ducked: how does the car ever
  learn the URL, given nobody wants to type a hostname into a car browser.
  Scanning is the correct interaction, and the repo already has QR
  rendering in the connect-info overlay.
- **A short domain** (`vehp.nl`) matters more than it looks, because the
  fallback when a QR fails is a human typing it on a car touchscreen.
- **Session persistence** is the same requirement the session-10 Reverse
  finding produced independently (one-tap resume, `ARCHITECTURE.md` §6).

**The one part that has to change: what the DNS record points at.**

If `JD82BV103.vehp.nl` resolves to our server and we tunnel through to the
phone, then every video byte flows phone -> our server -> car. That is a
cloud media relay, the thing rejected in session 6, and this time it fails
on arithmetic before it fails on principle:

- The stream is 8 Mbps (`ARCHITECTURE.md` §2 default) = **3.6 GB per
  hour**, one direction.
- **The car's internet is the phone's hotspot.** So relayed bytes cross
  the phone's cellular link twice: up to our server, then back down to the
  phone and over Wi-Fi to the car. That is **7.2 GB per hour of the user's
  own mobile data**, roughly **216 GB/month at one hour of driving a day**,
  to move video between two devices about a meter apart. No consumer data
  plan survives that, and the user pays it, not us.
- Our egress at typical cloud pricing is **$0.04 to $0.32 per user per
  hour**, i.e. **$1 to $10 per user per month at one hour a day**, against
  a **one-time** EUR 9.99 price (`COMPETITIVE_REASSESSMENT.md` §5.2).
  Every hour driven after the first month or two is a direct loss, forever,
  with no mechanism to recover it.
- It adds a full internet round trip to a pipeline whose entire value is
  sub-200ms touch response.
- "We never see it" is not achievable by intent. The bytes physically
  transit our infrastructure, which makes us a processor with the legal and
  operational exposure that carries, even if we never read them.

WireGuard does not change any of this: the car is a locked browser and
cannot run a WireGuard client, so the tunnel could only terminate at our
server, which is the same relay with extra steps. FIDO/WebAuthn is likewise
overkill; an opaque bearer name delivered out-of-band by QR is already the
right strength for a local-link pairing.

**The fix is one line of the design:** point the AAAA record at the phone's
own address instead of at our tunnel. Everything else in the founder's
proposal survives intact.

```
JD82BV103.vehp.nl   AAAA   <phone's current IPv6 GUA>     (published by the
                                                           phone via the
                                                           control plane)
```

The car then opens `https://JD82BV103.vehp.nl:8443/`, which is a real
hostname with a real publicly-trusted certificate, resolving to an address
one Wi-Fi hop away. Same QR, same opaque identifier, same session
persistence, same "we see nothing" property, except now it is structurally
true rather than a promise: **we answer a DNS query and never appear in the
data path at all.** Zero egress cost, zero added latency, zero of the
user's mobile data.

Worth stating plainly because it is the crux: **a DNS record is control
plane, a tunnel is data plane.** The founder's instinct to use our
infrastructure as the rendezvous is right. It just has to be a rendezvous
that hands out an address, not one that carries the traffic.

**Where a relay legitimately belongs**: as an explicitly-labelled fallback
tier for users where the direct path cannot be established, in the shape
Tailscale uses (try direct first, relay only on failure, be transparent
that it is happening and that it is slower). That is a defensible product
decision, but it is a conscious amendment to the media-in-cloud rule, it
needs a recurring revenue line to fund the bandwidth, and it should be
decided the way §7 says: deliberately, not drifted into.

**One open question this surfaces**: Teslas with Premium Connectivity have
their own LTE, so a car may not be on the phone's hotspot at all. If the
car is independently online, the double-cellular arithmetic above halves,
and the local-link assumption this whole architecture rests on disappears
for those users. Nobody has checked which connection the browser actually
uses when both are available (ASSUMED, unverified). It affects both the
relay math and whether a local path exists at all, so it belongs in the
§3 test list.

## 9. Recommendation

1. Run §3's four observations on the next drive. Nothing else is on the
   critical path until they are done. Add: check whether the car's browser
   uses Premium Connectivity LTE instead of the hotspot when both exist
   (§8b's open question).
2. Capture the car's real Chromium version while there, so the compat
   matrix stops resting on a stale, once-mis-attributed number.
3. Hold §4 (real hostname plus real certificate) as the leading candidate
   design, **with §8b's naming, QR bootstrap and session persistence folded
   in**, but do not build it until §3 says which suspect is guilty.
4. Treat §7 as a live strategic option to decide deliberately, not a
   fallback to drift into.
5. Leave the differentiator-feature backlog
   (`DIFFERENTIATOR_FEATURES.md` §8) parked until the transport question
   is settled. Those features are all downstream of a working connection.
