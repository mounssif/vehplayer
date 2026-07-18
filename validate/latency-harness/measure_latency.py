#!/usr/bin/env python3
"""
Glass-to-glass latency harness (Foundation §7: "build the ruler before the
product... the first deliverable of the pipeline work").

Method: point a camera (phone slow-mo or a real camera at 60-120fps) at both
the source phone screen and the car screen at once. The phone flashes a
solid-color pattern (a simple on/off strobe is enough, doesn't need to be a
timestamp readout, we only need edge timing, not OCR) at a known interval.
This script reads the recording, watches two regions of interest (one over
each screen), detects the brightness-transition timestamps in each, and
reports the delta: that delta is glass-to-glass latency.

Usage:
    python3 measure_latency.py --video capture.mp4 \\
        --phone-roi 100,100,200,200 \\
        --car-roi 900,100,200,200

ROI format: x,y,w,h in pixels, in the source video's frame.

TODO(claude-code): the ROI selection above requires the operator to know
pixel coordinates ahead of time (from a still frame). A nicer version lets
you click-drag two boxes on the first frame interactively (cv2.selectROI
does exactly this, not wired in here to keep this script runnable
headlessly / in CI for the self-test below). Worth adding once this is
actually used against a real recording.
"""

from __future__ import annotations

import argparse
import sys
from dataclasses import dataclass

import cv2
import numpy as np


@dataclass
class Roi:
    x: int
    y: int
    w: int
    h: int

    @staticmethod
    def parse(s: str) -> "Roi":
        x, y, w, h = (int(v) for v in s.split(","))
        return Roi(x, y, w, h)


def mean_brightness(frame: np.ndarray, roi: Roi) -> float:
    crop = frame[roi.y : roi.y + roi.h, roi.x : roi.x + roi.w]
    return float(cv2.cvtColor(crop, cv2.COLOR_BGR2GRAY).mean())


def find_transition_timestamps(
    brightness: list[float], fps: float, threshold_std_mult: float = 2.0
) -> list[float]:
    """
    Finds timestamps (seconds) where brightness crosses from below-mean to
    above-mean by a meaningful margin, i.e. flash onsets. Simple derivative
    thresholding, deliberately not a fancy edge detector: the input signal
    (a deliberate on/off strobe) is about as clean a signal as this kind of
    measurement ever gets.
    """
    arr = np.array(brightness)
    if len(arr) < 3:
        return []
    diff = np.diff(arr)
    threshold = diff.std() * threshold_std_mult
    onsets = []
    for i in range(1, len(diff)):
        if diff[i] > threshold and diff[i - 1] <= threshold:
            onsets.append(i / fps)
    return onsets


def measure(video_path: str, phone_roi: Roi, car_roi: Roi) -> dict:
    cap = cv2.VideoCapture(video_path)
    if not cap.isOpened():
        raise RuntimeError(f"could not open {video_path}")
    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0

    phone_series: list[float] = []
    car_series: list[float] = []
    while True:
        ok, frame = cap.read()
        if not ok:
            break
        phone_series.append(mean_brightness(frame, phone_roi))
        car_series.append(mean_brightness(frame, car_roi))
    cap.release()

    phone_onsets = find_transition_timestamps(phone_series, fps)
    car_onsets = find_transition_timestamps(car_series, fps)

    # Pair each phone onset with the next car onset after it (the car should
    # always lag, never lead, if wiring/ROIs are correct).
    deltas_ms = []
    ci = 0
    for pt in phone_onsets:
        while ci < len(car_onsets) and car_onsets[ci] < pt:
            ci += 1
        if ci < len(car_onsets):
            deltas_ms.append((car_onsets[ci] - pt) * 1000.0)

    return {
        "fps": fps,
        "phone_onsets": phone_onsets,
        "car_onsets": car_onsets,
        "deltas_ms": deltas_ms,
        "mean_ms": float(np.mean(deltas_ms)) if deltas_ms else None,
        "p95_ms": float(np.percentile(deltas_ms, 95)) if deltas_ms else None,
    }


