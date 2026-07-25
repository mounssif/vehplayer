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

## 8c. Split DNS, the carrier dependency, and a free experiment that may collapse the whole problem

Follow-up founder questions: is the AAAA-to-the-phone design actually
possible, does it not make us hostage to whether the SIM provider does
IPv6, and could the phone instead run its own DNS (dnsmasq/CoreDNS)
handing out `DNS = 192.168.43.1` over DHCP so that `xxx.vehp.nl` resolves
to the phone's own hotspot address?

### The split-DNS variant: right goal, blocked mechanism, but there is a way around it

**Running DNS on the phone does not work rootless.** Android's tethering
stack owns the DHCP server for hotspot clients and there is no public API
to set DHCP option 6 (the DNS server handed to clients). `SoftApConfiguration`
does not expose it, `VpnService` captures the device's own app traffic
rather than forwarded tethering traffic, and `LocalOnlyHotspot` is
system-managed the same way (ASSUMED, consistent with §4's earlier note and
with `LocalOnlyHotspotController.kt` having no DHCP surface). On a dongle
this is a five-line dnsmasq config, which is one more entry on §6's list of
things the sandbox forbids and a small Linux board does natively.

**But controlling DHCP was never actually necessary.** Public DNS is
allowed to return private addresses, and that is enough to get exactly the
effect the founder is after:

```
JD82BV103.vehp.nl   A   10.142.169.193     (the phone's hotspot address,
                                            published in our own zone)
```

The car uses its normal resolver, gets the phone's local address, and
connects over the hotspot. No DHCP control, no root, no carrier
involvement. Verified from this sandbox that public wildcard-DNS services
already do exactly this: `10.142.169.193.sslip.io` (the phone's real
hotspot address from the session-10 screenshot) resolves to
`10.142.169.193`, and `192-168-43-1.sslip.io` resolves to `192.168.43.1`
(MEASURED, this sandbox's resolver, though note the car's resolution path
runs through the phone's forwarder to the carrier's resolver, which may
apply DNS-rebinding protection that strips private answers - see the A/B
control below).

### Why this is the highest-value test available, not just a convenience

There are two plausible ways Tesla could have implemented the RFC1918
block, and **the entire ten-session evidence base cannot tell them apart**,
because every attempt on record used a bare IP literal (§1):

- **(a) It inspects the resolved IP.** A hostname changes nothing; the
  block still fires. Most likely, since that is where Chromium's own
  address-space machinery sits.
- **(b) It inspects the URL and rejects private-range IP literals.** Then a
  hostname that resolves to the same address **slips straight through**,
  and this is over.

If (b) holds, the consequences are large: no IPv6 requirement, therefore
**no carrier dependency at all**, no dynamic-address problem, and the
product works on any SIM including IPv4-only ones. That directly answers
the founder's worry about being hostage to the provider.

**MEASURED, session 10, control test passed.** The founder ran the control
from a device on the hotspot, no car and no new APK involved (build-31
already serves `/ping`):

| URL | Result |
|---|---|
| `http://<hotspot-ip>:8080/ping` | **pong** |
| `http://<hotspot-ip>.sslip.io:8080/ping` | **pong** |

Both returned `pong`, which settles two things: the carrier's resolver does
**not** apply DNS-rebinding protection (it happily returns the private
address for an sslip.io name), and the phone answers on the hostname form
just as it does on the literal (NanoHTTPD does not care about the Host
header). **So the hostname form is a valid test rather than a DNS dead end,
and if it fails in the car that failure is Tesla, not DNS.** This is exactly
the precondition that had to hold before the in-car run is worth doing.

Still proves nothing about the car: it is the only device carrying the
filter. But the experiment is now de-risked.

### RESULT: hypothesis (b) is dead. The filter reads the resolved address.

**MEASURED, session 10, in the car.** `http://10.247.244.242.sslip.io:8080/ping`
returned **`ERR_CONNECTION_REFUSED`**, with the browser naming the hostname
in the error ("10.247.244.242.sslip.io heeft de verbinding geweigerd"), so
DNS resolved fine and the refusal came at connection time.

