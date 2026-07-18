// Input (car -> phone), ARCHITECTURE.md §4. Coordinates normalized to the
// *video frame*, not the DOM element, so a resolution/quality-ladder change
// on the encoder side never breaks touch mapping (ARCHITECTURE.md §4).

import { Channel, buildFrame, nowMonotonicUs } from './protocol';
import { InputEventType, encodeInputEvent } from './inputProtocol';

export interface InputSenderOptions {
  surface: HTMLElement; // the canvas or <video> element receiving touches
  send: (frame: ArrayBuffer) => void;
}

export class InputSender {
  private readonly surface: HTMLElement;
  private readonly send: (frame: ArrayBuffer) => void;

  constructor(opts: InputSenderOptions) {
    this.surface = opts.surface;
    this.send = opts.send;
    this.attach();
  }

  private attach() {
    // Pointer Events give us multi-touch + pressure in one API (per
    // ARCHITECTURE.md §8 S2 spike, "touch event fidelity: multi-touch?
    // pressure? event rate?" this is the client-side half of answering that).
    this.surface.style.touchAction = 'none'; // prevent the browser's own scroll/zoom gestures from eating touches
    this.surface.addEventListener('pointerdown', (e) => this.onEvent(e, InputEventType.DOWN));
    this.surface.addEventListener('pointermove', (e) => this.onEvent(e, InputEventType.MOVE));
    this.surface.addEventListener('pointerup', (e) => this.onEvent(e, InputEventType.UP));
    this.surface.addEventListener('pointercancel', (e) => this.onEvent(e, InputEventType.UP));
    this.surface.addEventListener(
      'wheel',
      (e) => {
        e.preventDefault();
        this.sendScroll(e as WheelEvent);
      },
      { passive: false },
    );
  }

  private onEvent(e: PointerEvent, type: InputEventType) {
    // Only forward primary-ish pointer moves at a sane rate; MOVE events can
    // fire far faster than the phone needs (ARCHITECTURE.md §4 doesn't
    // specify a rate limit yet, this is a first guess, revisit with real S3
    // gesture-fidelity numbers).
    const rect = this.surface.getBoundingClientRect();
    const xNorm = clamp01((e.clientX - rect.left) / rect.width);
    const yNorm = clamp01((e.clientY - rect.top) / rect.height);

    const payload = encodeInputEvent({
      type,
      pointerId: e.pointerId,
      xNorm,
      yNorm,
    });
    const frame = buildFrame(Channel.INPUT, 0, nowMonotonicUs(), payload);
    this.send(frame);
  }

  private sendScroll(e: WheelEvent) {
    const rect = this.surface.getBoundingClientRect();
    const xNorm = clamp01((e.clientX - rect.left) / rect.width);
    const yNorm = clamp01((e.clientY - rect.top) / rect.height);
    const payload = encodeInputEvent({
      type: InputEventType.SCROLL,
      pointerId: 0,
      xNorm,
      yNorm,
      scrollDelta: e.deltaY,
    });
    const frame = buildFrame(Channel.INPUT, 0, nowMonotonicUs(), payload);
    this.send(frame);
  }
}

function clamp01(n: number): number {
  return Math.max(0, Math.min(1, n));
}
