// Decode + render (ARCHITECTURE.md §2). Primary: WebCodecs VideoDecoder ->
// canvas. Fallback: MSE. Render policy: decode queue max depth 1-2, drop to
// latest, never accumulate delay (latency wins over smoothness).

import { extractAvcConfig, isAnnexB, annexBToAvcc } from './nalu';
import { MseFallbackRenderer } from './mseFallback';

function sameBytes(a: Uint8Array | null, b: Uint8Array | null): boolean {
  if (a === b) return true;
  if (!a || !b || a.length !== b.length) return false;
  for (let i = 0; i < a.length; i++) if (a[i] !== b[i]) return false;
  return true;
}

export type DecodeBackend = 'webcodecs' | 'mse' | 'none';

export interface RendererStats {
  backend: DecodeBackend;
  framesDecoded: number;
  framesDropped: number;
  queueDepth: number;
}

export interface VideoRendererOptions {
  canvas: HTMLCanvasElement;
  onStats?: (s: RendererStats) => void;
  // Surfaced to the UI so a decode failure shows a reason instead of a silent
  // black canvas (e.g. Firefox-on-Linux has VideoDecoder but no H.264 decode).
  onError?: (message: string) => void;
}

/**
 * Feature-detects WebCodecs vs MSE once at construction and picks a backend.
 * Both backends expose the same feed(chunk) surface so main.ts never branches
 * on which one is active.
 */
export class VideoRenderer {
  readonly backend: DecodeBackend;
  private readonly canvas: HTMLCanvasElement;
  private readonly ctx: CanvasRenderingContext2D;
  private readonly onStats?: (s: RendererStats) => void;
  private readonly onError?: (message: string) => void;

  private decoder: VideoDecoder | null = null;
  private configured = false;
  private avcDescription: Uint8Array | null = null; // AVCC-style config (length-prefixed SPS/PPS), built from the first CONFIG frame

  private mse: MseFallbackRenderer | null = null;

  // Drop-to-latest queue bookkeeping. WebCodecs' own internal decode queue is
  // not directly inspectable frame-by-frame, so we track "frames handed to
  // decode() but not yet painted" ourselves and skip painting stale ones.
  private pendingFrames = 0;
  private readonly maxQueueDepth = 2;
  private framesDecoded = 0;
  private framesDropped = 0;

  constructor(opts: VideoRendererOptions) {
    this.canvas = opts.canvas;
    this.onStats = opts.onStats;
    this.onError = opts.onError;
    const ctx = this.canvas.getContext('2d');
    if (!ctx) throw new Error('2D canvas context unavailable');
    this.ctx = ctx;

    if (typeof VideoDecoder !== 'undefined') {
      this.backend = 'webcodecs';
      this.initWebCodecs();
    } else if (typeof MediaSource !== 'undefined') {
      this.backend = 'mse';
      this.mse = new MseFallbackRenderer(this.canvas);
    } else {
      this.backend = 'none';
      console.error('[video] neither WebCodecs nor MSE available, this browser cannot render video at all');
      this.onError?.('this browser has neither WebCodecs nor MSE - cannot render video');
    }
  }

  private initWebCodecs() {
    this.decoder = new VideoDecoder({
      output: (frame) => this.onWebCodecsFrame(frame),
      error: (e) => {
        console.error('[video] WebCodecs decoder error', e);
        // The classic Firefox-on-Linux case: VideoDecoder exists but H.264
        // decode isn't supported, so every chunk errors and the canvas stays
        // black. Say so instead of failing silently. Chromium (the car) does
        // decode H.264 fine.
        this.onError?.(`decoder error: ${(e as Error).message || e}. ` +
          'If this is Firefox, H.264 WebCodecs decode is unsupported there - the car uses Chromium, which works.');
      },
    });
  }

  /**
   * Feed one Annex B access unit (from a Channel.VIDEO frame's payload).
   * `isKeyframe`/`isConfig` come from the wire-protocol flags byte
   * (VideoFlag.KEYFRAME / VideoFlag.CONFIG, protocol.ts).
   */
  feed(payload: Uint8Array, timestampUs: bigint, isKeyframe: boolean, isConfig: boolean) {
    if (this.backend === 'webcodecs') {
      this.feedWebCodecs(payload, timestampUs, isKeyframe, isConfig);
    } else if (this.backend === 'mse') {
      this.mse?.feed(payload, timestampUs, isKeyframe, isConfig);
    }
  }

  private feedWebCodecs(payload: Uint8Array, timestampUs: bigint, isKeyframe: boolean, isConfig: boolean) {
    if (!this.decoder) return;

    if (isConfig || !this.configured) {
      // SPS/PPS arrived (repeated before every IDR per ARCHITECTURE.md §2's
      // PREPEND_HEADER_TO_SYNC_FRAMES flag). Build the avcC description
      // WebCodecs wants for `configure()`. Safe to call configure() again on
      // a mid-stream resolution change, WebCodecs handles that.
      const config = extractAvcConfig(payload);
      if (config && !sameBytes(config.description, this.avcDescription)) {
        this.avcDescription = config.description;
        this.decoder.configure({
          codec: config.codecString, // real profile/level read out of the SPS (nalu.ts), not a hardcoded guess
          description: config.description,
          optimizeForLatency: true,
        });
        this.configured = true;
      }
      if (isConfig) return; // config-only frame carries no picture data
    }

    if (!this.configured) return; // drop picture data until we have a config

    // Drop-to-latest: if the decoder already has work queued, skip this frame
    // rather than let latency build up (ARCHITECTURE.md §2 render policy).
    // Never skip a keyframe, skipping an IDR desyncs everything downstream.
    if (this.pendingFrames >= this.maxQueueDepth && !isKeyframe) {
      this.framesDropped++;
      this.reportStats();
      return;
    }

    const avcc = isAnnexB(payload) ? annexBToAvcc(payload) : payload;
    const chunk = new EncodedVideoChunk({
      type: isKeyframe ? 'key' : 'delta',
      timestamp: Number(timestampUs),
      data: avcc,
    });
    this.pendingFrames++;
    try {
      this.decoder.decode(chunk);
    } catch (e) {
      this.pendingFrames--;
      console.error('[video] decode() threw, likely a corrupt/out-of-order chunk', e);
    }
  }

  private onWebCodecsFrame(frame: VideoFrame) {
    this.pendingFrames = Math.max(0, this.pendingFrames - 1);
    this.framesDecoded++;

    if (this.canvas.width !== frame.displayWidth || this.canvas.height !== frame.displayHeight) {
      this.canvas.width = frame.displayWidth;
      this.canvas.height = frame.displayHeight;
    }
    // requestAnimationFrame-paced paint per ARCHITECTURE.md §2; painting
    // directly here (not queueing) is intentional, rAF pacing of the *source*
    // (the phone's frame rate) already governs how often this callback fires.
    this.ctx.drawImage(frame, 0, 0, this.canvas.width, this.canvas.height);
    frame.close();
    this.reportStats();
  }

  private reportStats() {
    this.onStats?.({
      backend: this.backend,
      framesDecoded: this.framesDecoded,
      framesDropped: this.framesDropped,
      queueDepth: this.pendingFrames,
    });
  }
}