**Hypothesis (b) is therefore false: a hostname does not launder a private
address.** Tesla's filter inspects the resolved IP, exactly as the more
likely reading predicted. Consequences:

- The sslip.io approach is dead **for RFC1918**, and so is any variant of
  it on our own domain, because the resolved address is identical.
- The **Plex precedent (§8d) does not transfer.** `*.plex.direct` works in
  normal browsers; it does not survive Tesla's filter. Do not cite it as
  evidence for this product again.
- The carrier-IPv6 dependency does **not** get to be avoided this way.

**But note the error codes differ, and that is now the live signal:**

| Target | Error |
|---|---|
| RFC1918 literal (session 9) | `ERR_CONNECTION_REFUSED` |
| RFC1918 via hostname (this test) | `ERR_CONNECTION_REFUSED` |
| IPv6 GUA literal (session 10) | `ERR_ACCESS_DENIED` |

RFC1918 refuses identically however it is addressed, which is the
address-based filter behaving consistently. The IPv6 GUA produces a
**different** error, so it is not hitting that same rule. IPv6 remains the
only rootless path not yet ruled out, and what `ERR_ACCESS_DENIED` actually
represents is now the single most valuable unknown.

Highest-priority follow-ups, in order: (1) a **public** host on the same
non-standard port (`http://veh.modev.be:8080/`), because if that fails then
port 8080 has been confounding every test for ten sessions and the address
was never the only variable; (2) reproduce the IPv6 literal error on the
current GUA; (3) the same IPv6 address via an sslip hostname, to see
whether the error changes.

### The IPv6 result may not be about Tesla at all

Two more in-car MEASURED results, same session, on the current GUA
(`2a02:a020:5d0:758f:79f7:7d03:88f6:bfda`, note it changed from the
previous night, which confirms the address is dynamic):

| Target | Result |
|---|---|
| `http://[<GUA>]:8080/ping` | `ERR_ACCESS_DENIED` (reproduces) |
| `http://<GUA-as-sslip-name>:8080/ping` | **`DNS_PROBE_FINISHED_NXDOMAIN`** |

The second is **not a Tesla refusal**, it is a name-resolution failure, and
that changes the reading. Verified from this sandbox: that sslip name has
**only an AAAA record and no A record**:

```
A    (IPv4) -> NONE
AAAA (IPv6) -> 2a02:a020:5d0:758f:79f7:7d03:88f6:bfda
```

The most economical explanation is that **the car queried only for A
records, got nothing, and reported NXDOMAIN**, which is what a client with
no IPv6 connectivity does. If that is right, the car has no IPv6 on the
hotspot link at all, and tier 1 has been failing for a reason that has
nothing to do with Tesla's filter: **the car cannot route to any IPv6
address, so the phone's GUA was never reachable regardless of policy.**

This is exactly the prerequisite session 7 wrote down and nobody ever
verified: "Android's downstream IPv6 tethering must delegate a prefix to
hotspot clients." The phone having a GUA on `ap_br_swlan0` is **not** the
same as the phone advertising a prefix so the car gets its own IPv6
address. That link in the chain has never been checked.

Two ten-second observations settle it, and they outrank everything else:

1. **`https://ipv6.google.com/` in the car.** Loads = the car has working
   IPv6, so `ERR_ACCESS_DENIED` really is a Tesla policy about the phone's
   address. Fails = the car has no IPv6, tier 1 is dead for an Android
   tethering reason rather than a Tesla one, and the whole IPv6 branch of
   this document needs rewriting around that.
2. **`http://veh.modev.be:8080/` in the car** (still not run). A public
   host on the same non-standard port, no phone involved. If this fails,
   port 8080 has been confounding every single test for ten sessions.

### CONFIRMED: the car has no IPv6, and that closes the rootless phone path

**MEASURED, session 10, decisive.** `https://ipv6.google.com/` in the car
returned `DNS_PROBE_FINISHED_NXDOMAIN`. The same URL works on the founder's
phone. `ipv6.google.com` is an AAAA-only host with no A record and no
relationship to us, so this is an independent confirmation of the reading
above: **the car cannot resolve or route IPv6 on the phone's hotspot.**

