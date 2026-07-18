// Adaptive quality (ARCHITECTURE.md §5). The ENCODER lives on the phone and
// is the actual authority on bitrate/resolution/framerate; this module only
// monitors symptoms visible client-side (WS bufferedAmount growth, RTT via
// control-channel ping/pong) and asks the phone to step the ladder up/down.
// The phone is free to ignore requests (e.g. thermal override in progress).

import type { ControlMessage } from './control';

export interface QualityLadderOptions {
  getBufferedAmount: () => number;
  sendControl: (msg: ControlMessage) => void;
  onPong?: (rttMs: number) => void;
}

const BUFFERED_HIGH_WATERMARK = 512 * 1024; // bytes; sustained growth above this suggests the socket can't keep up
const CHECK_INTERVAL_MS = 1000;
const RTT_BAD_MS = 150;

export class QualityLadder {
  private lastBuffered = 0;
  private growthStreak = 0;
  private lastPingT = 0;
  private intervalId: number | undefined;

  constructor(private readonly opts: QualityLadderOptions) {}

  start() {
    this.intervalId = window.setInterval(() => this.tick(), CHECK_INTERVAL_MS);
  }

  stop() {
    if (this.intervalId !== undefined) window.clearInterval(this.intervalId);
  }

  /** Call this whenever a 'pong' control message arrives. */
  onPongReceived(echoedT: number) {
    const rtt = performance.now() - echoedT;
    this.opts.onPong?.(rtt);
    if (rtt > RTT_BAD_MS) {
      this.opts.sendControl({ kind: 'quality_request', direction: 'down' });
    }
  }

  private tick() {
    const buffered = this.opts.getBufferedAmount();
    if (buffered > BUFFERED_HIGH_WATERMARK && buffered >= this.lastBuffered) {
      this.growthStreak++;
    } else {
      this.growthStreak = Math.max(0, this.growthStreak - 1); // hysteresis, per ARCHITECTURE.md §5 "recover with hysteresis"
    }
    this.lastBuffered = buffered;

    if (this.growthStreak >= 3) {
      this.opts.sendControl({ kind: 'quality_request', direction: 'down' });
      this.growthStreak = 0;
    } else if (this.growthStreak === 0 && buffered < BUFFERED_HIGH_WATERMARK / 4) {
      // Only ask to step up when things have been clearly fine for a while;
      // real hysteresis tuning needs Gate 2 numbers, this is a placeholder ratio.
      this.opts.sendControl({ kind: 'quality_request', direction: 'up' });
    }

    this.lastPingT = performance.now();
    this.opts.sendControl({ kind: 'ping', t: this.lastPingT });
  }
}
