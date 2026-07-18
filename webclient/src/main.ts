// Entry point for the /go landing page (ARCHITECTURE.md §6). Kept
// deliberately thin: this file wires modules together and owns the DOM,
// all real logic lives in the modules it imports.

import './style.css';
import { WsClient } from './wsClient';
import { VideoRenderer } from './videoDecoder';
import { AudioPlayer } from './audioPlayer';
import { InputSender } from './inputSender';
import { QualityLadder } from './qualityLadder';
import type { ControlMessage } from './control';

const app = document.querySelector<HTMLDivElement>('#app')!;
app.innerHTML = `
  <div id="connect-screen">
    <h1>vehplayer</h1>
    <p id="status">tap to connect</p>
    <button id="connect-btn">Connect</button>
  </div>
  <canvas id="video-canvas" style="display:none; width:100%; height:100%;"></canvas>
  <div id="stats-overlay"></div>
`;

const canvas = document.querySelector<HTMLCanvasElement>('#video-canvas')!;
const statusEl = document.querySelector<HTMLParagraphElement>('#status')!;
const statsEl = document.querySelector<HTMLDivElement>('#stats-overlay')!;
const connectBtn = document.querySelector<HTMLButtonElement>('#connect-btn')!;
const connectScreen = document.querySelector<HTMLDivElement>('#connect-screen')!;

// Pairing token + WS URL come from the /go page's query string, set by the
// phone's local HTTP server when it serves this bundle (ARCHITECTURE.md §1
// trust-boundary note: "short-lived pairing token, rendered as part of the
// /go URL"). ?ws= and ?token= match HttpAssetServer.kt's /go redirect
// (confirmed against the actual Kotlin source, not just written to agree).
const params = new URLSearchParams(window.location.search);
const wsUrl = params.get('ws') ?? `ws://${window.location.hostname}:8787/stream`;
const token = params.get('token') ?? '';

let renderer: VideoRenderer | null = null;
const audioPlayer = new AudioPlayer();
let inputSender: InputSender | null = null;
let qualityLadder: QualityLadder | null = null;

const wsClient = new WsClient({
  url: wsUrl,
  token,
  onVideoFrame: (payload, ts, isKeyframe, isConfig) => renderer?.feed(payload, ts, isKeyframe, isConfig),
  onAudioFrame: (payload, ts) => audioPlayer.feed(payload, ts),
  onControl: (msg: ControlMessage) => {
    if (msg.kind === 'pong') qualityLadder?.onPongReceived(msg.echoedT);
    if (msg.kind === 'thermal') statusEl.textContent = `thermal: ${msg.level}`;
  },
  onStateChange: (state) => {
    statusEl.textContent = `status: ${state}`;
    if (state === 'open') {
      connectScreen.style.display = 'none';
      canvas.style.display = 'block';
    }
  },
});

connectBtn.addEventListener('click', async () => {
  // User-gesture requirement for AudioContext lives here (ARCHITECTURE.md
  // §3 "audio starts only after a user gesture on the page, the connect tap
  // covers this").
  await audioPlayer.initFromUserGesture().catch((e) => console.error('[audio] init failed', e));

  renderer = new VideoRenderer({
    canvas,
    onStats: (s) => {
      statsEl.textContent = `${s.backend} | decoded ${s.framesDecoded} | dropped ${s.framesDropped} | queue ${s.queueDepth}`;
    },
  });

  inputSender = new InputSender({
    surface: canvas,
    send: (frame) => wsClient.sendInput(frame),
  });

  qualityLadder = new QualityLadder({
    getBufferedAmount: () => wsClient.bufferedAmount,
    sendControl: (msg) => wsClient.sendControl(msg),
  });
  qualityLadder.start();

  // Debug handle only, not read anywhere else: keeps `inputSender` from being
  // flagged unused while giving devtools access during Gate-1/2 poking.
  (window as unknown as Record<string, unknown>).__vehplayer = { renderer, inputSender, qualityLadder };

  wsClient.connect();
});

// Reverse-gear / tab-suspend lifecycle edge case (Foundation §7). Log for now,
// TODO(claude-code): confirm real Tesla behavior at Gate 1 S2 and decide
// whether this needs an explicit pause/resume of the decoder vs. just letting
// the browser suspend rAF naturally.
document.addEventListener('visibilitychange', () => {
  console.log('[lifecycle] visibilitychange ->', document.visibilityState);
});