Tier 1 was therefore never failing because of Tesla's filter. It was
failing because the car has no IPv6 at all, so the phone's GUA was
unreachable regardless of policy. The `ERR_ACCESS_DENIED` on the IPv6
literal is now a secondary curiosity rather than the central question.

Root cause is Android's downstream tethering: the phone holds a GUA on
`rmnet_data7` (cellular), but is not delegating an IPv6 prefix to hotspot
clients, so the car never receives an address. This is exactly the
prerequisite session 7 wrote down and never verified. It is also
carrier-dependent, which makes it a poor product foundation even if it
could be coaxed to work on this one SIM.

**The full ladder, with every rung now measured:**

| Path | Status |
|---|---|
| RFC1918 literal | MEASURED dead, Tesla filter, TCP and UDP (session 9) |
| RFC1918 via hostname | MEASURED dead, same refusal (session 10) |
| IPv6 GUA | MEASURED dead, car has no IPv6 at all (session 10) |
| CGNAT via VpnService | MEASURED dead, Android BPF ingress discard (session 7) |
| Public-style via VpnService | MEASURED dead, same BPF hardening (session 7) |

**There is no remaining address a rootless Android phone can present that
this car will accept.** IPv4 is either RFC1918 (blocked) or public (not
ours, and the only mechanism to borrow it is BPF-dead), and IPv6 is not
routable from the car. That is a complete, evidenced conclusion rather than
another open question, and it should be treated as settling the phone-only
architecture rather than as one more wall to poke at.

### What survives, and the cheap way to validate it

One address family was never actually disproven: **CGNAT / RFC6598 shared
space, `100.64.0.0/10`.** It is explicitly *not* RFC1918, so Tesla's
measured filter does not obviously cover it. Tier 2 was designed around
exactly that reasoning and it died on the *delivery mechanism*
(VpnService plus BPF ingress discard), never on the address being refused.
No packet ever reached the car for it to accept or reject.

A rootless phone cannot put a `100.64.x.x` address on its AP interface. A
small Linux board can, trivially, along with everything else §6 lists
(bind 443, run its own DNS, hold a certificate, guarantee addressing
independent of the carrier). **This turns the dongle from an upsell into
the answer**, and it gives the dongle a concrete, evidence-derived design
rule: **hand out `100.64.0.0/10`, never RFC1918.**

Before spending anything on hardware, this is testable with whatever is
already lying around: bring up any AP (a laptop, a spare router, a travel
router) configured to serve `100.64.0.0/10` instead of the usual
`192.168.x`, join the car to it, and open `http://100.64.0.1:8080/` from
the car. If that loads where RFC1918 refused, the dongle direction is
validated for the price of an afternoon. If it refuses identically, Tesla
blocks by "not a public address" rather than by RFC1918 specifically, and
the dongle needs a genuinely public address to be viable, which changes its
cost and design substantially.

Note also: `http://veh.modev.be:8080/` was inconclusive, Cloudflare
redirects it to https, so the non-standard-port question is still formally
open. It matters much less now, since a dongle can bind 443 anyway.

The remaining test costs nothing and needs no build:

1. **Laptop control first** (same method that cracked session 9): from a
   laptop on the hotspot, open `http://<hotspot-ip>.sslip.io:<port>/diag`.
   This proves the name resolves through the carrier's resolver and that
   the phone answers on it. If the laptop fails, the resolver is stripping
   private answers and the test is inconclusive, not a Tesla result.
2. **Then the identical URL in the car.** Loads = hypothesis (b), the
   filter is literal-based, and the reachability problem is solved with a
   DNS record. Same `ERR_ACCESS_DENIED`/refusal = hypothesis (a), the
   filter is address-based, and a non-private address is genuinely required.

Run this before anything in §4 gets built, because it decides whether §4 is
needed at all.

### The carrier IPv6 dependency, stated honestly

