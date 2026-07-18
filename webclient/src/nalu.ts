// H.264 Annex B <-> AVCC framing. The phone encoder emits Annex B (start-code
// delimited NAL units, ARCHITECTURE.md §4 "Video payload: raw AnnexB access
// unit"). WebCodecs wants AVCC (4-byte big-endian length prefix per NAL unit)
// plus a separate avcC "description" blob built from SPS/PPS for configure().
// This module is pure functions, no browser APIs, deliberately kept testable
// without a real video stream (feed it any byte array with correct start
// codes and it should do the right thing).

const NAL_TYPE_SPS = 7;
const NAL_TYPE_PPS = 8;

interface NaluRange {
  start: number; // offset of the first byte AFTER the start code (i.e. the NAL header byte)
  end: number;   // exclusive
}

export function isAnnexB(data: Uint8Array): boolean {
  return (
    (data.length >= 4 && data[0] === 0 && data[1] === 0 && data[2] === 0 && data[3] === 1) ||
    (data.length >= 3 && data[0] === 0 && data[1] === 0 && data[2] === 1)
  );
}

/** Walks an Annex B buffer and returns the byte ranges of each NAL unit (header byte inclusive, start code excluded). */
function findNalus(data: Uint8Array): NaluRange[] {
  const starts: number[] = [];
  let i = 0;
  while (i < data.length - 2) {
    if (data[i] === 0 && data[i + 1] === 0 && data[i + 2] === 1) {
      starts.push(i + 3);
      i += 3;
    } else if (i < data.length - 3 && data[i] === 0 && data[i + 1] === 0 && data[i + 2] === 0 && data[i + 3] === 1) {
      starts.push(i + 4);
      i += 4;
    } else {
      i++;
    }
  }
  const ranges: NaluRange[] = [];
  for (let n = 0; n < starts.length; n++) {
    const start = starts[n];
    // end = start of next start code, or, if this is the last NAL, end of buffer.
    // Next start code begins somewhere before the next entry in `starts` minus
    // its (3 or 4 byte) prefix; simplest correct approach: end is just the
    // next start's position minus that start's prefix length, which we don't
    // track separately, so instead re-scan for the next 00 00 01 from `start`.
    let end = data.length;
    for (let j = start; j < data.length - 2; j++) {
      if (data[j] === 0 && data[j + 1] === 0 && (data[j + 2] === 1 || (j < data.length - 3 && data[j + 2] === 0 && data[j + 3] === 1))) {
        end = j;
        break;
      }
    }
    ranges.push({ start, end });
  }
  return ranges;
}

function nalType(data: Uint8Array, headerOffset: number): number {
  return data[headerOffset] & 0x1f;
}

export interface AvcConfig {
  description: Uint8Array; // avcC box body, for VideoDecoder.configure()'s `description`
  codecString: string;     // e.g. "avc1.640028", for VideoDecoder.configure()'s `codec`
}

/**
 * Builds a WebCodecs `description` (avcC box body) and MIME codec string from
 * the SPS/PPS found in an Annex B access unit. Returns null if either is
 * missing (e.g. this frame carries no config, caller should keep the
 * previously built config). Only handles a single SPS + single PPS, which is
 * what the phone's encoder is expected to emit (ARCHITECTURE.md §2, no
 * multi-SPS scalability tricks planned). TODO(claude-code): verify against a
 * real device capture at Gate 2, this has never seen a real bitstream.
 */
export function extractAvcConfig(annexB: Uint8Array): AvcConfig | null {
  const nalus = findNalus(annexB);
  let sps: Uint8Array | null = null;
  let pps: Uint8Array | null = null;

  for (const r of nalus) {
    const type = nalType(annexB, r.start);
    if (type === NAL_TYPE_SPS && !sps) sps = annexB.subarray(r.start, r.end);
    if (type === NAL_TYPE_PPS && !pps) pps = annexB.subarray(r.start, r.end);
  }
  if (!sps || !pps) return null;

  // avcC layout (ISO 14496-15):
  //   1  configurationVersion = 1
  //   1  AVCProfileIndication      <- sps[1]
  //   1  profile_compatibility     <- sps[2]
  //   1  AVCLevelIndication        <- sps[3]
  //   1  0xFF (reserved 111111 + lengthSizeMinusOne=11 -> 4-byte lengths)
  //   1  0xE1 (reserved 111 + numOfSPS=00001)
  //   2  SPS length (big-endian)
  //   N  SPS bytes
  //   1  numOfPPS = 1
  //   2  PPS length (big-endian)
  //   N  PPS bytes
  const out = new Uint8Array(6 + 2 + sps.length + 1 + 2 + pps.length);
  let o = 0;
  out[o++] = 1;
  out[o++] = sps[1];
  out[o++] = sps[2];
  out[o++] = sps[3];
  out[o++] = 0xff;
  out[o++] = 0xe1;
  out[o++] = (sps.length >> 8) & 0xff;
  out[o++] = sps.length & 0xff;
  out.set(sps, o); o += sps.length;
  out[o++] = 1;
  out[o++] = (pps.length >> 8) & 0xff;
  out[o++] = pps.length & 0xff;
  out.set(pps, o); o += pps.length;

  // MIME codec string is the same 3 profile/constraint/level bytes as the
  // avcC header, hex-encoded (RFC 6381): "avc1.PPCCLL".
  const hex = (b: number) => b.toString(16).padStart(2, '0');
  const codecString = `avc1.${hex(sps[1])}${hex(sps[2])}${hex(sps[3])}`;

  return { description: out, codecString };
}

/** Converts a full Annex B access unit to AVCC (4-byte length-prefixed NAL units), dropping SPS/PPS/AUD NALs since those go in the `description`, not the per-chunk payload. */
export function annexBToAvcc(annexB: Uint8Array): Uint8Array {
  const nalus = findNalus(annexB)
    .map((r) => ({ r, type: nalType(annexB, r.start) }))
    .filter(({ type }) => type !== NAL_TYPE_SPS && type !== NAL_TYPE_PPS && type !== 9 /* AUD */);

  const totalLen = nalus.reduce((sum, { r }) => sum + 4 + (r.end - r.start), 0);
  const out = new Uint8Array(totalLen);
  let o = 0;
  for (const { r } of nalus) {
    const len = r.end - r.start;
    out[o++] = (len >>> 24) & 0xff;
    out[o++] = (len >>> 16) & 0xff;
    out[o++] = (len >>> 8) & 0xff;
    out[o++] = len & 0xff;
    out.set(annexB.subarray(r.start, r.end), o);
    o += len;
  }
  return out;
}
