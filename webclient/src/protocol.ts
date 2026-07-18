// Wire protocol (ARCHITECTURE.md §4). One WebSocket, binary frames, fixed header:
//
//   byte 0      channel   (0x01 video, 0x02 audio, 0x03 input, 0x04 control)
//   byte 1      flags     (video: bit0 = keyframe/IDR, bit1 = config (SPS/PPS))
//   bytes 2-9   u64 timestamp_us (sender monotonic)
//   bytes 10..  payload
//
// This module is the single place that knows the byte layout. Both the phone
// sender and this car client must stay byte-for-byte compatible; if the header
// ever changes, bump Channel.HELLO_VERSION in control.ts and both sides.

export const HEADER_BYTES = 10;

export enum Channel {
  VIDEO = 0x01,
  AUDIO = 0x02,
  INPUT = 0x03,
  CONTROL = 0x04,
}

export const VideoFlag = {
  KEYFRAME: 0b0000_0001,
  CONFIG: 0b0000_0010,
} as const;

export interface Frame {
  channel: Channel;
  flags: number;
  timestampUs: bigint;
  payload: Uint8Array;
}

/** Parse one frame out of a raw ArrayBuffer received from the WebSocket. */
export function parseFrame(buf: ArrayBuffer): Frame {
  if (buf.byteLength < HEADER_BYTES) {
    throw new Error(`frame too short: ${buf.byteLength} bytes, need >= ${HEADER_BYTES}`);
  }
  const view = new DataView(buf);
  const channel = view.getUint8(0) as Channel;
  const flags = view.getUint8(1);
  const timestampUs = view.getBigUint64(2, false); // big-endian, matches Kotlin's default ByteBuffer order if BIG_ENDIAN is set sender-side
  const payload = new Uint8Array(buf, HEADER_BYTES);
  return { channel, flags, timestampUs, payload };
}

/** Build one frame for sending (used by the input channel, car -> phone). */
export function buildFrame(channel: Channel, flags: number, timestampUs: bigint, payload: Uint8Array): ArrayBuffer {
  const out = new ArrayBuffer(HEADER_BYTES + payload.byteLength);
  const view = new DataView(out);
  view.setUint8(0, channel);
  view.setUint8(1, flags);
  view.setBigUint64(2, timestampUs, false);
  new Uint8Array(out, HEADER_BYTES).set(payload);
  return out;
}

export function nowMonotonicUs(): bigint {
  // performance.now() is milliseconds, sub-ms precision as a float; convert to
  // integer microseconds. This clock is NOT synchronized with the phone's
  // clock, only used for local RTT/jitter math on this side, never compared
  // directly to the phone's timestamp without the control-channel ping/pong
  // offset (see control.ts).
  return BigInt(Math.round(performance.now() * 1000));
}