The founder is right to flag it. Tonight's connect-info overlay reported
`[SIM has IPv6] [hotspot exposes IPv6]`, so this SIM is fine, but that is
one carrier. IPv6 availability varies by operator and APN configuration,
and session 7 already lost a night to a SIM with no IPv6 GUA on cellular.
As a shipped product that becomes a support burden of the form "works on
carrier X, not on carrier Y", which is exactly the class of problem that
gives this category its 2-star reviews (`COMPETITIVE_REASSESSMENT.md` §4.4).

So the preference order is: **hypothesis (b) if it holds (no dependency at
all) > IPv6 GUA (carrier-dependent) > dongle (dependency removed by
hardware)**. That ordering is another reason to run the free test first.

### The Cloudflare API idea: yes, with two non-negotiable details

Dynamic per-device subdomains via Cloudflare's API is the right mechanism,
and `cloud/` already exists as a Cloudflare Worker to host it. Two things
must be right:

- **Never ship a Cloudflare API token in the APK.** It is extractable, and
  a leaked token with DNS-edit rights lets anyone rewrite the whole zone.
  The phone must authenticate to **our Worker** per-device, and the Worker
  holds the credentials and makes the change. This is precisely what a
  control plane is for, and it keeps the token server-side.
- **The record must be DNS-only, never proxied.** An orange-cloud record
  routes traffic through Cloudflare, which would make us the media relay
  §8b just rejected, and Cloudflare cannot reach a local address anyway.
  Grey cloud, TTL at the minimum (60s) so it tracks the phone's changing
  address, and only update when the address actually changes so the API
  rate limits are not hit at scale.

### Certificates at scale

Let's Encrypt's per-registered-domain certificate limits make one
certificate per device impractical (REPORTED, LE rate limits). A single
wildcard covering every device is operationally simple, but then every
device shares one private key, so extraction from any APK compromises the
whole domain. Practical middle ground: a **wildcard on a dedicated
throwaway domain** whose compromise costs nothing and never touches the
main domain, **delivered and rotated through the control plane** rather
than baked into the APK. DNS-01 issuance works regardless of what the
A/AAAA record points at, so a certificate can be held for names that
resolve to a local address.

## 8d. Cross-checking four other models' proposals against our measured data

The founder put the same question to ChatGPT, Gemini, Grok and Kimi, and
proposed this chain:

```
Vehicle Browser -> xxx.vehp.nl -> DNS? -> Android VPN intercept
  -> 192.168.43.1 -> Caddy -> MediaMTX -> WebRTC
```

All four models independently converged on roughly "split DNS plus WebRTC
P2P". That convergence is worth noticing but it is **not** evidence: they
are reasoning from general knowledge, none of them has this project's
measured data, and the same pattern already produced a confidently wrong
IPv6/WebRTC claim earlier this session (`DIFFERENTIATOR_FEATURES.md` §1).
Two of the links they agree on are contradicted by our own in-car
measurements.

### Link-by-link verdict on the proposed chain

- **`xxx.vehp.nl`**: keep. Correct, and §8b/§8c already build on it.
- **`DNS?`**: the question mark is the right instinct. Public DNS works
  rootless; local DNS needs DHCP control the phone does not have (§8c).
- **`Android VPN intercept`: dead, and this is the most important
  correction.** All four models suggest `VpnService` as the DNS-interception
  mechanism, citing AdGuard/Tailscale/RethinkDNS as precedent. Those apps
  filter **the phone's own** traffic. `VpnService` captures traffic from
  apps on the device; it does **not** capture forwarded tethering traffic
  from hotspot clients. The car's DNS query is forwarded by the kernel
  tethering path and never enters the tun interface. Worse, we already
  MEASURED (session 7) that Android 14+ BPF ingress-discard hardening
  deliberately isolates VPN addresses from tethered peers. So this is not
  merely undocumented, the platform is actively hardened against it. **A
  rootless phone cannot intercept its hotspot clients' DNS.**
- **`192.168.43.1`**: MEASURED blocked by Tesla for both TCP and UDP
  (session 9). Only viable if §8c's hypothesis (b) holds, which is exactly
  the untested question.
