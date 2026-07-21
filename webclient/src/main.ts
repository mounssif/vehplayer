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
  <button id="audio-btn" style="display:none;">audio off, tap for sound</button>
`;

const canvas = document.querySelector<HTMLCanvasElement>('#video-canvas')!;
const statusEl = document.querySelector<HTMLParagraphElement>('#status')!;
const statsEl = document.querySelector<HTMLDivElement>('#stats-overlay')!;
const connectBtn = document.querySelector<HTMLButtonElement>('#connect-btn')!;
const connectScreen = document.querySelector<HTMLDivElement>('#connect-screen')!;
const audioBtn = document.querySelector<HTMLButtonElement>('#audio-btn')!;

// Pairing token + WS URL come from the /go page's query string, set by the
// phone's local HTTP server when it serves this bundle (ARCHITECTURE.md §1
// trust-boundary note: "short-lived pairing token, rendered as part of the
// /go URL"). ?ws= and ?token= match HttpAssetServer.kt's /go redirect
// (confirmed against the actual Kotlin source, not just written to agree).
const params = new URLSearchParams(window.location.search);
const wsUrl = params.get('ws') ?? `ws://${window.location.hostname}:8787/stream`;
const token = params.get('token') ?? '';

// Both params present means the browser just followed a fresh /go redirect
// (or a bookmarked full link) rather than a bare dev load of index.html.
// Session 10's real in-car finding: the only interruption was Reverse
// closing the tab, and reopening it should feel like "click and it works
// again", not a manual re-pair (ARCHITECTURE.md §6). Treat this as a resume
// and skip the tap-to-connect step entirely.
const arrivedViaGo = params.has('token') && params.has('ws');

let renderer: VideoRenderer | null = null;
const audioPlayer = new AudioPlayer();
let audioReady = false;
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
      // Audio needs a real user gesture (browser autoplay policy); an
      // auto-resume has none to spend. Offer it as a small non-blocking tap
      // rather than gating video on it - session 10 measured video playing
      // fine on its own (ARCHITECTURE.md §2).
      if (!audioReady) audioBtn.style.display = 'block';
    }
  },
});

// Guard re-entry: a second start used to re-init everything on top of the
// already-open session and break it (founder-observed: "second connect no
// longer worked"). One start per page load; reload (or reopen the bookmarked
// /go link) to retry.
let sessionStarted = false;
function startSession() {
  if (sessionStarted) return;
  sessionStarted = true;

  renderer = new VideoRenderer({
    canvas,
    onStats: (s) => {
      statsEl.textContent = `${s.backend} | decoded ${s.framesDecoded} | dropped ${s.framesDropped} | queue ${s.queueDepth}`;
    },
    onError: (message) => {
      // Make a black canvas explain itself.
      statusEl.textContent = 'video: ' + message;
      connectScreen.style.display = 'block';
      canvas.style.display = 'none';
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
}

async function tryInitAudio() {
  await audioPlayer.initFromUserGesture().then(() => { audioReady = true; }).catch((e) => console.error('[audio] init failed', e));
  audioBtn.style.display = 'none';
}

connectBtn.addEventListener('click', async () => {
  connectBtn.disabled = true;
  // A real click, so this is a valid place to unlock AudioContext
  // (ARCHITECTURE.md §3, "audio starts only after a user gesture").
  await tryInitAudio();
  startSession();
});

audioBtn.addEventListener('click', tryInitAudio);

if (arrivedViaGo) {
  // No tap to wait for: the browser navigation that got us here already is
  // the "one tap" (opening the bookmarked /go link). Audio stays gated
  // behind the #audio-btn affordance above until a real gesture unlocks it.
  statusEl.textContent = 'reconnecting...';
  startSession();
}

// Reverse-gear lifecycle (session 10, MEASURED in the real car): Reverse
// closes the browser tab outright rather than just hiding it, so this event
// won't fire for that case, the fix is the arrivedViaGo auto-resume above,
// triggered on the next page load. Kept for the lesser cases that don't
// destroy the page (app switcher, GPS view), logged rather than acted on
// since nothing so far needed an explicit decoder pause/resume for those.
document.addEventListener('visibilitychange', () => {
  console.log('[lifecycle] visibilitychange ->', document.visibilityState);
});
