// Desktop test harness (repo layout §9: "webclient/ ... talks the same
// protocol to a fake sender for development without a car"). Run with
// `npm run harness`, then `npm run dev` and open the printed localhost URL
// with ?ws=ws://localhost:8787/stream in the query string.
//
// What this validates: control-channel handshake (hello/hello_ack/version),
// ping/pong round trip, and input-channel framing (logs every touch event
// the web client sends, with decoded x/y so you can confirm the normalized
// coordinates line up with where you actually clicked).
//
// What this does NOT validate: real video/audio decode. The video/audio
// frames sent below are synthetic placeholder bytes, NOT a valid H.264/AAC
// bitstream, WebCodecs will reject them (visible as decode errors in the
// browser console, videoDecoder.ts already catches and logs these rather
// than crashing). Real bitstream testing needs android/'s actual encoder
// output, that's a Gate-2, real-device task, not something this harness can
// fake convincingly without embedding a real H.264 sample (deliberately not
// done here, keeps this harness dependency-free and honest about its scope).

import { WebSocketServer } from 'ws';

const PORT = 8787;
const wss = new WebSocketServer({ port: PORT });

console.log(`[harness] fake phone sender listening on ws://localhost:${PORT}/stream`);
console.log('[harness] open the webclient dev server with ?ws=ws://localhost:8787/stream');

const HELLO_VERSION = 1;

function buildFrame(channel, flags, timestampUs, payload) {
  const header = Buffer.alloc(10);
  header.writeUInt8(channel, 0);
  header.writeUInt8(flags, 1);
  header.writeBigUInt64BE(BigInt(timestampUs), 2);
  return Buffer.concat([header, payload]);
}

function parseFrame(buf) {
  return {
    channel: buf.readUInt8(0),
    flags: buf.readUInt8(1),
    timestampUs: buf.readBigUInt64BE(2),
    payload: buf.subarray(10),
  };
}

const Channel = { VIDEO: 0x01, AUDIO: 0x02, INPUT: 0x03, CONTROL: 0x04 };

wss.on('connection', (ws) => {
  console.log('[harness] client connected');
  let helloOk = false;

  const fakeFrameTimer = setInterval(() => {
    if (!helloOk) return;
    // Synthetic, NOT decodable video (see module comment). Exercises framing
    // and drop-to-latest / stats plumbing only.
    const fakePayload = Buffer.from([0, 0, 0, 1, 0x65, 0xaa, 0xbb, 0xcc]);
    const frame = buildFrame(Channel.VIDEO, 0x01 /* keyframe */, Date.now() * 1000, fakePayload);
    ws.send(frame);
  }, 1000 / 30);

  ws.on('message', (data, isBinary) => {
    if (!isBinary) {
      const msg = JSON.parse(data.toString());
      if (msg.kind === 'hello') {
        console.log(`[harness] hello: version=${msg.version} viewport=${msg.viewportW}x${msg.viewportH} token=${msg.token || '(none)'}`);
        helloOk = true;
        ws.send(JSON.stringify({ kind: 'hello_ack', ok: true, serverVersion: HELLO_VERSION }));
      } else if (msg.kind === 'ping') {
        ws.send(JSON.stringify({ kind: 'pong', t: Date.now(), echoedT: msg.t }));
      } else if (msg.kind === 'quality_request') {
        console.log(`[harness] quality_request: ${msg.direction}`);
      } else {
        console.log('[harness] control:', msg);
      }
      return;
    }

    const frame = parseFrame(data);
    if (frame.channel === Channel.INPUT) {
      const type = frame.payload.readUInt8(0);
      const pointerId = frame.payload.readUInt8(1);
      const x = frame.payload.readFloatBE(2);
      const y = frame.payload.readFloatBE(6);
      const typeName = ['down', 'move', 'up', 'scroll'][type] ?? `unknown(${type})`;
      console.log(`[harness] input: ${typeName} pointer=${pointerId} x=${x.toFixed(3)} y=${y.toFixed(3)}`);
    }
  });

  ws.on('close', () => {
    clearInterval(fakeFrameTimer);
    console.log('[harness] client disconnected');
  });
});
