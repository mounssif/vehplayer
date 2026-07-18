// Audio playback (ARCHITECTURE.md §3, Route B / Foundation §6b item 3, Pro
// toggle). Route A (default, car Bluetooth) needs zero code here, this
// module only matters when the user has the low-latency audio toggle on.
//
// Autoplay policy: the car browser will not let AudioContext leave
// 'suspended' until a user gesture. The connect tap on the pairing page
// covers this (ARCHITECTURE.md §3), so `resume()` is called from that same
// click handler in main.ts, not from here automatically.

const WORKLET_MODULE_URL = new URL('./worklets/ringBufferProcessor.ts', import.meta.url);

export class AudioPlayer {
  private ctx: AudioContext | null = null;
  private workletNode: AudioWorkletNode | null = null;
  private decoder: AudioDecoder | null = null;
  private ready = false;

  /** Must be called from within a user-gesture event handler (click/tap). */
  async initFromUserGesture(): Promise<void> {
    if (this.ready) return;
    this.ctx = new AudioContext({ latencyHint: 'interactive' });
    await this.ctx.audioWorklet.addModule(WORKLET_MODULE_URL);
    this.workletNode = new AudioWorkletNode(this.ctx, 'ring-buffer-processor');
    this.workletNode.connect(this.ctx.destination);

    if (typeof AudioDecoder !== 'undefined') {
      this.decoder = new AudioDecoder({
        output: (data) => this.onDecodedAudio(data),
        error: (e) => console.error('[audio] AudioDecoder error', e),
      });
      // TODO(claude-code): confirm the real config (codec string, sampleRate,
      // numberOfChannels) once android/'s AAC encoder settings are decided
      // (ARCHITECTURE.md §3 says "AAC (or 16-bit PCM at low bitrates)", this
      // assumes AAC-LC stereo 48kHz as the working default).
      this.decoder.configure({
        codec: 'mp4a.40.2', // AAC-LC
        sampleRate: 48000,
        numberOfChannels: 2,
      });
    } else {
      console.warn('[audio] WebCodecs AudioDecoder unavailable, low-latency audio toggle has no effect on this browser, user should stay on Bluetooth (Route A)');
    }

    if (this.ctx.state === 'suspended') {
      await this.ctx.resume();
    }
    this.ready = true;
  }

  /** Feed one Channel.AUDIO frame's payload (raw AAC access unit). */
  feed(payload: Uint8Array, timestampUs: bigint) {
    if (!this.decoder || this.decoder.state !== 'configured') return;
    const chunk = new EncodedAudioChunk({
      type: 'key', // AAC has no delta frames in this sense, every chunk is independently decodable
      timestamp: Number(timestampUs),
      data: payload,
    });
    try {
      this.decoder.decode(chunk);
    } catch (e) {
      console.error('[audio] decode() threw', e);
    }
  }

  private onDecodedAudio(data: AudioData) {
    if (!this.workletNode) {
      data.close();
      return;
    }
    const channelData = new Float32Array(data.numberOfFrames);
    data.copyTo(channelData, { planeIndex: 0 });
    // Transfer ownership of the buffer to the audio thread, avoid a copy.
    this.workletNode.port.postMessage(channelData, [channelData.buffer]);
    data.close();
  }
}
