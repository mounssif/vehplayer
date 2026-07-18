// The default TypeScript "DOM" lib does not include AudioWorkletGlobalScope
// types (they live in a separate scope, no <audio-worklet> element to anchor
// them to). Minimal ambient declarations so worklets/*.ts type-checks without
// pulling in a third-party @types package for three globals.
export {};

declare global {
  const sampleRate: number;
  const currentFrame: number;
  const currentTime: number;

  function registerProcessor(
    name: string,
    processorCtor: new (options?: AudioWorkletNodeOptions) => AudioWorkletProcessor,
  ): void;

  abstract class AudioWorkletProcessor {
    readonly port: MessagePort;
    constructor(options?: AudioWorkletNodeOptions);
    abstract process(
      inputs: Float32Array[][],
      outputs: Float32Array[][],
      parameters: Record<string, Float32Array>,
    ): boolean;
  }
}
