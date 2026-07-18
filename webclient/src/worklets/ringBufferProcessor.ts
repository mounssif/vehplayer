// Runs on the audio rendering thread. Receives raw PCM float32 chunks via
// port.postMessage from audioPlayer.ts (main thread, after AAC/PCM decode)
// and plays them back from a small ring buffer. A/V sync target 40-60ms per
// Foundation Route B / ARCHITECTURE.md §3: this buffer's target size should
// stay in that window, grown only enough to absorb network jitter.
//
// TODO(claude-code): this assumes audioPlayer.ts hands it already-decoded
// Float32 PCM (i.e. AAC decode happens on the main thread via WebCodecs
// AudioDecoder before reaching here). Confirm that split once android/'s
// audio encode path (AAC vs raw PCM at low bitrate, ARCHITECTURE.md §3) is
// settled, this processor doesn't care which, it only plays PCM.

class RingBufferProcessor extends AudioWorkletProcessor {
  private ring: Float32Array;
  private writeIdx = 0;
  private readIdx = 0;
  private available = 0;
  private readonly capacity: number;

  // Target buffer occupancy in samples, corresponds to Foundation's 40-60ms
  // window at whatever sampleRate the context negotiated (typically 48000).
  private readonly targetSamples: number;

  constructor() {
    super();
    this.capacity = sampleRate * 2; // 2s of headroom, generous on purpose, trimming happens via skip logic below
    this.ring = new Float32Array(this.capacity);
    this.targetSamples = Math.round(sampleRate * 0.05); // ~50ms, middle of the 40-60ms target

    this.port.onmessage = (e: MessageEvent<Float32Array>) => {
      const chunk = e.data;
      for (let i = 0; i < chunk.length; i++) {
        this.ring[this.writeIdx] = chunk[i];
        this.writeIdx = (this.writeIdx + 1) % this.capacity;
      }
      this.available = Math.min(this.capacity, this.available + chunk.length);
    };
  }

  process(_inputs: Float32Array[][], outputs: Float32Array[][]): boolean {
    const output = outputs[0][0];

    // Resample/skip to stay inside the 40-60ms window (ARCHITECTURE.md §3
    // "A/V sync: video is the master"). If we've drifted way ahead of
    // target (network burst caught up), drop some samples instead of
    // playing a growing lag.
    if (this.available > this.targetSamples * 3) {
      const drop = this.available - this.targetSamples;
      this.readIdx = (this.readIdx + drop) % this.capacity;
      this.available -= drop;
    }

    for (let i = 0; i < output.length; i++) {
      if (this.available > 0) {
        output[i] = this.ring[this.readIdx];
        this.readIdx = (this.readIdx + 1) % this.capacity;
        this.available--;
      } else {
        output[i] = 0; // underrun: silence rather than stale/garbage samples
      }
    }
    return true; // keep processor alive
  }
}

registerProcessor('ring-buffer-processor', RingBufferProcessor);
