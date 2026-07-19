# vehplayer - Privacy Policy (draft)

> **Status: draft, not legal advice, not yet reviewed by a lawyer.** This
> is an honest, technically-accurate description of what the app actually
> does, written from the codebase, so it can be handed to a real privacy
> lawyer as a starting point rather than drafted from nothing. **Do not
> publish this to a store listing until a real legal review has happened**
> (`docs/MARKET_AND_PRICING.md` §9.4). Placeholders marked `[MENS]` need
> founder-confirmed facts (legal entity name, registered address,
> jurisdiction, contact email) before this is publishable - none of those
> are asserted here as if already decided.

**Effective date**: not yet published. **Company**: `[MENS: legal entity
name and registered address]`. **Contact**: `[MENS: privacy contact
email]`.

## The one-paragraph version

vehplayer casts a car dashboard from your phone to your Tesla's browser
over your phone's own WiFi hotspot. Your screen and audio never leave the
direct connection between your phone and your car - there is no cloud
server in that path, ever (see "What we never see" below). The
permissions the app requests are used only to make the dashboard's
features work, listed below with the specific reason for each. No
advertising SDK, no analytics SDK, no third-party data broker, anywhere in
this app.

## What we never see

The **data plane** - your screen, your audio, your car dashboard's live
content - is 100% local. It travels directly between your phone and your
car over your own hotspot WiFi connection (or, if your phone's hotspot
address isn't directly reachable, a locally-assigned alternate address
still on the same local connection - see `docs/ARCHITECTURE.md` §7). It
never passes through any server we operate, is never logged, and is never
stored anywhere. This is an architectural fact, not a policy promise we
could quietly change: `cloud/src/index.ts`, the only server-side code this
app talks to, is explicitly scoped to licensing/entitlements, per-firmware
configuration, and opt-in compatibility telemetry - never media.

## What the app's permissions are used for

Every permission below exists to make a specific, named feature work.
None is used for advertising, analytics, or any purpose other than the
feature listed.

| Permission | Used for |
|---|---|
| Screen capture (MediaProjection) | Mirroring your car dashboard to the car's browser. Requested fresh, every session, via Android's own system consent dialog - never silent. |
| Accessibility Service (`dispatchGesture`) | Letting you control the car dashboard by touching the car's screen (taps/swipes on the car are relayed back to your phone). **Not used to read screen content, log keystrokes, or collect any data** - it only injects the touch gestures you make on the car screen. Requires its own explicit, separate in-app confirmation before it's enabled, distinct from any other permission request. |
| Notification access (`NotificationListenerService`) | Two features read from this, both locally: (1) showing what's currently playing (song/artist/art) by reading the active media session, and (2) showing a compact recent-messages view by reading notification metadata (sender, preview text) from messaging apps - never the underlying message content, since no such API exists for most messaging apps (see the "Messages" note below). |
| Call log | Showing your recent calls in the Phone tile. Read-only, stays on your phone, never transmitted anywhere. |
| Contacts | Showing your contacts list (with the ability to call them) in the Phone tile. Read-only. |
| Location (coarse + fine) | Positioning the "you are here" marker and starting point for routing on the in-app map (Navigate tile). Used only while that screen is open. |
| Microphone (`RECORD_AUDIO`) | Only if you enable the optional low-latency audio route (Pro feature) - required by Android's `AudioPlaybackCaptureConfiguration` API for capturing your phone's own audio output, not for recording ambient sound. Off by default. |
| VPN service permission | Used only as a local network trick to make your phone reachable at an address your car's browser will actually connect to (some Tesla firmware refuses to connect to certain private-network address ranges). **This is not a privacy VPN** - no traffic is tunneled to a remote server, nothing is encrypted-and-relayed elsewhere. It only changes which local address your phone answers on. See `docs/ARCHITECTURE.md` §7 for the technical detail. |
| Install unknown apps / install packages | Only relevant to the sideloaded (non-Play-Store) build, used solely to let you install app updates you download from our own GitHub Releases, with the normal system install confirmation every time. |

### About the Messages feature specifically

There is no public API that lets any third-party app read WhatsApp message
content, and Android's SMS provider doesn't include RCS messages either.
So the Messages tile does not, and cannot, read your actual conversations.
What it shows is exactly what you'd see in your phone's own notification
shade - sender name, a short preview, and a timestamp - for whichever
messaging apps post a normal Android notification. Tapping one opens the
real conversation in the real app; vehplayer never has access to full
message history or content.

## What we do collect (opt-in, control-plane only)

- **Entitlements**: whether you've purchased Pro, checked against
  `cloud/src/entitlements.ts` - `[MENS: current implementation is a
  placeholder device-ID check, not yet a real signed token - do not claim
  a specific security property here until that's replaced, see
  docs/NEXT_SESSION.md]`.
- **Compatibility telemetry**: opt-in only, reports which reachability
  tier worked and on what Tesla firmware version, used solely to build a
  public "does my car work" compatibility matrix. No screen content, no
  personal data, no precise location.

## What we don't do

- No advertising SDK, no ad network, no ads, anywhere in the app.
- No analytics SDK tracking your usage across sessions.
- No account required to use the free tier.
- No data sold or shared with any third party.

## Your rights

`[MENS / legal review needed]`: this section needs a real GDPR-compliant
rights statement (access, deletion, portability, the supervisory authority
to complain to) once the legal entity and jurisdiction are confirmed. Not
drafted here to avoid asserting rights language before a lawyer has
reviewed the actual data flows above.

## Children

`[MENS / legal review needed]`: standard age-appropriate-use statement,
pending legal review.

## Changes to this policy

`[MENS / legal review needed]`: standard change-notification language,
pending legal review.
