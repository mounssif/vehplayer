# S1 , Reachability matrix (template, fill in from the car)

> Run: `webclient/probe/README.md`. One row per test, per firmware version tested.
> Evidence tag on completion: MEASURED.

| Firmware | Tier tested | HTTP loads? | WS opens? | Sustains 10 Mbps / 60s? | Notes |
|---|---|---|---|---|---|
| | IPv6 GUA, no VPN | | | | |
| | IPv6 ULA | | | | |
| | CGNAT via VpnService (not built yet, Gate 2) | | | | |
| | RFC1918 control (expected fail, confirms the block) | | | | |
| | Public-range via VpnService (not built yet, Gate 2) | | | | |

## This session's partial result
`android/` ships the tier-(a) detector only (`ReachabilityProbe.kt`): confirms whether a
global-scope IPv6 address exists on the phone locally. It does not yet confirm the car can
reach it, run S2's WS round-trip box against it once `android/` has a WS server (Gate 2).

## Decision this feeds (Foundation §6b)
This spike no longer picks a single winning tier, it calibrates the order and timeout budget
of the fallback ladder that already ships. Fill in real numbers before trusting any latency
claim elsewhere in the docs.
