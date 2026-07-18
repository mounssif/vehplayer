// WS connection lifecycle + channel routing (ARCHITECTURE.md §4/§6). Single
// socket, binary frames on video/audio/input, JSON on control. This module
// owns the socket; everything else (video renderer, audio player, input
// sender) just gets callbacks.

import { Channel, parseFrame, buildFrame, nowMonotonicUs, VideoFlag } from './protocol';
import { type ControlMessage, decodeControl, encodeControl, buildHello, HELLO_VERSION } from './control';

export interface WsClientOptions {
  url: string;
  token: string;
  onVideoFrame: (payload: Uint8Array, timestampUs: bigint, isKeyframe: boolean, isConfig: boolean) => void;
  onAudioFrame: (payload: Uint8Array, timestampUs: bigint) => void;
  onControl: (msg: ControlMessage) => void;
  onStateChange: (state: 'connecting' | 'open' | 'closed' | 'error') => void;
}

export class WsClient {
  private ws: WebSocket | null = null;
  private helloAcked = false;

  constructor(private readonly opts: WsClientOptions) {}

  connect() {
    this.opts.onStateChange('connecting');
    this.ws = new WebSocket(this.opts.url);
    this.ws.binaryType = 'arraybuffer';

    this.ws.onopen = () => {
      this.sendControl(buildHello(this.opts.token));
    };

    this.ws.onmessage = (ev: MessageEvent) => {
      if (typeof ev.data === 'string') {
        this.handleControlRaw(ev.data);
      } else {
        this.handleBinary(ev.data as ArrayBuffer);
      }
    };

    this.ws.onclose = () => this.opts.onStateChange('closed');
    this.ws.onerror = () => this.opts.onStateChange('error');
  }

  disconnect() {
    this.ws?.close();
    this.ws = null;
    this.helloAcked = false;
  }

  get bufferedAmount(): number {
    return this.ws?.bufferedAmount ?? 0;
  }

  sendInput(frame: ArrayBuffer) {
    if (this.ws?.readyState === WebSocket.OPEN) this.ws.send(frame);
  }

  sendControl(msg: ControlMessage) {
    if (this.ws?.readyState === WebSocket.OPEN) this.ws.send(encodeControl(msg));
  }

  private handleControlRaw(raw: string) {
    const msg = decodeControl(raw);
    if (msg.kind === 'hello_ack') {
      if (!msg.ok) {
        console.error('[ws] hello rejected:', msg.reason);
        this.disconnect();
        this.opts.onStateChange('error');
        return;
      }
      if (msg.serverVersion !== HELLO_VERSION) {
        console.warn(`[ws] protocol version mismatch, client=${HELLO_VERSION} server=${msg.serverVersion}, expect the phone to force a reload per ARCHITECTURE.md §6`);
      }
      this.helloAcked = true;
      this.opts.onStateChange('open');
      return;
    }
    this.opts.onControl(msg);
  }

  private handleBinary(buf: ArrayBuffer) {
    if (!this.helloAcked) return; // ignore media before handshake completes
    const frame = parseFrame(buf);
    switch (frame.channel) {
      case Channel.VIDEO:
        this.opts.onVideoFrame(
          frame.payload,
          frame.timestampUs,
          (frame.flags & VideoFlag.KEYFRAME) !== 0,
          (frame.flags & VideoFlag.CONFIG) !== 0,
        );
        break;
      case Channel.AUDIO:
        this.opts.onAudioFrame(frame.payload, frame.timestampUs);
        break;
      default:
        console.warn('[ws] unexpected binary channel from server', frame.channel);
    }
  }
}

// re-exported for callers that need to build an INPUT frame without pulling
// in protocol.ts directly (kept here so wsClient stays the single import
// surface for main.ts's happy path).
export { nowMonotonicUs, buildFrame };
