# vehplayer - hardware

The beginning of the open-source setup track described in
`docs/PIVOT_HARDWARE.md` §5. Right now it contains exactly one thing: the
test that decides whether the whole hardware direction is real.

## Why a box at all

Ten sessions of trying to make a phone reachable from a Tesla's browser
ended in a complete answer, and it was no (`docs/REACHABILITY_RETHINK.md`).
Every address a rootless Android phone can present is measured dead:

| Path | Status |
|---|---|
| RFC1918 by IP literal | refused by the car, TCP and UDP |
| RFC1918 by hostname | refused identically, so the filter reads the resolved address |
| IPv6 GUA | the car has no IPv6 at all on the hotspot |
| CGNAT / public-style via VpnService | dropped in the kernel by Android's BPF ingress hardening |

One address family was never actually disproven: **CGNAT, `100.64.0.0/10`**
(RFC6598 shared address space). It is explicitly not RFC1918, so the
measured filter does not obviously cover it. The reason it was never tested
is that the only mechanism available to a phone for presenting such an
address died before a single packet reached the car, so the car has never
had the chance to accept or refuse it.

A Raspberry Pi can present it trivially. That is the entire test.

## Update: we now expect this to pass

`docs/REACHABILITY_RETHINK.md` §0 establishes, from three shipping
products' own public configuration, that **Tesla's browser blocks on the
destination IP being RFC1918 and on nothing else**. tesla-android ran for
about a year on `9.9.0.0/16` with the car loading a bare IP literal over
plain http on port 80.

`100.64.0.0/10` is not RFC1918, so it should be in the clear. That turns
this script from an exploratory test into a **confirmation on our own
hardware**, which is still worth doing before anything is built on it, and
still cheap.

It also settles a design choice. Both existing projects reach a non-private
address by squatting: `9.0.0.0/8` is IBM's, `240.3.3.4` is reserved. That
works only because the AP swallows the packet, and it shadows whatever
really lives at those addresses. RFC6598 is space designated for exactly
this kind of use and belongs to nobody, so **the box should hand out
`100.64.0.0/10` rather than borrow someone else's prefix.**

## The test

Needs a Pi with WiFi, nothing else. No phone, no internet, no vehplayer app.

```sh
sudo ./ap-cgnat.sh up      # AP on 100.64.0.1 + a /ping to fetch
sudo ./ap-cgnat.sh down    # undo everything
```

Then:

1. **Control first.** From a laptop or a second phone, join the AP and open
   `http://100.64.0.1:8080/`. This proves the Pi is actually serving. If
   the control fails, the Pi is the problem and a car result would be
   meaningless. This is the same A/B discipline that finally cracked the
   RFC1918 question in session 9.
2. **Then the car.** Join the car's WiFi to the same AP and open the same
   URL.

### Reading the result

- **It loads.** The car accepts an address a phone can never present. The
  hardware direction is validated, and the design rule for the box is
  settled: hand out `100.64.0.0/10`, never RFC1918.
- **It refuses.** Tesla blocks more than RFC1918, probably anything not
  publicly routable. The box then needs a genuinely public address, which
  changes its cost and complexity enough to reopen whether this is the
  right product at all. Photograph the exact error text: the difference
  between `ERR_CONNECTION_REFUSED` and something else has already carried
  real information twice in this project.

Either way, record it in `docs/NEXT_SESSION.md`. A clean no is worth as
much here as a yes, and considerably more than another maybe.

## Before you run it

The script reconfigures the wireless interface, so **an SSH session over
WiFi will drop**. Use ethernet, or a keyboard and monitor. The script
checks for this and refuses unless you pass `--force`.

It targets Raspberry Pi OS Bookworm or newer, which manages the network
with NetworkManager. On anything older, set up `hostapd` and `dnsmasq` by
hand with the same address. The tooling does not matter, the address does.

Defaults are overridable by environment variable: `VEHPLAYER_SSID`,
`VEHPLAYER_PSK`, `VEHPLAYER_IFACE`, `VEHPLAYER_PORT`.

## What this is not, yet

Not a product, not an image, not a supported install. `PIVOT_HARDWARE.md`
§7 has the sequence: this test comes first, the architecture decision
(does the box serve the dashboard, or only carry it) comes second, and a
real setup repo with a flashable image comes after both. Nothing here
should be read as a commitment to ship hardware, which is a decision gated
on the result above.