- **`Caddy`**: unnecessary, and it inherits the in-APK Go-binary problem
  documented in `MEDIAMTX_HLS_RESEARCH.md` (Android's no-execute-from-
  writable-app-storage rule), plus Caddy's automatic TLS wants port 443
  which Android forbids without root. The app already ships **NanoHTTPD
  2.3.1**, which has `makeSecure(SSLServerSocketFactory, ...)` built in.
  TLS termination is a small change to a server we already run, not a new
  component in a foreign runtime.
- **`MediaMTX`**: adds nothing to the mirror path. `CaptureService` already
  produces H.264 access units from MediaCodec and ships them over the
  existing wire protocol to a WebCodecs canvas. MediaMTX would repackage
  something we already have in exactly the form we need. It earns its place
  only for passive-media HLS or foreign RTSP/RTMP ingest
  (`MEDIAMTX_HLS_RESEARCH.md`), not here.
- **`WebRTC`: does not do what all four models claim it does.** Every one
  of them argues ICE will find the local path, and Gemini states explicitly
  that serving the page from a public domain "bypasses the browser's local
  IP block". That is wrong on our data: **the block is on the destination
  address, not on the page's origin**, and session 9 measured it covering
  **UDP as well as TCP** (laptop UDP to the phone succeeded at 22ms; car
  UDP to the identical address failed). ICE host candidates on a blocked
  address are exactly as dead as HTTP was. WebRTC is a good transport
  *once an address is reachable*; it is not a way around an unreachable
  one, and TURN as the fallback is the cloud media relay §8b rejected on
  arithmetic.

### What is genuinely valuable in the other answers

Two contributions are real and are adopted:

- **Gemini's Plex precedent, and it is the strongest validation yet for
  §8c.** Plex has shipped `*.plex.direct` for years at large scale: public
  DNS returns the **private** LAN address of the user's own server
  (`192-168-1-100.<hash>.plex.direct` -> `192.168.1.100`), paired with a
  real wildcard certificate so the browser gets valid HTTPS to a local
  address. That is precisely the §8c design, proven in production by a
  major product rather than invented here. It means every part of the
  mechanism except Tesla's custom filter is battle-tested, and it is worth
  studying their exact naming and certificate-distribution scheme before
  designing ours.
- **ChatGPT's DNS-over-HTTPS warning, which nobody else raised.** Embedded
  Chromium builds may use a public DoH resolver and ignore the
  DHCP-provided DNS entirely, which would silently break **any** local-DNS
  scheme. This is a real, specific, testable risk. Note the useful
  corollary: **DoH breaks local DNS but not public DNS**, so it is a third
  independent argument for the public-record approach over a phone-hosted
  resolver.

Minor but worth keeping: Grok's note that iOS hotspots default to
`172.20.10.1` (useful when the iOS sender arrives), and Gemini's reminder
that CORS headers are required if the player page and the stream end up on
different origins.

### The chain that actually follows

Stripping the dead and redundant links leaves something far smaller than
what was proposed:

```
Vehicle browser
  |  https://<opaque-id>.vehp.nl:8443        (real hostname, real cert, QR-paired)
DNS: public record, DNS-only, points at the phone's address
  |
NanoHTTPD + makeSecure()                     (already in the app, add TLS)
  |
existing binary wire protocol -> WebCodecs canvas   (already built)
```

No VPN, no Caddy, no MediaMTX, no WebRTC on the critical path. The delta
against what is already built and emulator-verified is **TLS termination on
the existing server, plus one control-plane call to publish a DNS record**.
That is the honest size of the remaining work in the good branch, and it is
much smaller than the proposed chain implies.

## 9. Recommendation

0. **Run §8c's hostname-to-private-address test first.** It costs nothing,
   needs no build, and if it passes the reachability problem is solved by a
   DNS record with no IPv6 and no carrier dependency. It is the single
   highest-value observation available and it has never been made.
1. Run §3's four observations on the same drive. Nothing else is on the
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
