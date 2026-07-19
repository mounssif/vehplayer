// MSE fallback (ARCHITECTURE.md §2): "the TesAA-era path, works everywhere
// video works but costs latency, keep it dumb and reliable." Used only when
// `VideoDecoder` is not present (older Tesla firmware / other browsers).
//
// Deliberately NOT a priority to finish (ARCHITECTURE.md §2, session 6):
// Tesla's in-car browser is REPORTED to suppress <video> element playback
// while the car is in Drive (multiple independent sources, see that doc).
// This renderer works through a real <video> element (see the constructor
// below) - so on any car where it's actually *needed* (WebCodecs
// unsupported), it's also the browser configuration most likely to be
// suppressed in the one state that matters for a driving/navigation
// product. Keep the plumbing (it's genuinely useful for park-only/media
// use cases), but don't treat the muxer TODO below as blocking anything.
//
// This file is the plumbing (SourceBuffer lifecycle, aggressive buffer
// trimming, playbackRate nudging to chase the live edge). The one piece
// deliberately NOT implemented here is the Annex-B -> fragmented-MP4 muxer
// (`muxToFmp4` below): hand-rolling ISO-BMFF box writers with no real H.264
// stream or browser to validate against, in a sandbox with no video decoder
// available at all, is a good way to ship silently-wrong binary code nobody
// catches until a real car. That needs a real MEASURED pass (Gate 2, real
// phone, real Tesla or at least a desktop Chromium with a real MSE video
// element open to eyeball it). See the TODO on `muxToFmp4` for the exact
// box layout to implement, this is a scoped, well-specified task, not an
// open-ended one.

export class MseFallbackRenderer {
  private readonly video: HTMLVideoElement;
  private mediaSource: MediaSource | null = null;
  private sourceBuffer: SourceBuffer | null = null;
  private mp4Config: Uint8Array | null = null; // avcC-equivalent, built once from first SPS/PPS
  private queue: Uint8Array[] = [];
  private appending = false;

  constructor(private readonly canvas: HTMLCanvasElement) {
    // MSE renders through a <video> element, not the canvas directly. Swap
    // the canvas out of the DOM in favor of a video element with the same
    // size/position; main.ts only ever talks to VideoRenderer, this swap is
    // an implementation detail of the MSE backend.
    this.video = document.createElement('video');
    this.video.autoplay = true;
    this.video.muted = true; // audio in fallback mode stays on car Bluetooth per Foundation Route A, this element is video-only
    this.video.playsInline = true;
    this.video.style.cssText = this.canvas.style.cssText;
    this.canvas.replaceWith(this.video);

    this.mediaSource = new MediaSource();
    this.video.src = URL.createObjectURL(this.mediaSource);
    this.mediaSource.addEventListener('sourceopen', () => this.onSourceOpen(), { once: true });

    // Chase the live edge: if we drift behind, nudge playbackRate up rather
    // than seeking (seeking on a live-ish fMP4 stream is jankier). Never
    // build up delay, matches the render policy in ARCHITECTURE.md §2.
    setInterval(() => this.chaseLiveEdge(), 1000);
  }

  private onSourceOpen() {
    // TODO(claude-code): pick the real mime/codec string once muxToFmp4 is
    // implemented and we know the exact profile/level being muxed; keep in
    // sync with the avc1.42E01E baseline assumption in videoDecoder.ts.
    const mime = 'video/mp4; codecs="avc1.42E01E"';
    if (!MediaSource.isTypeSupported(mime)) {
      console.error('[video/mse] mime not supported by this browser', mime);
      return;
    }
    this.sourceBuffer = this.mediaSource!.addSourceBuffer(mime);
    this.sourceBuffer.mode = 'sequence'; // per ARCHITECTURE.md §2
    this.sourceBuffer.addEventListener('updateend', () => this.pump());
  }

