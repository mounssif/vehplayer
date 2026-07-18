# S2 , Browser capability probe (template, fill in from the car)

> Run: `webclient/probe/index.html` per `webclient/probe/README.md`.
> Evidence tag on completion: MEASURED.

Paste the probe's result table here per firmware version tested:

```
User agent:
Screen viewport:
WebCodecs present:
H.264 Baseline (avc1.42E01E) decode support:
MediaSource Extensions present:
fMP4 H.264 Baseline isTypeSupported:
WebSocket present:
AudioWorklet present:
AudioContext initial state:
rAF: frames / fps / max jitter (5s window):
Touch: pointerType observed / pressure supported? / event rate:
Page-visibility behavior on reverse gear (manual observation, see below):
```

## Manual checks the page can't automate
- Shift to reverse and back: does `visibilitychange` fire (see console), does playback resume
  cleanly or does the tab reload? (ARCHITECTURE.md §7 lifecycle edge case)
- Autoplay: does AudioContext leave `suspended` only after a tap on the page, or does the car
  browser have its own stricter policy?
