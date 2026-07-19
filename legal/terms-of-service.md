# vehplayer - Terms of Service (draft)

> **Status: draft, not legal advice, not yet reviewed by a lawyer.**
> Placeholders marked `[MENS]` need founder-confirmed facts before this is
> publishable. Do not publish to a store listing until a real legal
> review has happened (`docs/MARKET_AND_PRICING.md` §9.4).

**Effective date**: not yet published. **Provided by**: `[MENS: legal
entity name and registered address]`.

## What vehplayer is

vehplayer mirrors a car-optimized dashboard from your Android phone to
your Tesla's in-car browser, over your phone's own WiFi hotspot connection
to the car. It is an independent product, not affiliated with, endorsed
by, or connected to Tesla, Inc., Google LLC, or Apple Inc. "Android Auto"
and "CarPlay" are trademarks of their respective owners, referenced here
only to describe compatibility, not to claim any relationship.

## Safety - read this before using it while driving

**This is the single most important section in this document, and it
should stay honest rather than boilerplate** (`docs/MARKET_AND_PRICING.md`
§9.4).

- vehplayer casts a live video feed and, if you enable the accessibility
  feature, relays touch input from the car's screen back to your phone.
  Like any in-car screen, **using it while driving is a distraction and
  you are responsible for using it safely and in compliance with the laws
  of your jurisdiction.**
- `[MENS / needs real-car verification before this can be stated as
  fact]`: what actually works and doesn't work while the car is in Drive
  vs. Park on current Tesla firmware. `docs/ARCHITECTURE.md` §2 documents
  a REPORTED (not yet MEASURED in our own car) finding that Tesla's
  browser suppresses `<video>`-element playback while driving, which is
  the whole reason this app renders to canvas instead - but the actual
  in-motion behavior of the *whole app* has never been confirmed against
  a real car. Do not publish a specific "works while driving" or
  "restricted while driving" claim in this document until that's been
  MEASURED, not ASSUMED.
- Video-heavy or attention-heavy content (browsing, video playback if
  ever added) should be treated as a park-only feature by the user,
  regardless of whatever the app technically allows - this is a plain
  statement of expectation, not a technical restriction the app currently
  enforces.

## Accounts and purchases

- The free tier requires no account.
- Pro is a one-time purchase (see `brand.json` for the current price),
  not a subscription - `[MENS]`: refund policy, ideally modeled on the
  "doesn't work in your car within 7 days, contact us" approach
  (`docs/MARKET_AND_PRICING.md` §5.2), needs a final decision and the
  actual support contact/process before this can be published.

## No warranty, limitation of liability

`[MENS / legal review needed]`: standard "as-is", limitation-of-liability,
and indemnification language, pending legal review specific to the
jurisdiction the legal entity is registered in.

## Acceptable use

- Do not use vehplayer's accessibility/input-control feature for any
  purpose other than controlling your own device from your own car's
  screen.
- Do not attempt to circumvent entitlement/licensing checks.

## Termination

`[MENS / legal review needed]`.

## Governing law

`[MENS / legal review needed]`: depends on the legal entity's
jurisdiction, not yet confirmed.

## Changes to these terms

`[MENS / legal review needed]`: standard change-notification language.