def _selftest() -> None:
    """
    No real camera footage exists to test against in this authoring session
    (see NEXT_SESSION.md). This generates a synthetic clip with a KNOWN
    injected latency and asserts measure() recovers it within tolerance, so
    the detection math itself is verified even though the real-world camera
    pipeline isn't. Run with `python3 measure_latency.py --selftest`.
    """
    import tempfile
    import os

    fps = 60.0
    duration_s = 3.0
    n_frames = int(fps * duration_s)
    w, h = 640, 240
    phone_roi = Roi(20, 20, 100, 100)
    car_roi = Roi(400, 20, 100, 100)
    injected_latency_ms = 120.0
    injected_latency_frames = round(injected_latency_ms / 1000.0 * fps)

    flash_every_n_frames = 30  # one flash per 0.5s at 60fps

    with tempfile.TemporaryDirectory() as tmp:
        path = os.path.join(tmp, "synthetic.mp4")
        fourcc = cv2.VideoWriter_fourcc(*"mp4v")
        writer = cv2.VideoWriter(path, fourcc, fps, (w, h))

        for i in range(n_frames):
            frame = np.full((h, w, 3), 30, dtype=np.uint8)  # dark background
            phone_on = (i % flash_every_n_frames) == 0
            car_on = ((i - injected_latency_frames) % flash_every_n_frames) == 0 and i >= injected_latency_frames
            if phone_on:
                frame[phone_roi.y : phone_roi.y + phone_roi.h, phone_roi.x : phone_roi.x + phone_roi.w] = 255
            if car_on:
                frame[car_roi.y : car_roi.y + car_roi.h, car_roi.x : car_roi.x + car_roi.w] = 255
            writer.write(frame)
        writer.release()

        result = measure(path, phone_roi, car_roi)

    print(f"[selftest] injected latency: {injected_latency_ms:.1f}ms")
    print(f"[selftest] measured mean:    {result['mean_ms']:.1f}ms" if result["mean_ms"] else "[selftest] no deltas measured")
    print(f"[selftest] measured p95:     {result['p95_ms']:.1f}ms" if result["p95_ms"] else "")

    tolerance_ms = 1000.0 / fps * 1.5  # within 1.5 frame-periods
    assert result["mean_ms"] is not None, "selftest FAILED: no latency deltas detected at all"
    assert abs(result["mean_ms"] - injected_latency_ms) <= tolerance_ms, (
        f"selftest FAILED: measured {result['mean_ms']:.1f}ms, expected {injected_latency_ms:.1f}ms "
        f"+/- {tolerance_ms:.1f}ms"
    )
    print("[selftest] PASSED, detection math checks out")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--video", help="path to the filmed recording")
    parser.add_argument("--phone-roi", type=Roi.parse, help="x,y,w,h of the phone screen region")
    parser.add_argument("--car-roi", type=Roi.parse, help="x,y,w,h of the car screen region")
    parser.add_argument("--selftest", action="store_true", help="run the synthetic self-test instead of measuring a real video")
    args = parser.parse_args()

    if args.selftest:
        _selftest()
        return

    if not (args.video and args.phone_roi and args.car_roi):
        parser.error("--video, --phone-roi, and --car-roi are required unless --selftest is given")

    result = measure(args.video, args.phone_roi, args.car_roi)
    if result["mean_ms"] is None:
        print("no latency deltas detected, check ROI placement and that both screens actually flash in this clip", file=sys.stderr)
        sys.exit(1)

    print(f"fps: {result['fps']:.1f}")
    print(f"samples: {len(result['deltas_ms'])}")
    print(f"mean glass-to-glass latency: {result['mean_ms']:.1f}ms")
    print(f"p95 glass-to-glass latency:  {result['p95_ms']:.1f}ms")
    print(f"budget (Foundation §7): <= 120ms perceived")


if __name__ == "__main__":
    main()
