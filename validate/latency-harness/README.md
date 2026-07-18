# Latency harness (Foundation §7)

"Build the ruler before the product... no optimization without measurement."
This is that ruler.

## Setup
```
pip install -r requirements.txt
python3 measure_latency.py --selftest
```
The self-test generates a synthetic clip with a known 120ms injected delay
and confirms the detection math recovers it (already run once during this
session: measured 116.7ms vs 120ms injected, well within one frame period at
60fps). That's the only thing verifiable without a real camera/phone/car,
see NEXT_SESSION.md.

## Real measurement (Gate 2, in the car)
1. Film both the phone screen and the car screen in one shot, camera at the
   highest frame rate available (phone slow-mo, 120-240fps, ideal; a normal
   60fps clip still works, just coarser resolution on the result).
2. Have the phone flash a solid on/off pattern (any full-screen strobe,
   twice a second is plenty, this doesn't need to be part of the real app
   yet, a throwaway test Activity that toggles the screen white/black is
   enough).
3. Note the pixel region of each screen in the recording (a still frame in
   any image viewer gives you x,y,w,h).
4. `python3 measure_latency.py --video capture.mp4 --phone-roi X,Y,W,H --car-roi X,Y,W,H`

Output: mean + p95 glass-to-glass latency in ms, held against the Foundation
§7 budget (<=120ms perceived).

## Known gaps (TODO for claude-code, real device session)
- ROI selection is manual/numeric only, no interactive click-drag (see the
  module docstring in measure_latency.py for why, and the one-line fix with
  `cv2.selectROI` if it's worth adding once real footage exists).
- No per-pipeline-stage breakdown yet (capture / encode / transport / decode
  individually), this only measures the end-to-end glass-to-glass number.
  ARCHITECTURE.md §7's stage budget (encode <=20ms, transport <=10ms, decode
  <=20ms) needs either instrumented logging on both ends correlated by
  timestamp, or several harness runs with pieces of the pipeline swapped
  out, not designed yet.