  feed(payload: Uint8Array, _timestampUs: bigint, isKeyframe: boolean, isConfig: boolean) {
    if (isConfig) {
      // TODO(claude-code): build the avcC-equivalent init segment (ftyp+moov)
      // here via muxToFmp4Init(payload) once implemented, store in
      // this.mp4Config, and append it before the first media segment.
      return;
    }
    if (!this.mp4Config) return; // no init segment yet, can't append media segments

    const segment = muxToFmp4MediaSegment(payload, isKeyframe);
    if (segment) {
      this.queue.push(segment);
      this.pump();
    }
  }

  private pump() {
    if (this.appending || !this.sourceBuffer || this.sourceBuffer.updating) return;
    const next = this.queue.shift();
    if (!next) return;
    this.appending = true;
    try {
      this.sourceBuffer.appendBuffer(next as BufferSource);
    } catch (e) {
      console.error('[video/mse] appendBuffer failed', e);
    } finally {
      this.appending = false;
    }
    this.trimBuffer();
  }

  /** Aggressive buffer trimming per ARCHITECTURE.md §2, keep only a couple seconds of history. */
  private trimBuffer() {
    if (!this.sourceBuffer || this.sourceBuffer.updating) return;
    const buffered = this.sourceBuffer.buffered;
    if (buffered.length === 0) return;
    const end = buffered.end(buffered.length - 1);
    const keepFrom = end - 2; // keep last ~2s
    const start = buffered.start(0);
    if (keepFrom > start) {
      try {
        this.sourceBuffer.remove(start, keepFrom);
      } catch (e) {
        console.warn('[video/mse] buffer trim failed (non-fatal)', e);
      }
    }
  }

  private chaseLiveEdge() {
    const buffered = this.video.buffered;
    if (buffered.length === 0) return;
    const end = buffered.end(buffered.length - 1);
    const behind = end - this.video.currentTime;
    if (behind > 0.5) {
      this.video.playbackRate = 1.5; // nudge, don't hard-seek
    } else if (behind < 0.15) {
      this.video.playbackRate = 1.0;
    }
  }
}

/**
 * TODO(claude-code), scoped Gate-2 task: implement the Annex-B -> fragmented
 * MP4 (fMP4) remux. Exact plan, so this doesn't need to be figured out from
 * scratch:
 *
 * Init segment (once, from the first CONFIG frame's SPS/PPS), boxes in order:
 *   ftyp (brand 'isom', minor version 0, compatible brands 'isom','iso2','avc1','mp41')
 *   moov
 *     mvhd (timescale e.g. 90000, duration 0 for fragmented)
 *     trak
 *       tkhd (track id 1, width/height from SPS)
 *       mdia
 *         mdhd (timescale 90000)
 *         hdlr (handler type 'vide')
 *         minf
 *           vmhd
 *           dinf > dref > url (self-contained)
 *           stbl
 *             stsd > avc1 > avcC   <- reuse extractAvcConfig()'s output from nalu.ts, same bytes
 *             stts (empty, count 0)
 *             stsc (empty)
 *             stsz (empty)
 *             stco (empty)
 *     mvex > trex (track id 1, default values)
 *
 * Media segment (per access unit or small GOP), boxes in order:
 *   moof
 *     mfhd (sequence number, increment per segment)
 *     traf
 *       tfhd (track id 1, flags for default-base-is-moof)
 *       tfdt (base media decode time, running counter in the 90000 timescale)
 *       trun (sample count, data offset, flags: sample-duration/size/flags present,
 *             first-sample-flags for keyframe vs delta)
 *   mdat (raw AVCC-framed NAL units, same length-prefix conversion as
 *         annexBToAvcc() in nalu.ts, this can and should reuse that function)
 *
 * Every box is [4-byte big-endian size][4-byte ASCII fourcc][body]. Reference
 * implementation approach: write a tiny `box(fourcc, ...children)` helper
 * that concatenates child buffers and prepends the size, then compose boxes
 * top-down as listed above. This is mechanical once one real SPS/PPS pair
 * from the actual encoder (ARCHITECTURE.md §2) is available to test against,
 * hence deferred rather than guessed here.
 */
function muxToFmp4MediaSegment(_payload: Uint8Array, _isKeyframe: boolean): Uint8Array | null {
  console.warn('[video/mse] muxToFmp4MediaSegment not implemented yet, MSE fallback is inert until Gate 2');
  return null;
}
