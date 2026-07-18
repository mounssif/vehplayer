// Control channel (ARCHITECTURE.md §4): JSON, low rate. Sent/received on
// Channel.CONTROL frames, payload is a single JSON object per frame, no
// batching. HELLO_VERSION must match the phone sender or the phone forces a
// reload (ARCHITECTURE.md §6, "control channel announces the minimum client
// version").

export const HELLO_VERSION = 1;

export type ControlMessage =
  | { kind: 'hello'; version: number; token: string; viewportW: number; viewportH: number; dpr: number }
  | { kind: 'hello_ack'; ok: boolean; reason?: string; serverVersion: number }
  | { kind: 'viewport'; w: number; h: number; dpr: number }
  | { kind: 'stats'; fps: number; decodeMs: number; bufferedAmount: number; framesDropped: number }
  | { kind: 'quality_request'; direction: 'up' | 'down' }
  | { kind: 'thermal'; level: 'nominal' | 'fair' | 'serious' | 'critical' }
  | { kind: 'ping'; t: number }
  | { kind: 'pong'; t: number; echoedT: number };

export function encodeControl(msg: ControlMessage): string {
  return JSON.stringify(msg);
}

export function decodeControl(raw: string): ControlMessage {
  // TODO(claude-code): replace with a real runtime validator (zod or hand-rolled)
  // once android/ actually emits these; trusting shape blindly is fine for a
  // local-only, self-controlled protocol at Gate 2/3, revisit before anything
  // resembling untrusted input reaches this channel.
  return JSON.parse(raw) as ControlMessage;
}

/** hello.version handshake, called once per connection right after WS open. */
export function buildHello(token: string): ControlMessage {
  return {
    kind: 'hello',
    version: HELLO_VERSION,
    token,
    viewportW: window.innerWidth,
    viewportH: window.innerHeight,
    dpr: window.devicePixelRatio || 1,
  };
}
