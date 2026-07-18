// Input payload (car -> phone), per ARCHITECTURE.md §4:
//   compact binary { type: down|move|up|scroll, pointer_id, x_norm, y_norm }
// Coordinates normalized to the video frame [0,1] so encoder resolution
// changes never break touch mapping. Layout chosen here (not yet frozen in
// ARCHITECTURE.md, this is the first concrete proposal, revisit at Gate 3
// once android/ actually parses it):
//
//   byte 0        event type (0=down, 1=move, 2=up, 3=scroll)
//   byte 1        pointer_id (0-255, enough for any real touchscreen)
//   bytes 2-5     x_norm as f32
//   bytes 6-9     y_norm as f32
//   bytes 10-13   scroll_delta as f32 (0 for non-scroll events)

export enum InputEventType {
  DOWN = 0,
  MOVE = 1,
  UP = 2,
  SCROLL = 3,
}

export interface InputEvent {
  type: InputEventType;
  pointerId: number;
  xNorm: number;
  yNorm: number;
  scrollDelta?: number;
}

export const INPUT_PAYLOAD_BYTES = 14;

export function encodeInputEvent(ev: InputEvent): Uint8Array {
  const buf = new ArrayBuffer(INPUT_PAYLOAD_BYTES);
  const view = new DataView(buf);
  view.setUint8(0, ev.type);
  view.setUint8(1, ev.pointerId & 0xff);
  view.setFloat32(2, ev.xNorm, false);
  view.setFloat32(6, ev.yNorm, false);
  view.setFloat32(10, ev.scrollDelta ?? 0, false);
  return new Uint8Array(buf);
}
