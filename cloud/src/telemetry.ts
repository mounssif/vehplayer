// Opt-in compatibility telemetry (ARCHITECTURE.md §6: "the /go page doubles
// as the compatibility probe... uploads the result to the compatibility
// matrix"). This is the aggregate, device/car-compatibility-only data
// Foundation §8's privacy posture allows, never video/audio/location.
//
// Downstream use (GROWTH_SAAS.md §6 Gate 5): aggregated into the public
// "does my car work" page, which doubles as a trust/marketing asset. This
// endpoint only ingests; the public aggregation view is a separate,
// not-yet-built piece (Gate 5).

import type { Env } from './index';

export interface CompatReport {
  // Maps 1:1 onto the S1/S2 probe outputs (validate/S1_reachability.md,
  // validate/S2_browser_capability.md, webclient/probe/index.html), this is
  // the same data, just uploaded instead of read off a screen in the car.
  firmwareVersion: string;
  reachabilityTier: string; // "ipv6-gua" | "cgnat-vpn" | "public-vpn" | "none", ReachabilityProbe.kt's tier values
  webCodecsSupported: boolean;
  mseFallbackUsed: boolean;
  avgFps: number | null;
  timestamp: string; // ISO 8601, set server-side, not trusted from the client
}

export async function handleTelemetry(request: Request, env: Env): Promise<Response> {
  if (request.method !== 'POST') {
    return new Response('POST only', { status: 405 });
  }

  let body: Partial<CompatReport>;
  try {
    body = await request.json();
  } catch {
    return new Response('invalid JSON', { status: 400 });
  }

  if (!body.firmwareVersion || !body.reachabilityTier) {
    return new Response('missing required fields', { status: 400 });
  }

  const report: CompatReport = {
    firmwareVersion: body.firmwareVersion,
    reachabilityTier: body.reachabilityTier,
    webCodecsSupported: Boolean(body.webCodecsSupported),
    mseFallbackUsed: Boolean(body.mseFallbackUsed),
    avgFps: typeof body.avgFps === 'number' ? body.avgFps : null,
    timestamp: new Date().toISOString(),
  };

  // TODO(claude-code): this KV key scheme (one entry per report, timestamped
  // key) is fine for low volume, revisit for a real aggregation store
  // (D1/Analytics Engine) once Gate 5's public matrix needs querying, not
  // just storing.
  const key = `report:${report.firmwareVersion}:${crypto.randomUUID()}`;
  await env.COMPAT_TELEMETRY.put(key, JSON.stringify(report));

  return new Response(JSON.stringify({ ok: true }), {
    status: 200,
    headers: { 'content-type': 'application/json' },
  });
}
